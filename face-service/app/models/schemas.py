from pydantic import BaseModel


class FaceMatchResponse(BaseModel):
    matched: bool
    subject_id: int | None = None
    member_id: int | None = None
    similarity: float
    quality_score: float | None = None
    feature_ref: str | None = None
    message: str
