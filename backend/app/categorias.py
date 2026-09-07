"""Os tipos de trombadice que a instalação já nasce sabendo.

São os mesmos oito valores do enum antigo, com os mesmos nomes de tela: quem
instalar hoje começa com a lista que o app tinha, e daí muda o que quiser -
renomeia, desativa, apaga o que nunca usou, acrescenta o que a casa repete.

Semeado **uma vez**, na criação da conta do pai, e não a cada subida do
servidor: religar a lista inteira toda vez que o container reinicia faria
reaparecer o que o pai apagou de propósito. Quem já tinha o app instalado
recebe as mesmas linhas pela migration, com os registros antigos já apontando
para elas.
"""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import TrombadiceCategory

TIPOS_INICIAIS: tuple[str, ...] = (
    "Falta de respeito",
    "Falta de educação",
    "Não fez o que devia",
    "Mentira",
    "Birra / descontrole",
    "Escola",
    "Agressão",
    "Outra",
)


def semear_tipos_de_trombadice(db: Session) -> None:
    """Cria a lista inicial, se não houver nenhuma linha ainda."""
    if db.scalar(select(TrombadiceCategory.id).limit(1)) is not None:
        return
    db.add_all(
        TrombadiceCategory(name=nome, position=posicao)
        for posicao, nome in enumerate(TIPOS_INICIAIS)
    )
    db.commit()
