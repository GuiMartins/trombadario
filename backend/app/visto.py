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
