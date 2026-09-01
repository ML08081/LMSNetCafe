from io import BytesIO

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile

from app.models.schemas import FaceMatchResponse
from app.services.face_matcher import FaceMatcher

router = APIRouter()
matcher = FaceMatcher()


@router.post("/enroll", response_model=FaceMatchResponse)
async def enroll(
    subject_id: int = Form(...),
    member_id: int | None = Form(default=None),
    image: UploadFile = File(...),
) -> FaceMatchResponse:
    try:
        return await matcher.enroll(subject_id=subject_id, member_id=member_id, image=image)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@router.post("/verify", response_model=FaceMatchResponse)
async def verify(
    subject_id: int = Form(...),
    member_id: int | None = Form(default=None),
    image: UploadFile = File(...),
) -> FaceMatchResponse:
    try:
        return await matcher.verify(subject_id=subject_id, member_id=member_id, image=image)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@router.post("/identify", response_model=FaceMatchResponse)
async def identify(image: UploadFile = File(...)) -> FaceMatchResponse:
    try:
        return await matcher.identify(image=image)
    except ValueError as exc:
        return FaceMatchResponse(
            matched=False,
            similarity=0.0,
            message=str(exc),
        )


@router.delete("/subjects/{subject_id}", response_model=FaceMatchResponse)
async def remove(subject_id: int) -> FaceMatchResponse:
    return matcher.remove(subject_id)


@router.post("/internal/enroll", response_model=FaceMatchResponse)
async def internal_enroll(
    request: Request,
    subject_id: int,
    member_id: int | None = None,
) -> FaceMatchResponse:
    try:
        return await matcher.enroll(subject_id, member_id, await raw_upload(request))
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@router.post("/internal/verify", response_model=FaceMatchResponse)
async def internal_verify(
    request: Request,
    subject_id: int,
    member_id: int | None = None,
) -> FaceMatchResponse:
    try:
        return await matcher.verify(subject_id, member_id, await raw_upload(request))
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@router.post("/internal/identify", response_model=FaceMatchResponse)
async def internal_identify(request: Request) -> FaceMatchResponse:
    try:
        return await matcher.identify(await raw_upload(request))
    except ValueError as exc:
        return FaceMatchResponse(matched=False, similarity=0.0, message=str(exc))


async def raw_upload(request: Request) -> UploadFile:
    content = await request.body()
    return UploadFile(file=BytesIO(content), filename="capture.jpg")
