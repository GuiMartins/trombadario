import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from alembic import command
from alembic.config import Config
from app.config import get_settings
from app.database import SessionLocal
from app.routers import auth, events, health, users
from app.seed import seed_admin
from app.server_identity import get_server_id

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

BASE_DIR = Path(__file__).resolve().parent.parent


def run_migrations() -> None:
    """Applied at startup so a plain `docker compose up -d` is enough to deploy -
    no separate migration step to forget on the server."""
    alembic_cfg = Config(str(BASE_DIR / "alembic.ini"))
    alembic_cfg.set_main_option("script_location", str(BASE_DIR / "alembic"))
    alembic_cfg.set_main_option("sqlalchemy.url", get_settings().database_url)
    command.upgrade(alembic_cfg, "head")


@asynccontextmanager
async def lifespan(app: FastAPI):
    run_migrations()
    with SessionLocal() as db:
        seed_admin(db)
        logger.info("server_id: %s", get_server_id(db))
    yield


app = FastAPI(title="Trombadário", lifespan=lifespan)

app.include_router(health.router)
app.include_router(auth.router)
app.include_router(events.router)
app.include_router(users.router)
