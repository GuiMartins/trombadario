from fastapi import APIRouter

from app.deps import CurrentUser
from app.periodo import e_aniversario, hoje_local, idade_em
from app.schemas import BirthdayOut

router = APIRouter(prefix="/api", tags=["birthday"])


@router.get("/birthday", response_model=BirthdayOut)
def birthday(current_user: CurrentUser) -> BirthdayOut:
    """Hoje é aniversário de quem está perguntando?

    O app do filho troca a interface inteira por uma tela de festa quando a
    resposta é sim - no dia dele não existe Trombadário. Quem decide é aqui,
    porque o dia local é conta do servidor (ver `app/periodo.py`): pelo relógio
    do celular, bastaria adiantar a data pra ter festa em qualquer terça-feira.

    Cada um pergunta pelo próprio aniversário (`CurrentUser`, não `ChildUser`) -
    a coluna é de pessoa, não de papel. O que é do filho é a **tomada de tela**,
    e essa decisão é do app: pro pai o Trombadário é ferramenta de trabalho, e
    travar o dele um dia inteiro não faria a festa de ninguém.
    """
    hoje = hoje_local()
    nascimento = current_user.birth_date
    festa = nascimento is not None and e_aniversario(nascimento, hoje)

    return BirthdayOut(
        is_birthday=festa,
        display_name=current_user.display_name,
        birth_date=nascimento,
        age=idade_em(nascimento, hoje) if festa else None,
    )
