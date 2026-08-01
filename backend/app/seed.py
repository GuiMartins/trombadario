import logging

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.models import Role, User
from app.security import hash_password

logger = logging.getLogger(__name__)


def seed_admin(db: Session) -> None:
    """Creates the admin account on first start. Idempotent, and deliberately
    does NOT rewrite the password of an existing account: once the app is
    running, passwords are changed in the app, and re-applying the .env on every
    restart would silently undo that."""
    settings = get_settings()

    if db.scalar(select(User).where(User.username == settings.admin_username)):
        return

    if db.scalar(select(User).where(User.role == Role.ADMIN)):
        logger.warning(
            "Já existe um admin com outro nome de usuário - ADMIN_USERNAME=%s foi ignorado",
            settings.admin_username,
        )
        return

    db.add(
        User(
            username=settings.admin_username,
            password_hash=hash_password(settings.admin_password),
            display_name=settings.admin_display_name,
            role=Role.ADMIN,
        )
    )
    db.commit()
    logger.info("Conta de admin '%s' criada a partir do .env", settings.admin_username)
