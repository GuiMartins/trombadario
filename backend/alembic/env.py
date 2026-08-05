from logging.config import fileConfig

from sqlalchemy import engine_from_config, pool

from alembic import context
from app.config import get_settings
from app.database import Base
from app.models import (  # noqa: F401  (registers the tables)
    Punishment,
    ServerIdentity,
    SplashMessage,
    Task,
    Trombadice,
    User,
)

config = context.config

if config.config_file_name is not None:
    # `disable_existing_loggers=False` não é detalhe: as migrations rodam dentro
    # do lifespan do FastAPI (ver `main.run_migrations`), então o padrão `True`
    # desligaria os loggers que o uvicorn já tinha criado - `uvicorn.access` e
    # `uvicorn.error` - e o servidor passaria a rodar sem log de acesso nenhum
    # depois de subir. Foi o que aconteceu em produção, e cegou o diagnóstico do
    # poller de notificação: não dava pra ver se o celular estava chamando a API.
    fileConfig(config.config_file_name, disable_existing_loggers=False)

# Quem chama pode mandar o banco explicitamente (é o que os testes de migration
# fazem). Sem isso vale o do app, que é o caso de sempre - em produção ninguém
# passa nada e continua sendo o DATABASE_URL.
if not config.get_main_option("sqlalchemy.url", None):
    config.set_main_option("sqlalchemy.url", get_settings().database_url)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        render_as_batch=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            # SQLite cannot ALTER most things in place; batch mode rewrites the
            # table instead, which is what makes non-destructive migrations
            # possible here at all.
            render_as_batch=True,
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
