from pydantic import BaseModel


class FaceMatchResponse(BaseModel):
    matched: bool
    member_id: int | None = None
    similarity: float
    message: str
