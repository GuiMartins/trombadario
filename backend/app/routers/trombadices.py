from datetime import date

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import or_, select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Category, Role, Task, Trombadice, User
from app.periodo import data_local, intervalo
from app.schemas import DatasComRegistro, TrombadiceCreate, TrombadiceOut, TrombadiceUpdate
from app.visto import marcar_visto

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


def _escopo(query, current_user: User, child_id: int | None):
    """Quem pode ver o quê. Para o filho o `child_id` pedido é ignorado: o
    escopo é o id dele, peça o que pedir."""
    if current_user.role is Role.ADMIN:
        return query.where(Trombadice.child_id == child_id) if child_id is not None else query
    return query.where(Trombadice.child_id == current_user.id)


def _filtros(query, category: Category | None, de: date | None, ate: date | None, q: str | None):
    if category is not None:
        query = query.where(Trombadice.category == category)

    inicio, fim = intervalo(de, ate)
    if inicio is not None:
        query = query.where(Trombadice.occurred_at >= inicio)
    if fim is not None:
        query = query.where(Trombadice.occurred_at < fim)

    if q and (termo := q.strip()):
        # `ilike` no SQLite só ignora maiúscula em ASCII: "Escola" acha
        # "escola", mas "ÁGUA" não acha "água". Vale o que custa - o alternativo
        # é carregar uma extensão de collation no container.
        alvo = f"%{termo}%"
        query = query.where(
            or_(Trombadice.title.ilike(alvo), Trombadice.description.ilike(alvo))
        )
    return query


@router.get("", response_model=list[TrombadiceOut])
def list_trombadices(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
    category: Category | None = None,
    de: date | None = None,
    ate: date | None = None,
    q: str | None = None,
) -> list[Trombadice]:
    query = select(Trombadice).order_by(Trombadice.occurred_at.desc(), Trombadice.id.desc())
    query = _filtros(_escopo(query, current_user, child_id), category, de, ate, q)
    achadas = list(db.scalars(query))
    # O feed do filho está aberto na frente dele: isto é o "visto".
    marcar_visto(db, achadas, current_user)
    return achadas


@router.get("/datas", response_model=DatasComRegistro)
def dates_with_trombadices(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
    category: Category | None = None,
    q: str | None = None,
) -> DatasComRegistro:
    """Os dias que têm alguma coisa, para o calendário do filtro só deixar
    clicar neles.

    Agrupado em Python e não em SQL de propósito: o banco guarda UTC e o dia
    que interessa é o local, então agrupar por `date(occurred_at)` no SQLite
    jogaria tudo que aconteceu depois das 21h para o dia seguinte."""
    query = _filtros(_escopo(select(Trombadice), current_user, child_id), category, None, None, q)
    dias = {data_local(t.occurred_at) for t in db.scalars(query)}
    return DatasComRegistro(datas=sorted(dias))


@router.get("/{trombadice_id}", response_model=TrombadiceOut)
def get_trombadice(trombadice_id: int, current_user: CurrentUser, db: DbSession) -> Trombadice:
    trombadice = _get_or_404(db, trombadice_id)
    if current_user.role is not Role.ADMIN and trombadice.child_id != current_user.id:
        # 404, not 403: a child probing ids shouldn't learn that a trombadice
        # about someone else exists.
        raise NOT_FOUND
    marcar_visto(db, [trombadice], current_user)
    return trombadice


@router.post("", response_model=TrombadiceOut, status_code=status.HTTP_201_CREATED)
def create_trombadice(payload: TrombadiceCreate, admin: AdminUser, db: DbSession) -> Trombadice:
    child = db.get(User, payload.child_id)
    if child is None or child.role is not Role.CHILD:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")
    _check_task(db, payload.task_id, payload.child_id)

    # Sem título e com tarefa atrelada, o título é o nome da tarefa: o que
    # aconteceu foi não ter feito aquilo. Escrever de novo na mão só criaria
    # duas versões do mesmo nome.
    titulo = payload.title.strip()
    if not titulo and payload.task_id is not None:
        titulo = db.get(Task, payload.task_id).name

    trombadice = Trombadice(
        title=titulo,
        description=payload.description,
        category=payload.category,
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
