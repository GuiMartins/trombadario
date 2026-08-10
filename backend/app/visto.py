"""Marcar como visto acontece sozinho, quando o servidor entrega ao filho.

O pai perguntou se o filho já viu. A resposta honesta é "o app dele recebeu e
mostrou", e o momento em que isso é verdade é o momento em que o servidor
responde a leitura dele. Então é aqui que se carimba, e não numa chamada extra
que alguma tela nova possa esquecer de fazer.

**Isto é uma escrita dentro de um GET**, o que normalmente é errado. Vale aqui
por três motivos concretos e vale revisar se algum deixar de valer:

1. Não existe cache nem prefetch entre o app e este servidor - a leitura só
   acontece porque a tela está aberta na frente da criança.
2. A escrita é idempotente e só a primeira vale, então repetir a requisição não
   muda resultado nenhum.
3. A alternativa, o app avisar depois de mostrar, dá o mesmo resultado mas
   depende de cada tela nova lembrar de avisar - e a que esquecer vira uma
   mentira silenciosa para o pai.
"""

from datetime import UTC, datetime

from sqlalchemy.orm import Session

from app.models import Role, User


def marcar_visto(db: Session, itens: list, current_user: User) -> None:
    """Carimba a hora nos itens do próprio filho que ainda não tinham sido
    vistos. Não faz nada quando quem lê é o pai - quem "vê" é a criança."""
    if current_user.role is Role.ADMIN:
        return

    agora = datetime.now(UTC)
    mudou = False
    for item in itens:
        if item.seen_at is None and item.child_id == current_user.id:
            item.seen_at = agora
            mudou = True

    if mudou:
        db.commit()


def marcar_visto_pai(db: Session, itens: list) -> None:
    """A direção contrária de `marcar_visto`: aqui é o pai vendo o que o
    filho escreveu (um pedido novo). Não recebe `current_user` nem checa
    papel - só é chamada de rota já protegida por `AdminUser`, então quem
    está lendo já está garantido pelo tipo do parâmetro na rota.

    Campo próprio (`seen_by_parent_at`), não o `seen_at` de trombadice/
    castigo: "o pai viu que chegou um pedido" e "o filho viu a decisão" são
    fatos diferentes, cada um com seu carimbo - reaproveitar um `seen_at` só
    conflaria os dois."""
    agora = datetime.now(UTC)
    mudou = False
    for item in itens:
        if item.seen_by_parent_at is None:
            item.seen_by_parent_at = agora
            mudou = True

    if mudou:
        db.commit()


def marcar_decisao_vista(db: Session, itens: list, current_user: User) -> None:
    """O filho vendo que saiu uma decisão - simétrico a `marcar_visto`, mas
    só carimba pedido já decidido (pendente não tem decisão pra ver)."""
    if current_user.role is Role.ADMIN:
        return

    agora = datetime.now(UTC)
    mudou = False
    for item in itens:
        decidido = item.decided_at is not None
        if decidido and item.seen_by_child_at is None and item.child_id == current_user.id:
            item.seen_by_child_at = agora
            mudou = True

    if mudou:
        db.commit()


def marcar_assunto_visto(db: Session, itens: list, current_user: User) -> None:
    """Assunto tem uma regra própria: **quem vê é sempre quem não escreveu.**

    As outras duas funções aqui têm uma direção fixa, porque o que elas carimbam
    também tem (o pai cadastra, o filho vê; o filho pede, o pai vê). Assunto é a
    única coisa do app que os dois lados criam, então a direção sai do autor de
    cada linha, não da função. O pai relendo o assunto que ele mesmo escreveu não
    carimba nada - senão "o filho ainda não viu" viraria mentira sem ninguém ter
    aberto o app."""
    agora = datetime.now(UTC)
    mudou = False

    for item in itens:
        if current_user.role is Role.ADMIN:
            if item.by_child and item.seen_by_parent_at is None:
                item.seen_by_parent_at = agora
                mudou = True
        elif (
            not item.by_child
            and item.child_id == current_user.id
            and item.seen_by_child_at is None
        ):
            item.seen_by_child_at = agora
            mudou = True

    if mudou:
        db.commit()
