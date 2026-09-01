from pathlib import Path

import cv2
import numpy as np
from fastapi import UploadFile

from app.core.config import settings
from app.models.schemas import FaceMatchResponse


class FaceMatcher:
    def __init__(self) -> None:
        settings.feature_dir.mkdir(parents=True, exist_ok=True)
        settings.upload_dir.mkdir(parents=True, exist_ok=True)
        cascade_path = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        self.detector = cv2.CascadeClassifier(cascade_path)
        if self.detector.empty():
            raise RuntimeError("OpenCV face detector could not be loaded")

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
        np.savez_compressed(
            target,
            feature=feature,
            member_id=np.array([-1 if member_id is None else member_id], dtype=np.int64),
        )
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
        enrolled = np.load(target)["feature"]
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
        for feature_path in settings.feature_dir.glob("subject-*.npz"):
            try:
                stored = np.load(feature_path)
                similarity = self._similarity(feature, stored["feature"])
                if similarity > best_similarity:
                    best_similarity = similarity
                    best_subject = int(feature_path.stem.removeprefix("subject-"))
                    member_value = int(stored["member_id"][0])
                    best_member = None if member_value < 0 else member_value
            except (OSError, ValueError, KeyError):
                continue
        matched = best_subject is not None and best_similarity >= settings.identify_threshold
        return FaceMatchResponse(
            matched=matched,
            subject_id=best_subject if matched else None,
            member_id=best_member if matched else None,
            similarity=round(best_similarity, 4),
            quality_score=round(quality, 2),
            message="已识别登录用户" if matched else "未识别到已录入用户",
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
        content = await image.read()
        if not content:
            raise ValueError("上传的图片为空")
        if len(content) > settings.maximum_image_bytes:
            raise ValueError("图片大小不能超过 5MB")
        encoded = np.frombuffer(content, dtype=np.uint8)
        frame = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
        if frame is None:
            raise ValueError("无法读取图片，仅支持 JPG、JPEG 和 PNG")
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        equalized = cv2.equalizeHist(gray)
        minimum = max(52, min(frame.shape[:2]) // 9)
        faces = self.detector.detectMultiScale(
            equalized,
            scaleFactor=1.08,
            minNeighbors=4,
            minSize=(minimum, minimum),
        )
        if len(faces) == 0:
            fallback_minimum = max(44, minimum - 12)
            faces = self.detector.detectMultiScale(
                equalized,
                scaleFactor=1.06,
                minNeighbors=3,
                minSize=(fallback_minimum, fallback_minimum),
            )
        if len(faces) == 0:
            raise ValueError("未检测到正面人脸，请正对摄像头后重试")
        if len(faces) > 1:
            raise ValueError("画面中检测到多张人脸，请仅保留一人")
        x, y, width, height = faces[0]
        padding = int(max(width, height) * 0.12)
        x0, y0 = max(0, x - padding), max(0, y - padding)
        x1 = min(gray.shape[1], x + width + padding)
        y1 = min(gray.shape[0], y + height + padding)
        face = gray[y0:y1, x0:x1]
        quality = self._quality(face, width * height, gray.shape[0] * gray.shape[1])
        return self._feature(face), quality

    def _feature(self, face: np.ndarray) -> np.ndarray:
        normalized = cv2.resize(face, (128, 128), interpolation=cv2.INTER_AREA)
        normalized = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(normalized)
        float_face = normalized.astype(np.float32) / 255.0
        dct = cv2.dct(float_face)[1:33, 1:33].reshape(-1)
        gradients_x = cv2.Sobel(float_face, cv2.CV_32F, 1, 0)
        gradients_y = cv2.Sobel(float_face, cv2.CV_32F, 0, 1)
        gradient = cv2.resize(cv2.magnitude(gradients_x, gradients_y), (16, 16)).reshape(-1)
        feature = np.concatenate((dct, gradient)).astype(np.float32)
        norm = float(np.linalg.norm(feature))
        if norm == 0:
            raise ValueError("人脸图像缺少有效特征")
        return feature / norm

    def _quality(self, face: np.ndarray, face_area: int, image_area: int) -> float:
        blur = float(cv2.Laplacian(face, cv2.CV_64F).var())
        sharpness_score = min(100.0, blur / 1.25)
        brightness = float(face.mean())
        brightness_score = max(0.0, 100.0 - abs(brightness - 130.0) * 1.15)
        size_score = min(100.0, face_area / max(1, image_area) * 700.0)
        return sharpness_score * 0.45 + brightness_score * 0.35 + size_score * 0.2

    def _similarity(self, current: np.ndarray, enrolled: np.ndarray) -> float:
        cosine = float(np.dot(current, enrolled) / (np.linalg.norm(current) * np.linalg.norm(enrolled)))
        return max(0.0, min(1.0, (cosine + 1.0) / 2.0))

    def _feature_path(self, subject_id: int) -> Path:
        if subject_id <= 0:
            raise ValueError("用户标识无效")
        return settings.feature_dir / f"subject-{subject_id}.npz"
