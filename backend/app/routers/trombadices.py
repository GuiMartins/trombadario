from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Role, Task, Trombadice, User
from app.schemas import TrombadiceCreate, TrombadiceOut, TrombadiceUpdate

router = APIRouter(prefix="/api/trombadices", tags=["trombadices"])

NOT_FOUND = HTTPException(
    status_code=status.HTTP_404_NOT_FOUND, detail="Trombadice não encontrada"
)


def _get_or_404(db: DbSession, trombadice_id: int) -> Trombadice:
    trombadice = db.get(Trombadice, trombadice_id)
    if trombadice is None:
        raise NOT_FOUND
    return trombadice


def _check_task(db: DbSession, task_id: int | None, child_id: int) -> None:
    """A trombadice can point at the task that wasn't done - but only at a task
    belonging to the same child, otherwise the link would state something
    false."""
    if task_id is None:
        return
    task = db.get(Task, task_id)
    if task is None or task.child_id != child_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Essa tarefa não é desse filho",
        )


@router.get("", response_model=list[TrombadiceOut])
def list_trombadices(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
) -> list[Trombadice]:
    query = select(Trombadice).order_by(Trombadice.occurred_at.desc(), Trombadice.id.desc())
    if current_user.role is Role.ADMIN:
        if child_id is not None:
            query = query.where(Trombadice.child_id == child_id)
    else:
        # The child_id query param is ignored for a child - the scope is their
        # own id, whatever they ask for.
        query = query.where(Trombadice.child_id == current_user.id)
    return list(db.scalars(query))


@router.get("/{trombadice_id}", response_model=TrombadiceOut)
def get_trombadice(trombadice_id: int, current_user: CurrentUser, db: DbSession) -> Trombadice:
    trombadice = _get_or_404(db, trombadice_id)
    if current_user.role is not Role.ADMIN and trombadice.child_id != current_user.id:
        # 404, not 403: a child probing ids shouldn't learn that a trombadice
        # about someone else exists.
        raise NOT_FOUND
    return trombadice


@router.post("", response_model=TrombadiceOut, status_code=status.HTTP_201_CREATED)
def create_trombadice(payload: TrombadiceCreate, admin: AdminUser, db: DbSession) -> Trombadice:
    child = db.get(User, payload.child_id)
    if child is None or child.role is not Role.CHILD:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")
    _check_task(db, payload.task_id, payload.child_id)

    trombadice = Trombadice(
        title=payload.title,
        description=payload.description,
        occurred_at=payload.occurred_at,
        child_id=payload.child_id,
        task_id=payload.task_id,
        author_id=admin.id,
    )
    db.add(trombadice)
    db.commit()
    db.refresh(trombadice)
    return trombadice


@router.patch("/{trombadice_id}", response_model=TrombadiceOut)
def update_trombadice(
    trombadice_id: int, payload: TrombadiceUpdate, admin: AdminUser, db: DbSession
) -> Trombadice:
    trombadice = _get_or_404(db, trombadice_id)
    data = payload.model_dump(exclude_unset=True)

    if "task_id" in data:
        _check_task(db, data["task_id"], trombadice.child_id)

    for field, value in data.items():
        setattr(trombadice, field, value)
    db.commit()
    db.refresh(trombadice)
    return trombadice


@router.delete("/{trombadice_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_trombadice(trombadice_id: int, admin: AdminUser, db: DbSession) -> None:
    db.delete(_get_or_404(db, trombadice_id))
    db.commit()
