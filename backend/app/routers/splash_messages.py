import secrets

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import or_, select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Role, SplashMessage, User
from app.schemas import (
    SplashMessageCreate,
    SplashMessageOut,
    SplashMessageRandom,
    SplashMessageUpdate,
)

router = APIRouter(prefix="/api/splash-messages", tags=["splash-messages"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Frase não encontrada")


def _get_or_404(db: DbSession, message_id: int) -> SplashMessage:
    message = db.get(SplashMessage, message_id)
    if message is None:
        raise NOT_FOUND
    return message


def _check_child(db: DbSession, child_id: int | None) -> None:
    """None is valid and means "every child"; anything else has to be a child."""
    if child_id is None:
        return
    child = db.get(User, child_id)
    if child is None or child.role is not Role.CHILD:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")


@router.get("", response_model=list[SplashMessageOut])
def list_messages(admin: AdminUser, db: DbSession) -> list[SplashMessage]:
    """Admin only: the child never sees the whole list, just the one they get.
    Otherwise they would read the phrases written for a sibling."""
    return list(db.scalars(select(SplashMessage).order_by(SplashMessage.id.desc())))


@router.get("/random", response_model=SplashMessageRandom)
def random_message(current_user: CurrentUser, db: DbSession) -> SplashMessageRandom:
    """One phrase for whoever is asking. Drawn server-side so the phone can't
    influence the pick and never receives phrases meant for someone else."""
    query = select(SplashMessage).where(SplashMessage.is_active)
    if current_user.role is not Role.ADMIN:
        query = query.where(
            or_(SplashMessage.child_id.is_(None), SplashMessage.child_id == current_user.id)
        )

    candidates = list(db.scalars(query))
    if not candidates:
        return SplashMessageRandom(text=None)
    # secrets.choice rather than random.choice - no reason to reach for a
    # predictable PRNG when the stdlib hands over a good one.
    return SplashMessageRandom(text=secrets.choice(candidates).text)


@router.post("", response_model=SplashMessageOut, status_code=status.HTTP_201_CREATED)
def create_message(
    payload: SplashMessageCreate, admin: AdminUser, db: DbSession
) -> SplashMessage:
    _check_child(db, payload.child_id)

    message = SplashMessage(
        text=payload.text.strip(),
        child_id=payload.child_id,
        author_id=admin.id,
    )
    db.add(message)
    db.commit()
    db.refresh(message)
    return message


@router.patch("/{message_id}", response_model=SplashMessageOut)
def update_message(
    message_id: int, payload: SplashMessageUpdate, admin: AdminUser, db: DbSession
) -> SplashMessage:
    message = _get_or_404(db, message_id)
    data = payload.model_dump(exclude_unset=True)

    if "child_id" in data:
        _check_child(db, data["child_id"])

    for field, value in data.items():
        setattr(message, field, value)
    db.commit()
    db.refresh(message)
    return message


@router.delete("/{message_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_message(message_id: int, admin: AdminUser, db: DbSession) -> None:
    db.delete(_get_or_404(db, message_id))
    db.commit()
