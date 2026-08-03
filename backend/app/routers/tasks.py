from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Periodicity, Role, Task, User
from app.schemas import TaskCreate, TaskOut, TaskUpdate

router = APIRouter(prefix="/api/tasks", tags=["tasks"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Tarefa não encontrada")


def _get_or_404(db: DbSession, task_id: int) -> Task:
    task = db.get(Task, task_id)
    if task is None:
        raise NOT_FOUND
    return task


def _normalize_schedule(
    periodicity: Periodicity, weekdays: list[int], day_of_month: int | None
) -> tuple[str, int | None]:
    """Keeps the schedule fields honest for the chosen periodicity: a daily task
    carrying leftover weekdays would render as a contradiction in the UI."""
    if periodicity is Periodicity.WEEKLY:
        if not weekdays:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Escolha pelo menos um dia da semana",
            )
        return ",".join(str(day) for day in sorted(set(weekdays))), None

    if periodicity is Periodicity.MONTHLY:
        if day_of_month is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Escolha o dia do mês",
            )
        return "", day_of_month

    return "", None


@router.get("", response_model=list[TaskOut])
def list_tasks(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
) -> list[Task]:
    query = select(Task).order_by(Task.is_active.desc(), Task.name)
    if current_user.role is Role.ADMIN:
        if child_id is not None:
            query = query.where(Task.child_id == child_id)
    else:
        query = query.where(Task.child_id == current_user.id)
    return list(db.scalars(query))


@router.get("/{task_id}", response_model=TaskOut)
def get_task(task_id: int, current_user: CurrentUser, db: DbSession) -> Task:
    task = _get_or_404(db, task_id)
    if current_user.role is not Role.ADMIN and task.child_id != current_user.id:
        # 404, not 403 - same reasoning as trombadices.
        raise NOT_FOUND
    return task


@router.post("", response_model=TaskOut, status_code=status.HTTP_201_CREATED)
def create_task(payload: TaskCreate, admin: AdminUser, db: DbSession) -> Task:
    child = db.get(User, payload.child_id)
    if child is None or child.role is not Role.CHILD:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")

    weekdays, day_of_month = _normalize_schedule(
        payload.periodicity, payload.weekdays, payload.day_of_month
    )

    task = Task(
        name=payload.name,
        description=payload.description,
        periodicity=payload.periodicity,
        weekdays=weekdays,
        day_of_month=day_of_month,
        child_id=payload.child_id,
        author_id=admin.id,
    )
    db.add(task)
    db.commit()
    db.refresh(task)
    return task


@router.patch("/{task_id}", response_model=TaskOut)
def update_task(task_id: int, payload: TaskUpdate, admin: AdminUser, db: DbSession) -> Task:
    task = _get_or_404(db, task_id)
    data = payload.model_dump(exclude_unset=True)

    periodicity = data.pop("periodicity", task.periodicity)
    weekdays = data.pop("weekdays", None)
    day_of_month = data.pop("day_of_month", task.day_of_month)
    if weekdays is None:
        weekdays = [int(day) for day in task.weekdays.split(",") if day.strip()]

    task.periodicity = periodicity
    task.weekdays, task.day_of_month = _normalize_schedule(periodicity, weekdays, day_of_month)

    for field, value in data.items():
        setattr(task, field, value)
    db.commit()
    db.refresh(task)
    return task


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_task(task_id: int, admin: AdminUser, db: DbSession) -> None:
    # Trombadices pointing at this task use ondelete=SET NULL - the record of
    # what happened survives the task that caused it.
    db.delete(_get_or_404(db, task_id))
    db.commit()
