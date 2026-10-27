from pathlib import Path
from threading import RLock
from uuid import uuid4

import cv2
import numpy as np
from fastapi import UploadFile

from app.core.config import settings
from app.models.schemas import FaceMatchResponse


class FaceMatcher:
    def __init__(self) -> None:
        settings.feature_dir.mkdir(parents=True, exist_ok=True)
        settings.upload_dir.mkdir(parents=True, exist_ok=True)
        self.lock = RLock()
        self.detector = None
        self.recognizer = None
        self.model_version = "sface-2021dec-v1"

    def _load_models(self):
        if self.recognizer is not None:
            return
        detector_path = settings.model_dir / "face_detection_yunet_2023mar.onnx"
        recognizer_path = settings.model_dir / "face_recognition_sface_2021dec.onnx"
        if not detector_path.exists() or not recognizer_path.exists():
            raise RuntimeError("人脸模型未安装，请在 face-service 目录运行 python download_models.py")
        try:
            self.detector = cv2.FaceDetectorYN.create(str(detector_path), "", (320, 320), 0.85)
            self.recognizer = cv2.FaceRecognizerSF.create(str(recognizer_path), "")
        except cv2.error as exc:
            raise RuntimeError("人脸模型加载失败，请重新下载模型并检查 OpenCV 安装") from exc

    def readiness(self) -> dict:
        try:
            with self.lock:
                self._load_models()
            compatible = 0
            stale = 0
            for path in settings.feature_dir.glob("subject-*.npz"):
                try:
                    self._read_feature(path)
                    compatible += 1
                except (OSError, ValueError, KeyError):
                    stale += 1
            return {"ready": True, "model": self.model_version, "profiles": compatible,
                    "profilesRequiringEnrollment": stale}
        except RuntimeError as exc:
            return {"ready": False, "message": str(exc)}

    def _read_feature(self, path: Path):
        with np.load(path, allow_pickle=False) as stored:
            if "model_version" not in stored or str(stored["model_version"]) != self.model_version:
                raise ValueError("人脸算法已升级，请重新录入该用户人脸")
            feature = stored["feature"].copy()
            member = int(stored["member_id"][0])
        if feature.shape != (128,) or not np.isfinite(feature).all():
            raise ValueError("人脸档案损坏，请重新录入")
        return feature, None if member < 0 else member

    async def enroll(
        self,
        subject_id: int,
        member_id: int | None,
        image: UploadFile,
    ) -> FaceMatchResponse:
        feature, quality = await self._extract(image)
        if quality < settings.minimum_quality:
            raise ValueError(
                f"人脸图像质量不足（{quality:.1f}），请改善光线并保持镜头稳定"
            )
        target = self._feature_path(subject_id)
        temporary = target.with_name(f".{target.stem}-{uuid4().hex}.npz")
        try:
            np.savez_compressed(
                temporary, feature=feature, model_version=np.array(self.model_version),
                member_id=np.array([-1 if member_id is None else member_id], dtype=np.int64),
            )
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        return FaceMatchResponse(
            matched=True,
            subject_id=subject_id,
            member_id=member_id,
            similarity=1.0,
            quality_score=round(quality, 2),
            feature_ref=f"features/{target.name}",
            message="人脸录入成功",
        )

    async def verify(
        self,
        subject_id: int,
        member_id: int | None,
        image: UploadFile,
    ) -> FaceMatchResponse:
        target = self._feature_path(subject_id)
        if not target.exists():
            return FaceMatchResponse(
                matched=False,
                subject_id=subject_id,
                member_id=member_id,
                similarity=0.0,
                message="该用户尚未录入人脸",
            )
        feature, quality = await self._extract(image)
        enrolled, _ = self._read_feature(target)
        similarity = self._similarity(feature, enrolled)
        matched = similarity >= settings.verify_threshold
        return FaceMatchResponse(
            matched=matched,
            subject_id=subject_id,
            member_id=member_id,
            similarity=round(similarity, 4),
            quality_score=round(quality, 2),
            message="人脸验证通过" if matched else "人脸与已录入档案不匹配",
        )

    async def identify(self, image: UploadFile) -> FaceMatchResponse:
        feature, quality = await self._extract(image)
        best_subject: int | None = None
        best_member: int | None = None
        best_similarity = 0.0
        runner_up = 0.0
        stale = 0
        for feature_path in settings.feature_dir.glob("subject-*.npz"):
            try:
                enrolled, member = self._read_feature(feature_path)
                similarity = self._similarity(feature, enrolled)
                if similarity > best_similarity:
                    runner_up = best_similarity
                    best_similarity = similarity
                    best_subject = int(feature_path.stem.removeprefix("subject-"))
                    best_member = member
                else:
                    runner_up = max(runner_up, similarity)
            except (OSError, ValueError, KeyError):
                stale += 1
                continue
        matched = (best_subject is not None and best_similarity >= settings.identify_threshold
                   and best_similarity - runner_up >= settings.identify_margin)
        return FaceMatchResponse(
            matched=matched,
            subject_id=best_subject if matched else None,
            member_id=best_member if matched else None,
            similarity=round(best_similarity, 4),
            quality_score=round(quality, 2),
            message=("已识别登录用户" if matched else
                     "旧人脸档案需要重新录入" if best_subject is None and stale else
                     "未识别到唯一用户，请重拍或使用密码登录"),
        )

    def remove(self, subject_id: int) -> FaceMatchResponse:
        target = self._feature_path(subject_id)
        if target.exists():
            target.unlink()
        return FaceMatchResponse(
            matched=False,
            subject_id=subject_id,
            similarity=0.0,
            message="人脸档案已删除",
        )

    async def _extract(self, image: UploadFile) -> tuple[np.ndarray, float]:
        content = await image.read(settings.maximum_image_bytes + 1)
        if not content:
            raise ValueError("上传的图片为空")
        if len(content) > settings.maximum_image_bytes:
            raise ValueError("图片大小不能超过 5MB")
        encoded = np.frombuffer(content, dtype=np.uint8)
        frame = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
        if frame is None:
            raise ValueError("无法读取图片，仅支持 JPG、JPEG 和 PNG")
        height, width = frame.shape[:2]
        if width * height > 16_000_000:
            raise ValueError("图片分辨率过大，请使用摄像头拍照或缩小图片")
        scale = min(1.0, 960 / max(width, height))
        if scale < 1:
            frame = cv2.resize(frame, (round(width * scale), round(height * scale)))
        with self.lock:
            self._load_models()
            self.detector.setInputSize((frame.shape[1], frame.shape[0]))
            _, faces = self.detector.detect(frame)
            if faces is None or len(faces) == 0:
                raise ValueError("未检测到正面人脸，请正对摄像头并改善光线")
            if len(faces) != 1:
                raise ValueError("画面中检测到多张人脸，请仅保留一人")
            face = faces[0]
            x, y, w, h = [int(value) for value in face[:4]]
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            crop = gray[max(0, y):min(gray.shape[0], y+h), max(0, x):min(gray.shape[1], x+w)]
            if crop.size == 0 or min(w, h) < 60:
                raise ValueError("人脸过小，请靠近镜头后重试")
            quality = self._quality(crop, w * h, gray.size)
            if quality < settings.minimum_quality:
                raise ValueError(f"人脸图像质量不足（{quality:.1f}），请改善光线并保持稳定")
            aligned = self.recognizer.alignCrop(frame, face)
            feature = self.recognizer.feature(aligned).flatten().copy()
        norm = float(np.linalg.norm(feature))
        if norm == 0 or not np.isfinite(feature).all():
            raise ValueError("人脸图像缺少有效特征，请重新拍摄")
        return feature / norm, quality

    def _quality(self, face: np.ndarray, face_area: int, image_area: int) -> float:
        blur = float(cv2.Laplacian(face, cv2.CV_64F).var())
        sharpness_score = min(100.0, blur / 1.25)
        brightness = float(face.mean())
        brightness_score = max(0.0, 100.0 - abs(brightness - 130.0) * 1.15)
        size_score = min(100.0, face_area / max(1, image_area) * 700.0)
        return sharpness_score * 0.45 + brightness_score * 0.35 + size_score * 0.2

    def _similarity(self, current: np.ndarray, enrolled: np.ndarray) -> float:
        cosine = float(np.dot(current, enrolled) / (np.linalg.norm(current) * np.linalg.norm(enrolled)))
        return max(0.0, min(1.0, cosine))

    def _feature_path(self, subject_id: int) -> Path:
        if subject_id <= 0:
            raise ValueError("用户标识无效")
        return settings.feature_dir / f"subject-{subject_id}.npz"
