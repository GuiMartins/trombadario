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
    fileConfig(config.config_file_name)

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
