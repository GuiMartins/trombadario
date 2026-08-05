"""As migrations rodam dentro do lifespan e não podem calar o uvicorn.

`alembic/env.py` chama `logging.config.fileConfig`, que por padrão vem com
`disable_existing_loggers=True` - e isso desliga todo logger já criado, incluindo
os que o uvicorn monta ao subir. Como `main.run_migrations()` roda a cada
inicialização do app, o efeito em produção era o servidor responder normalmente
e não registrar requisição nenhuma: sem `uvicorn.access`, o `docker logs` parava
na última linha do Alembic e nunca mais dizia nada.

Só apareceu porque foi preciso olhar o log pra saber se o celular estava
chamando a API - até então ninguém tinha sentido falta.
"""

import logging

from app.main import run_migrations

# Os que o uvicorn cria ao subir e que o `fileConfig` desligava de tabela.
LOGGERS_DO_UVICORN = ("uvicorn", "uvicorn.access", "uvicorn.error")


def test_migrations_nao_desligam_os_loggers_do_uvicorn(tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("DATABASE_URL", f"sqlite:///{tmp_path / 'log.db'}")
    from app.config import get_settings

    get_settings.cache_clear()

    # Estado de quem já subiu: o uvicorn cria os loggers antes do lifespan.
    for nome in LOGGERS_DO_UVICORN:
        logging.getLogger(nome).disabled = False

    run_migrations()

    for nome in LOGGERS_DO_UVICORN:
        assert not logging.getLogger(nome).disabled, (
            f"{nome} foi desligado pelas migrations - o servidor sobe sem log de acesso"
        )

    get_settings.cache_clear()
