from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "LMSNetCafe Face Service"
    feature_dir: str = "data/features"
    upload_dir: str = "data/uploads"
    verify_threshold: float = 0.82

    model_config = SettingsConfigDict(env_prefix="FACE_", env_file=".env", extra="ignore")


settings = Settings()
