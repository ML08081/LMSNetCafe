from fastapi import APIRouter, UploadFile

from app.models.schemas import FaceMatchResponse
from app.services.face_matcher import FaceMatcher

router = APIRouter()
matcher = FaceMatcher()


@router.post("/enroll", response_model=FaceMatchResponse)
async def enroll(member_id: int, image: UploadFile) -> FaceMatchResponse:
    return await matcher.enroll(member_id=member_id, image=image)


@router.post("/verify", response_model=FaceMatchResponse)
async def verify(member_id: int, image: UploadFile) -> FaceMatchResponse:
    return await matcher.verify(member_id=member_id, image=image)


@router.post("/identify", response_model=FaceMatchResponse)
async def identify(image: UploadFile) -> FaceMatchResponse:
    return await matcher.identify(image=image)
