from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.models import ServerIdentity

_cached_secret_key: str | None = None


def get_identity(db: Session) -> ServerIdentity:
    """Returns the single identity row, creating it on first call. Lives in the
    database rather than the .env so it survives a redeploy without the user
    having to keep track of it - if the server_id changed, every app would think
    it had left home."""
    identity = db.scalar(select(ServerIdentity).limit(1))
    if identity is None:
        identity = ServerIdentity()
        db.add(identity)
        db.commit()
        db.refresh(identity)
    return identity


def get_server_id(db: Session) -> str:
    return get_identity(db).server_id


def current_secret_key() -> str:
    """The JWT signing key. An explicit SECRET_KEY in the environment always
    wins; otherwise it is generated once and kept in the database.

    Generating it matters for the CasaOS store install: the app has to come up
    secure with zero configuration, and a shipped default would mean every
    install in the world signs tokens with the same key."""
    settings = get_settings()
    if settings.secret_key:
        return settings.secret_key

    global _cached_secret_key
    if _cached_secret_key is None:
        # Imported here to avoid a circular import at module load: database
        # imports config, and security imports this.
        from app.database import SessionLocal

        with SessionLocal() as db:
            _cached_secret_key = get_identity(db).secret_key
    return _cached_secret_key


def reset_secret_key_cache() -> None:
    """Only used by tests, which swap the database between cases."""
    global _cached_secret_key
    _cached_secret_key = None
