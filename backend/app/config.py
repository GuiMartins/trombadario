from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Empty means "generate one and keep it in the database" - see
    # app/server_identity.py. There is deliberately no shipped default: the app
    # is installable from the CasaOS store and has to come up secure with no
    # configuration at all.
    secret_key: str = ""
    algorithm: str = "HS256"
    # 30 days: this is a family app on a home LAN and the network gate already
    # blocks any use outside the house, so a short expiry would only mean
    # retyping passwords for no security gain.
    access_token_expire_minutes: int = 43200

    database_url: str = "sqlite:///./data/trombadario.db"


@lru_cache
def get_settings() -> Settings:
    return Settings()
