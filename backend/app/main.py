import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles

from alembic import command
from alembic.config import Config
from app.config import get_settings
from app.database import SessionLocal
from app.routers import auth, health, punishments, setup, tasks, trombadices, users
from app.server_identity import get_identity
from app.setup_state import setup_required
from app.web import routes as web_routes
from app.web.deps import Forbidden, RedirectTo

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
        identity = get_identity(db)
        logger.info("server_id: %s", identity.server_id)
        if setup_required(db):
            logger.info("Nenhum admin cadastrado - abra o servidor no navegador para configurar")
    yield


app = FastAPI(title="Trombadário", lifespan=lifespan)


# A dependency can't return a response, so the web layer signals redirects and
# the child-blocked page by raising; these turn them back into responses.
@app.exception_handler(RedirectTo)
async def _redirect_handler(request: Request, exc: RedirectTo) -> RedirectResponse:
    return RedirectResponse(exc.url, status_code=303)


@app.exception_handler(Forbidden)
async def _forbidden_handler(request: Request, exc: Forbidden):
    return web_routes.templates.TemplateResponse(
        request=request, name="bloqueado.html", context={"user": None}, status_code=403
    )


app.mount(
    "/static",
    StaticFiles(directory=str(web_routes.BASE_DIR / "static")),
    name="static",
)

app.include_router(health.router)
app.include_router(setup.router)
app.include_router(auth.router)
app.include_router(trombadices.router)
app.include_router(tasks.router)
app.include_router(punishments.router)
app.include_router(users.router)
# Last: its routes live at the root and would otherwise shadow /api paths.
app.include_router(web_routes.router)
