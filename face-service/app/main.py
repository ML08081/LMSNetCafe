from fastapi import FastAPI

from app.api.face import router as face_router
from app.api.health import router as health_router
from app.core.config import settings

app = FastAPI(title=settings.app_name, version="0.1.0")

app.include_router(health_router, prefix="/face-service")
app.include_router(face_router, prefix="/face-service")
