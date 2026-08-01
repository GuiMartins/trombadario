from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Event, Role, User
from app.schemas import EventCreate, EventOut, EventUpdate

router = APIRouter(prefix="/api/events", tags=["events"])


def _get_or_404(db: DbSession, event_id: int) -> Event:
    event = db.get(Event, event_id)
    if event is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Acontecimento não encontrado")
    return event


@router.get("", response_model=list[EventOut])
def list_events(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
) -> list[Event]:
    query = select(Event).order_by(Event.occurred_at.desc(), Event.id.desc())
    if current_user.role is Role.ADMIN:
        if child_id is not None:
            query = query.where(Event.child_id == child_id)
    else:
        # The child_id query param is ignored for a child - the scope is their
        # own id, whatever they ask for.
        query = query.where(Event.child_id == current_user.id)
    return list(db.scalars(query))


@router.get("/{event_id}", response_model=EventOut)
def get_event(event_id: int, current_user: CurrentUser, db: DbSession) -> Event:
    event = _get_or_404(db, event_id)
    if current_user.role is not Role.ADMIN and event.child_id != current_user.id:
        # 404, not 403: a child probing ids shouldn't learn that an event about
        # someone else exists.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Acontecimento não encontrado")
    return event


@router.post("", response_model=EventOut, status_code=status.HTTP_201_CREATED)
def create_event(payload: EventCreate, admin: AdminUser, db: DbSession) -> Event:
    child = db.get(User, payload.child_id)
    if child is None or child.role is not Role.CHILD:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")

    event = Event(
        title=payload.title,
        description=payload.description,
        occurred_at=payload.occurred_at,
        child_id=payload.child_id,
        author_id=admin.id,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


@router.patch("/{event_id}", response_model=EventOut)
def update_event(event_id: int, payload: EventUpdate, admin: AdminUser, db: DbSession) -> Event:
    event = _get_or_404(db, event_id)
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(event, field, value)
    db.commit()
    db.refresh(event)
    return event


@router.delete("/{event_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_event(event_id: int, admin: AdminUser, db: DbSession) -> None:
    db.delete(_get_or_404(db, event_id))
    db.commit()
