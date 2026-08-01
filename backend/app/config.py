from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    admin_username: str = "pai"
    admin_password: str = "changeme"
    admin_display_name: str = "Pai"

    secret_key: str = "changeme"
    algorithm: str = "HS256"
    # 30 days: this is a family app on a home LAN and the network gate already
    # blocks any use outside the house, so a short expiry would only mean
    # retyping passwords for no security gain.
    access_token_expire_minutes: int = 43200

    database_url: str = "sqlite:///./data/trombadario.db"


@lru_cache
def get_settings() -> Settings:
    return Settings()
