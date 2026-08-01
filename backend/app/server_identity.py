from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ServerIdentity


def get_server_id(db: Session) -> str:
    """Returns the server's UUID, creating it on first call. Lives in the
    database rather than the .env so it survives a redeploy without the user
    having to keep track of it - if it changed, every app would think it had
    left home."""
    identity = db.scalar(select(ServerIdentity).limit(1))
    if identity is None:
        identity = ServerIdentity()
        db.add(identity)
        db.commit()
        db.refresh(identity)
    return identity.server_id
