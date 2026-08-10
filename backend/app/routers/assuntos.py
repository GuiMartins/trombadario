"""Assuntos pra conversar: a pauta da conversa que vai acontecer pessoalmente.

A única parte do app em que pai e filho escrevem a mesma coisa, do mesmo jeito.
Por isso as rotas de leitura e de criação aceitam `CurrentUser` em vez de
`AdminUser`/`ChildUser` - o que muda entre os dois papéis é o escopo (o filho só
enxerga os assuntos dele) e quem pode encerrar, não a permissão de participar.
"""

from datetime import UTC, datetime

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Assunto, Role, User
from app.schemas import AssuntoConversa, AssuntoCreate, AssuntoOut, AssuntoUpdate
from app.visto import marcar_assunto_visto

router = APIRouter(prefix="/api/assuntos", tags=["assuntos"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assunto não encontrado")
BLOQUEADO = HTTPException(
    status_code=status.HTTP_403_FORBIDDEN,
    detail="Assuntos não estão liberados pra essa conta",
)


def _liberado(current_user: User) -> None:
    """`can_discuss` desligado tira a funcionalidade inteira do filho, não só o
    botão de criar - é o que "desabilitar acesso" quer dizer. Checado aqui e não
    só na tela porque o filho tem o APK na mão."""
    if current_user.role is not Role.ADMIN and not current_user.can_discuss:
        raise BLOQUEADO


def _get_or_404(db: DbSession, assunto_id: int) -> Assunto:
    assunto = db.get(Assunto, assunto_id)
    if assunto is None:
        raise NOT_FOUND
    return assunto


def _visivel_para(assunto: Assunto, current_user: User) -> bool:
    return current_user.role is Role.ADMIN or assunto.child_id == current_user.id


@router.get("", response_model=list[AssuntoOut])
def list_assuntos(
    current_user: CurrentUser,
    db: DbSession,
    child_id: int | None = None,
    pendentes: bool | None = None,
) -> list[Assunto]:
    _liberado(current_user)

    query = select(Assunto).order_by(Assunto.created_at.desc())
    if current_user.role is Role.ADMIN:
        if child_id is not None:
            query = query.where(Assunto.child_id == child_id)
    else:
        # O child_id que o filho mandar é ignorado, nunca respeitado - mesmo
        # padrão de trombadices/tarefas/castigos/pedidos.
        query = query.where(Assunto.child_id == current_user.id)
    if pendentes is not None:
        query = query.where(
            Assunto.talked_at.is_(None) if pendentes else Assunto.talked_at.is_not(None)
        )

    assuntos = list(db.scalars(query))
    marcar_assunto_visto(db, assuntos, current_user)
    return assuntos


@router.get("/{assunto_id}", response_model=AssuntoOut)
def get_assunto(assunto_id: int, current_user: CurrentUser, db: DbSession) -> Assunto:
    _liberado(current_user)
    assunto = _get_or_404(db, assunto_id)
    if not _visivel_para(assunto, current_user):
        # 404, não 403 - sondar id não pode confirmar que o assunto de um irmão
        # existe. Mesmo padrão de toda leitura de item único no projeto.
        raise NOT_FOUND
    marcar_assunto_visto(db, [assunto], current_user)
    return assunto


@router.post("", response_model=AssuntoOut, status_code=status.HTTP_201_CREATED)
def create_assunto(payload: AssuntoCreate, current_user: CurrentUser, db: DbSession) -> Assunto:
    _liberado(current_user)

    if current_user.role is Role.ADMIN:
        child = db.get(User, payload.child_id) if payload.child_id is not None else None
        if child is None or child.role is not Role.CHILD:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filho inválido")
        child_id = child.id
    else:
        child_id = current_user.id

    assunto = Assunto(
        title=payload.title.strip(),
        description=payload.description.strip(),
        child_id=child_id,
        author_id=current_user.id,
    )
    db.add(assunto)
    db.commit()
    db.refresh(assunto)
    return assunto


@router.patch("/{assunto_id}", response_model=AssuntoOut)
def update_assunto(
    assunto_id: int, payload: AssuntoUpdate, current_user: CurrentUser, db: DbSession
) -> Assunto:
    """Corrigir o que se escreveu. **Só o autor** - nem o pai reescreve o assunto
    que o filho trouxe: mudar a pauta do outro é mudar o que ele quis dizer, e o
    pai já tem como encerrar ou apagar o que não deveria estar lá."""
    _liberado(current_user)
    assunto = _get_or_404(db, assunto_id)
    if assunto.author_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN, detail="Só quem escreveu pode corrigir"
        )
    if assunto.talked_at is not None:
        # Já conversado é história: reescrever a pauta depois da conversa faria
        # o registro descrever um assunto que não foi o tratado.
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="Esse assunto já foi conversado"
        )

    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(assunto, field, value.strip() if isinstance(value, str) else value)
    db.commit()
    db.refresh(assunto)
    return assunto


@router.patch("/{assunto_id}/conversado", response_model=AssuntoOut)
def mark_talked(
    assunto_id: int, payload: AssuntoConversa, admin: AdminUser, db: DbSession
) -> Assunto:
    """Só o pai encerra, mesmo quando foi o filho quem trouxe o assunto.

    Não é hierarquia por hierarquia: o filho riscando da lista um assunto que o
    pai levantou apagaria a pauta do outro sem que ninguém tivesse conversado -
    exatamente o que a funcionalidade existe pra evitar."""
    assunto = _get_or_404(db, assunto_id)

    if payload.talked:
        assunto.talked_at = datetime.now(UTC)
        assunto.talked_by_id = admin.id
        assunto.talked_note = payload.note.strip()
    else:
        assunto.talked_at = None
        assunto.talked_by_id = None
        assunto.talked_note = ""

    db.commit()
    db.refresh(assunto)
    return assunto


@router.delete("/{assunto_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_assunto(assunto_id: int, current_user: CurrentUser, db: DbSession) -> None:
    """O pai apaga qualquer um. O filho apaga só o dele, e só enquanto ninguém
    conversou: desistir de um assunto antes da conversa é mudar de ideia, apagar
    depois é sumir com o que já foi tratado."""
    _liberado(current_user)
    assunto = _get_or_404(db, assunto_id)

    if current_user.role is not Role.ADMIN:
        if assunto.author_id != current_user.id:
            raise NOT_FOUND
        if assunto.talked_at is not None:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Assunto já conversado não se apaga",
            )

    db.delete(assunto)
    db.commit()
