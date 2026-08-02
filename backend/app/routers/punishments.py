from datetime import UTC, datetime

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Punishment, Role, Trombadice, User
from app.schemas import PunishmentCreate, PunishmentOut, PunishmentUpdate

router = APIRouter(prefix="/api/punishments", tags=["punishments"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Castigo não encontrado")


def _serialize(punishment: Punishment, now: datetime) -> PunishmentOut:
    return PunishmentOut(
        id=punishment.id,
        reason=punishment.reason,
        starts_at=punishment.starts_at,
        ends_at=punishment.ends_at,
        ended_early_at=punishment.ended_early_at,
        child_id=punishment.child_id,
        author_id=punishment.author_id,
        created_at=punishment.created_at,
        trombadice_ids=[t.id for t in punishment.trombadices],
        is_active=punishment.is_active_at(now),
    )


def _get_or_404(db: DbSession, punishment_id: int) -> Punishment:
    punishment = db.get(Punishment, punishment_id)
    if punishment is None:
        raise NOT_FOUND
    return punishment


def _resolve_trombadices(db: DbSession, ids: list[int], child_id: int) -> list[Trombadice]:
    """Only the child's own trombadices can justify their punishment - linking
    someone else's would put a sibling's name on this record."""
    if not ids:
        return []
    found = list(db.scalars(select(Trombadice).where(Trombadice.id.in_(ids))))
    if len(found) != len(set(ids)) or any(t.child_id != child_id for t in found):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Trombadice inválida para esse filho",
        )
    return found


@router.get("", response_model=list[PunishmentOut])
def list_punishments(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
) -> list[PunishmentOut]:
    now = datetime.now(UTC)
    query = select(Punishment).order_by(Punishment.starts_at.desc(), Punishment.id.desc())
    if current_user.role is Role.ADMIN:
        if child_id is not None:
            query = query.where(Punishment.child_id == child_id)
    else:
        query = query.where(Punishment.child_id == current_user.id)
    return [_serialize(p, now) for p in db.scalars(query)]


@router.get("/current", response_model=list[PunishmentOut])
def current_punishments(current_user: CurrentUser, db: DbSession) -> list[PunishmentOut]:
    """What the child's punishment screen asks: am I grounded right now?
    Returns an empty list when not - the app renders that as the good news."""
    now = datetime.now(UTC)
    query = select(Punishment)
    if current_user.role is not Role.ADMIN:
        query = query.where(Punishment.child_id == current_user.id)
    return [_serialize(p, now) for p in db.scalars(query) if p.is_active_at(now)]


@router.get("/{punishment_id}", response_model=PunishmentOut)
def get_punishment(punishment_id: int, current_user: CurrentUser, db: DbSession) -> PunishmentOut:
    punishment = _get_or_404(db, punishment_id)
    if current_user.role is not Role.ADMIN and punishment.child_id != current_user.id:
        raise NOT_FOUND
    return _serialize(punishment, datetime.now(UTC))


@router.post("", response_model=PunishmentOut, status_code=status.HTTP_201_CREATED)
def create_punishment(payload: PunishmentCreate, admin: AdminUser, db: DbSession) -> PunishmentOut:
    child = db.get(User, payload.child_id)
    if child is None or child.role is not Role.CHILD:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")

    starts_at = payload.starts_at or datetime.now(UTC)
    if payload.ends_at <= starts_at:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="O castigo precisa terminar depois de começar",
        )

    punishment = Punishment(
        reason=payload.reason,
        starts_at=starts_at,
        ends_at=payload.ends_at,
        child_id=payload.child_id,
        author_id=admin.id,
    )
    punishment.trombadices = _resolve_trombadices(db, payload.trombadice_ids, payload.child_id)
    db.add(punishment)
    db.commit()
    db.refresh(punishment)
    return _serialize(punishment, datetime.now(UTC))


@router.patch("/{punishment_id}", response_model=PunishmentOut)
def update_punishment(
    punishment_id: int, payload: PunishmentUpdate, admin: AdminUser, db: DbSession
) -> PunishmentOut:
    punishment = _get_or_404(db, punishment_id)
    data = payload.model_dump(exclude_unset=True)

    if data.pop("end_now", False):
        punishment.ended_early_at = datetime.now(UTC)

    if (ids := data.pop("trombadice_ids", None)) is not None:
        punishment.trombadices = _resolve_trombadices(db, ids, punishment.child_id)

    if (ends_at := data.get("ends_at")) is not None and ends_at <= punishment.starts_at:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="O castigo precisa terminar depois de começar",
        )

    for field, value in data.items():
        setattr(punishment, field, value)
    db.commit()
    db.refresh(punishment)
    return _serialize(punishment, datetime.now(UTC))


@router.delete("/{punishment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_punishment(punishment_id: int, admin: AdminUser, db: DbSession) -> None:
    db.delete(_get_or_404(db, punishment_id))
    db.commit()
