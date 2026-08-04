"""O que ainda não foi visto, sem gastar o "não visto" de ninguém.

`app/visto.py` marca `seen_at`/`seen_by_*` como efeito colateral de um GET
normal (`trombadices.py`, `punishments.py`, `pedidos.py`) - de propósito, é o
padrão do projeto. Mas um poller em segundo plano não pode chamar essas
rotas: ele precisa perguntar "tem algo novo?" sem consumir o "não visto" antes
da pessoa abrir a tela de verdade. Por isso este router só conta - nunca
importa nada de `app/visto.py`.
"""

from fastapi import APIRouter
from sqlalchemy import func, select

from app.deps import CurrentUser, DbSession
from app.models import Pedido, Punishment, RequestStatus, Role, Trombadice
from app.schemas import UnseenCounts

router = APIRouter(prefix="/api/unseen", tags=["unseen"])


@router.get("", response_model=UnseenCounts)
def unseen(current_user: CurrentUser, db: DbSession) -> UnseenCounts:
    if current_user.role is Role.ADMIN:
        pedidos_pendentes = db.scalar(
            select(func.count())
            .select_from(Pedido)
            .where(Pedido.status == RequestStatus.PENDENTE, Pedido.seen_by_parent_at.is_(None))
        )
        return UnseenCounts(pedidos_pendentes=pedidos_pendentes or 0)

    anotacoes_novas = db.scalar(
        select(func.count())
        .select_from(Trombadice)
        .where(Trombadice.child_id == current_user.id, Trombadice.seen_at.is_(None))
    )
    castigos_novos = db.scalar(
        select(func.count())
        .select_from(Punishment)
        .where(Punishment.child_id == current_user.id, Punishment.seen_at.is_(None))
    )
    decisoes_novas = db.scalar(
        select(func.count())
        .select_from(Pedido)
        .where(
            Pedido.child_id == current_user.id,
            Pedido.decided_at.is_not(None),
            Pedido.seen_by_child_at.is_(None),
        )
    )
    return UnseenCounts(
        anotacoes_novas=anotacoes_novas or 0,
        castigos_novos=castigos_novos or 0,
        decisoes_novas=decisoes_novas or 0,
    )
