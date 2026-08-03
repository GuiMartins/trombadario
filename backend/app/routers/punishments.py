from datetime import UTC, date, datetime

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Category, Kind, Punishment, Role, Trombadice, User
from app.periodo import data_local, intervalo
from app.schemas import DatasComRegistro, PunishmentCreate, PunishmentOut, PunishmentUpdate
from app.visto import marcar_visto

router = APIRouter(prefix="/api/punishments", tags=["punishments"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Castigo não encontrado")


def _serialize(punishment: Punishment, now: datetime) -> PunishmentOut:
    return PunishmentOut(
        id=punishment.id,
        reason=punishment.reason,
        starts_at=punishment.starts_at,
        ends_at=punishment.ends_at,
        ended_early_at=punishment.ended_early_at,
        seen_at=punishment.seen_at,
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
    if any(t.kind is not Kind.TROMBADICE for t in found):
        # Castigo por conquista não é castigo, é engano de digitação.
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Conquista não causa castigo",
        )
    return found


def _escopo(query, current_user: User, child_id: int | None):
    if current_user.role is Role.ADMIN:
        return query.where(Punishment.child_id == child_id) if child_id is not None else query
    return query.where(Punishment.child_id == current_user.id)


def _filtros(query, category: Category | None, de: date | None, ate: date | None, q: str | None):
    if category is not None:
        # Castigo não tem categoria própria - ele herda a das trombadices que o
        # causaram. Filtrar por "agressão" aqui quer dizer "castigos que vieram
        # de alguma agressão", que é a pergunta que o pai faz de verdade.
        query = query.where(
            Punishment.trombadices.any(Trombadice.category == category)
        )

    inicio, fim = intervalo(de, ate)
    # Pega o castigo que **encosta** no período, não só o que começa dentro
    # dele: um castigo de uma semana tem que aparecer no filtro de qualquer dia
    # em que a pessoa estava de castigo.
    if inicio is not None:
        query = query.where(Punishment.ends_at >= inicio)
    if fim is not None:
        query = query.where(Punishment.starts_at < fim)

    if q and (termo := q.strip()):
        query = query.where(Punishment.reason.ilike(f"%{termo}%"))
    return query


@router.get("", response_model=list[PunishmentOut])
def list_punishments(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
    category: Category | None = None,
    de: date | None = None,
    ate: date | None = None,
    q: str | None = None,
) -> list[PunishmentOut]:
    now = datetime.now(UTC)
    query = select(Punishment).order_by(Punishment.starts_at.desc(), Punishment.id.desc())
    query = _filtros(_escopo(query, current_user, child_id), category, de, ate, q)
    achados = list(db.scalars(query))
    marcar_visto(db, achados, current_user)
    return [_serialize(p, now) for p in achados]


@router.get("/datas", response_model=DatasComRegistro)
def dates_with_punishments(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
) -> DatasComRegistro:
    """Todo dia em que houve castigo, não só o dia em que começou - é o que o
    calendário precisa para deixar clicar em qualquer dia de uma semana de
    castigo."""
    dias: set[date] = set()
    for p in db.scalars(_escopo(select(Punishment), current_user, child_id)):
        fim = min(p.ended_early_at, p.ends_at) if p.ended_early_at else p.ends_at
        dia = data_local(p.starts_at)
        ultimo = data_local(fim)
        while dia <= ultimo:
            dias.add(dia)
            dia = dia.fromordinal(dia.toordinal() + 1)
    return DatasComRegistro(datas=sorted(dias))


@router.get("/current", response_model=list[PunishmentOut])
def current_punishments(current_user: CurrentUser, db: DbSession) -> list[PunishmentOut]:
    """What the child's punishment screen asks: am I grounded right now?
    Returns an empty list when not - the app renders that as the good news."""
    now = datetime.now(UTC)
    query = select(Punishment)
    if current_user.role is not Role.ADMIN:
        query = query.where(Punishment.child_id == current_user.id)
    # Só os que estão valendo agora: é o que aparece na tela do filho, e é
    # sobre esses que o pai quer saber se ele viu.
    ativos = [p for p in db.scalars(query) if p.is_active_at(now)]
    marcar_visto(db, ativos, current_user)
    return [_serialize(p, now) for p in ativos]


@router.get("/{punishment_id}", response_model=PunishmentOut)
def get_punishment(punishment_id: int, current_user: CurrentUser, db: DbSession) -> PunishmentOut:
    punishment = _get_or_404(db, punishment_id)
    if current_user.role is not Role.ADMIN and punishment.child_id != current_user.id:
        raise NOT_FOUND
    marcar_visto(db, [punishment], current_user)
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
