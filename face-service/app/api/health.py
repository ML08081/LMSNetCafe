from datetime import datetime, timezone

from fastapi import APIRouter
from app.api.face import matcher

router = APIRouter()


@router.get("/health")
def health() -> dict:
    readiness = matcher.readiness()
    return {
        "service": "lms-netcafe-face-service",
        "status": "UP" if readiness["ready"] else "DEGRADED",
        **readiness,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
