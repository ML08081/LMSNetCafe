from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


PROJECT_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    app_name: str = "LMSNetCafe Face Service"
    feature_dir: Path = PROJECT_DIR / "data" / "features"
    upload_dir: Path = PROJECT_DIR / "data" / "uploads"
    model_dir: Path = PROJECT_DIR / "models"
    verify_threshold: float = 0.363
    identify_threshold: float = 0.40
    identify_margin: float = 0.05
    minimum_quality: float = 35.0
    maximum_image_bytes: int = 5 * 1024 * 1024

    model_config = SettingsConfigDict(env_prefix="FACE_", env_file=".env", extra="ignore", protected_namespaces=())


settings = Settings()
