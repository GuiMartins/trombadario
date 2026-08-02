from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Role, User


def setup_required(db: Session) -> bool:
    """True while no active admin exists.

    Derived from the database rather than a flag on disk on purpose: a flag can
    drift out of sync with reality and either lock the owner out or reopen the
    wizard on an app already in use. The question "is there anyone who can
    administer this?" only has one honest source."""
    active_admins = db.scalar(
        select(func.count()).select_from(User).where(User.role == Role.ADMIN, User.is_active)
    )
    return not active_admins
