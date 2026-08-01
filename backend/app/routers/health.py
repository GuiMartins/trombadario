from fastapi import APIRouter

from app.deps import DbSession
from app.schemas import Health
from app.server_identity import get_server_id

router = APIRouter(prefix="/api", tags=["health"])


@router.get("/health", response_model=Health)
def health(db: DbSession) -> Health:
    """Public on purpose: the app calls this before login to decide whether it
    is on the home network at all (see CLAUDE.md). The server_id is what makes
    "something answered on this port" not good enough."""
    return Health(app="trombadario", server_id=get_server_id(db))
