from fastapi import UploadFile

from app.core.config import settings
from app.models.schemas import FaceMatchResponse


class FaceMatcher:
    async def enroll(self, member_id: int, image: UploadFile) -> FaceMatchResponse:
        await image.read()
        return FaceMatchResponse(
            matched=True,
            member_id=member_id,
            similarity=1.0,
            message="development enrollment placeholder",
        )

    async def verify(self, member_id: int, image: UploadFile) -> FaceMatchResponse:
        await image.read()
        return FaceMatchResponse(
            matched=False,
            member_id=member_id,
            similarity=0.0,
            message=f"face engine placeholder, threshold={settings.verify_threshold}",
        )

    async def identify(self, image: UploadFile) -> FaceMatchResponse:
        await image.read()
        return FaceMatchResponse(
            matched=False,
            member_id=None,
            similarity=0.0,
            message="face identification placeholder",
        )
