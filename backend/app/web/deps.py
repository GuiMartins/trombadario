from typing import Annotated

from fastapi import Depends, Request
from sqlalchemy import select

from app.deps import DbSession
from app.models import Role, User
from app.security import decode_access_token
from app.setup_state import setup_required

SESSION_COOKIE = "trombadario_session"


class RedirectTo(Exception):  # noqa: N818  (it is a control-flow signal, not a failure)
    """Raised by the dependencies below to bounce the browser somewhere else.

    A FastAPI dependency can't return a response, so redirecting from one takes
    an exception plus a handler (registered in app/main.py)."""

    def __init__(self, url: str) -> None:
        self.url = url


class Forbidden(Exception):
    """A child reached the web panel. Rendered as a page, not a bare 403, so
    they get told where to go instead of a blank error."""


def web_user(request: Request, db: DbSession) -> User | None:
    """Reads the session from the cookie. Browsers don't send an Authorization
    header on plain navigation, so the web transports the same JWT in an
    HttpOnly cookie while the Android app keeps using Bearer."""
    token = request.cookies.get(SESSION_COOKIE)
    if not token:
        return None

    payload = decode_access_token(token)
    if payload is None or not payload.get("sub"):
        return None

    user = db.scalar(select(User).where(User.username == payload["sub"]))
    return user if user is not None and user.is_active else None


def require_setup_done(db: DbSession) -> None:
    if setup_required(db):
        raise RedirectTo("/setup")


def require_admin_web(
    db: DbSession,
    user: Annotated[User | None, Depends(web_user)],
    _: Annotated[None, Depends(require_setup_done)],
) -> User:
    """The web panel is admin-only, and that is a security decision, not a
    convenience one: a browser can screenshot freely, so letting a child in here
    would undo the FLAG_SECURE the Android app relies on."""
    if user is None:
        raise RedirectTo("/login")
    if user.role is not Role.ADMIN:
        raise Forbidden
    return user


AdminWeb = Annotated[User, Depends(require_admin_web)]
MaybeUser = Annotated[User | None, Depends(web_user)]
