"""A lista de tipos de trombadice, mantida pelo pai.

Ler é dos dois papéis: o filho precisa dos nomes para os chips de filtro do
feed dele. Escrever é só do pai, como tudo o que é cadastro - e ele vê também
os desativados, que para o filho não existem: tipo pausado não é opção de
filtro, é lixo na tela.
"""

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import func, select

from app.deps import AdminUser, CurrentUser, DbSession
from app.models import Role, Trombadice, TrombadiceCategory
from app.schemas import (
    TrombadiceCategoryCreate,
    TrombadiceCategoryOut,
    TrombadiceCategoryUpdate,
)

router = APIRouter(prefix="/api/trombadice-categories", tags=["trombadice-categories"])

NOT_FOUND = HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Tipo não encontrado")

# Empate de posição desempata pelo nome, senão a lista dança a cada leitura.
ORDEM = (TrombadiceCategory.position, TrombadiceCategory.name)


def _em_uso(db: DbSession) -> dict[int, int]:
    """Quantas anotações apontam para cada tipo. Uma consulta agrupada e não uma
    por linha: a lista é do tamanho da paciência do pai, mas o N+1 seria de
    graça e não é."""
    linhas = db.execute(
        select(Trombadice.category_id, func.count(Trombadice.id))
        .where(Trombadice.category_id.is_not(None))
        .group_by(Trombadice.category_id)
    )
    return dict(linhas.all())


def _saida(categoria: TrombadiceCategory, usos: int) -> TrombadiceCategoryOut:
    return TrombadiceCategoryOut.model_validate(categoria).model_copy(update={"em_uso": usos})


def _get_or_404(db: DbSession, category_id: int) -> TrombadiceCategory:
    categoria = db.get(TrombadiceCategory, category_id)
    if categoria is None:
        raise NOT_FOUND
    return categoria


def _nome_livre(db: DbSession, nome: str, ignorando: int | None = None) -> None:
    query = select(TrombadiceCategory).where(func.lower(TrombadiceCategory.name) == nome.lower())
    if ignorando is not None:
        query = query.where(TrombadiceCategory.id != ignorando)
    if db.scalar(query) is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Já existe um tipo com esse nome",
        )


@router.get("", response_model=list[TrombadiceCategoryOut])
def list_categories(current_user: CurrentUser, db: DbSession) -> list[TrombadiceCategoryOut]:
    query = select(TrombadiceCategory).order_by(*ORDEM)
    if current_user.role is not Role.ADMIN:
        query = query.where(TrombadiceCategory.is_active)
    usos = _em_uso(db)
    return [_saida(c, usos.get(c.id, 0)) for c in db.scalars(query)]


@router.post("", response_model=TrombadiceCategoryOut, status_code=status.HTTP_201_CREATED)
def create_category(
    payload: TrombadiceCategoryCreate, admin: AdminUser, db: DbSession
) -> TrombadiceCategoryOut:
    nome = payload.name.strip()
    _nome_livre(db, nome)
    posicao = payload.position
    if posicao is None:
        # Sem posição pedida, entra no fim: quem cadastra o décimo tipo não quer
        # ter que saber que ele é o décimo.
        ultima = db.scalar(select(func.max(TrombadiceCategory.position)))
        posicao = 0 if ultima is None else ultima + 1
    categoria = TrombadiceCategory(name=nome, position=posicao)
    db.add(categoria)
    db.commit()
    db.refresh(categoria)
    return _saida(categoria, 0)


@router.patch("/{category_id}", response_model=TrombadiceCategoryOut)
def update_category(
    category_id: int, payload: TrombadiceCategoryUpdate, admin: AdminUser, db: DbSession
) -> TrombadiceCategoryOut:
    categoria = _get_or_404(db, category_id)
    data = payload.model_dump(exclude_unset=True)
    if (nome := data.get("name")) is not None:
        data["name"] = nome.strip()
        _nome_livre(db, data["name"], ignorando=category_id)
    for campo, valor in data.items():
        setattr(categoria, campo, valor)
    db.commit()
    db.refresh(categoria)
    return _saida(categoria, _em_uso(db).get(categoria.id, 0))


@router.delete("/{category_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_category(category_id: int, admin: AdminUser, db: DbSession) -> None:
    """Só apaga o que nunca foi usado.

    Com anotação apontando para ele, apagar deixaria registro sem dizer o que
    aconteceu - e a FK é RESTRICT, então o banco recusaria de qualquer jeito.
    Melhor um 409 explicando que existe `is_active` do que um 500."""
    categoria = _get_or_404(db, category_id)
    if _em_uso(db).get(category_id):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Esse tipo está em uso. Desative em vez de apagar.",
        )
    db.delete(categoria)
    db.commit()
