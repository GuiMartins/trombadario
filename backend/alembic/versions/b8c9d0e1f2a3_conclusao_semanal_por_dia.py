"""conclusão de tarefa semanal passa a ser por dia, não por semana

Revision ID: b8c9d0e1f2a3
Revises: a7b8c9d0e1f2
Create Date: 2026-08-05

Nada muda de schema aqui - o que muda é o **conteúdo** do `period_key` das
conclusões de tarefa semanal, e é por isso que precisa de migration.

Antes, `chave_do_periodo` devolvia a segunda-feira da semana pra tarefa
semanal. Uma tarefa de "segunda e quinta" tem duas ocorrências na mesma
semana, mas as duas caíam na mesma chave: marcar na segunda travava a quinta
com 409 "já marcado nesse período". Agora a chave é o dia, como na diária, e
quem impede marcar em dia solto é o dia agendado, conferido na rota.

As linhas antigas guardam a segunda-feira da semana. Deixadas como estão,
travariam por engano a segunda-feira seguinte (mesma string, agora com outro
significado). O dia de verdade está em `completed_at`, então dá pra reescrever
sem chutar nada.

> `tasks.periodicity` é `Enum(..., native_enum=False)`: o banco guarda o
> **nome** do membro, `'WEEKLY'`, não o valor `'weekly'`. Filtrar por 'weekly'
> aqui não daria erro nenhum - simplesmente não acharia linha alguma e a
> migration passaria "com sucesso" sem consertar nada. Ver
> tests/test_migrations.py.
"""

from datetime import UTC, datetime

import sqlalchemy as sa

from alembic import op

revision = "b8c9d0e1f2a3"
down_revision = "a7b8c9d0e1f2"
branch_labels = None
depends_on = None


def _dia_local(valor: object) -> str:
    """O dia em que a pessoa marcou, do ponto de vista de quem estava lá.

    `completed_at` é gravado em UTC (app/types.py) e o SQLite devolve texto
    cru, sem fuso nenhum - daí a conversão explícita, com o fuso do servidor
    (TZ no compose), que é o mesmo da casa.
    """
    if isinstance(valor, datetime):
        instante = valor
    else:
        instante = datetime.fromisoformat(str(valor).replace(" ", "T"))
    if instante.tzinfo is None:
        instante = instante.replace(tzinfo=UTC)
    return instante.astimezone().date().isoformat()


def upgrade() -> None:
    conexao = op.get_bind()
    linhas = conexao.execute(
        sa.text(
            "select c.id, c.completed_at from task_completions c"
            " join tasks t on t.id = c.task_id where t.periodicity = 'WEEKLY'"
        )
    ).fetchall()

    for completion_id, completed_at in linhas:
        conexao.execute(
            sa.text("update task_completions set period_key = :chave where id = :id"),
            {"chave": _dia_local(completed_at), "id": completion_id},
        )


def downgrade() -> None:
    """Volta a chave pra segunda-feira da semana da conclusão.

    Não é perda de informação (o dia continua em `completed_at`), mas duas
    conclusões da mesma semana colidiriam na UniqueConstraint - por isso a
    primeira de cada semana fica e as outras são apagadas, que é exatamente o
    estado que o esquema antigo conseguia representar.
    """
    conexao = op.get_bind()
    linhas = conexao.execute(
        sa.text(
            "select c.id, c.task_id, c.completed_at from task_completions c"
            " join tasks t on t.id = c.task_id where t.periodicity = 'WEEKLY'"
            " order by c.completed_at"
        )
    ).fetchall()

    vistas: set[tuple[int, str]] = set()
    for completion_id, task_id, completed_at in linhas:
        dia = datetime.fromisoformat(_dia_local(completed_at)).date()
        segunda = dia.fromordinal(dia.toordinal() - dia.weekday()).isoformat()
        if (task_id, segunda) in vistas:
            conexao.execute(
                sa.text("delete from task_completions where id = :id"), {"id": completion_id}
            )
            continue
        vistas.add((task_id, segunda))
        conexao.execute(
            sa.text("update task_completions set period_key = :chave where id = :id"),
            {"chave": segunda, "id": completion_id},
        )
