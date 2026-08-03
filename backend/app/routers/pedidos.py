from datetime import UTC, datetime

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, ChildUser, CurrentUser, DbSession
from app.models import Pedido, RequestStatus, Role, User
from app.schemas import PedidoCreate, PedidoDecision, PedidoOut
from app.visto import marcar_decisao_vista, marcar_visto_pai

router = APIRouter(prefix="/api/pedidos", tags=["pedidos"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pedido não encontrado")


def _get_or_404(db: DbSession, pedido_id: int) -> Pedido:
    pedido = db.get(Pedido, pedido_id)
    if pedido is None:
        raise NOT_FOUND
    return pedido


def _escopo(query, current_user: User, child_id: int | None):
    """Mesmo padrão de trombadices/tarefas/castigos: o pai filtra por
    qualquer filho (ou vê todos), o filho só enxerga os próprios - o
    child_id pedido por ele é ignorado, nunca respeitado."""
    if current_user.role is Role.ADMIN:
        return query.where(Pedido.child_id == child_id) if child_id is not None else query
    return query.where(Pedido.child_id == current_user.id)


@router.get("", response_model=list[PedidoOut])
def list_pedidos(
    current_user: CurrentUser, db: DbSession, child_id: int | None = None
) -> list[Pedido]:
    query = _escopo(select(Pedido).order_by(Pedido.created_at.desc()), current_user, child_id)
    pedidos = list(db.scalars(query))
    if current_user.role is Role.ADMIN:
        marcar_visto_pai(db, [p for p in pedidos if p.status is RequestStatus.PENDENTE])
    else:
        marcar_decisao_vista(db, pedidos, current_user)
    return pedidos


@router.get("/{pedido_id}", response_model=PedidoOut)
def get_pedido(pedido_id: int, current_user: CurrentUser, db: DbSession) -> Pedido:
    pedido = _get_or_404(db, pedido_id)
    if current_user.role is not Role.ADMIN and pedido.child_id != current_user.id:
        # 404, não 403 - mesmo padrão de toda leitura de item único no projeto.
        raise NOT_FOUND
    if current_user.role is Role.ADMIN:
        if pedido.status is RequestStatus.PENDENTE:
            marcar_visto_pai(db, [pedido])
    else:
        marcar_decisao_vista(db, [pedido], current_user)
    return pedido


@router.post("", response_model=PedidoOut, status_code=status.HTTP_201_CREATED)
def create_pedido(payload: PedidoCreate, child: ChildUser, db: DbSession) -> Pedido:
    if not child.can_request:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Pedidos não estão liberados pra essa conta",
        )
    pedido = Pedido(title=payload.title, justification=payload.justification, child_id=child.id)
    db.add(pedido)
    db.commit()
    db.refresh(pedido)
    return pedido


@router.patch("/{pedido_id}/decidir", response_model=PedidoOut)
def decide_pedido(
    pedido_id: int, payload: PedidoDecision, admin: AdminUser, db: DbSession
) -> Pedido:
    pedido = _get_or_404(db, pedido_id)
    if pedido.status is not RequestStatus.PENDENTE:
        # Imutável depois de resolvido - mesmo espírito de Punishment.starts_at
        # não ser editável: o passado não se reescreve.
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="Esse pedido já foi decidido"
        )
    pedido.status = payload.status
    pedido.decision_note = payload.decision_note
    pedido.decided_at = datetime.now(UTC)
    pedido.decided_by_id = admin.id
    db.commit()
    db.refresh(pedido)
    return pedido
