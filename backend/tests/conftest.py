import os
from collections.abc import Iterator

import pytest

# Must be set before app.config is imported anywhere, since Settings is cached.
# An explicit SECRET_KEY also keeps the tests off the database-generated key,
# which would otherwise be regenerated per in-memory database.
os.environ.setdefault("SECRET_KEY", "test-secret")
os.environ.setdefault("DATABASE_URL", "sqlite://")

from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import Session, sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.database import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Role, User  # noqa: E402
from app.security import hash_password  # noqa: E402

ADMIN_PASSWORD = "senha-do-pai"
CHILD_PASSWORD = "senha-do-filho"


@pytest.fixture
def db() -> Iterator[Session]:
    # In-memory SQLite with a single shared connection: without StaticPool each
    # session would get its own empty database.
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    session = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)()
    try:
        yield session
    finally:
        session.close()
        engine.dispose()


@pytest.fixture
def client(db: Session) -> Iterator[TestClient]:
    app.dependency_overrides[get_db] = lambda: db
    # Not used as a context manager on purpose - that would run lifespan, which
    # applies Alembic migrations against the real DATABASE_URL. Here the schema
    # comes from Base.metadata.create_all in the `db` fixture.
    yield TestClient(app)
    app.dependency_overrides.clear()


def make_user(db: Session, username: str, password: str, role: Role, name: str) -> User:
    user = User(
        username=username,
        password_hash=hash_password(password),
        display_name=name,
        role=role,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@pytest.fixture
def admin(db: Session) -> User:
    return make_user(db, "pai", ADMIN_PASSWORD, Role.ADMIN, "Pai")


@pytest.fixture
def child(db: Session) -> User:
    return make_user(db, "filho", CHILD_PASSWORD, Role.CHILD, "Joao")


@pytest.fixture
def other_child(db: Session) -> User:
    return make_user(db, "filha", "senha-da-filha", Role.CHILD, "Maria")


def auth_header(client: TestClient, username: str, password: str) -> dict[str, str]:
    response = client.post("/api/auth/login", data={"username": username, "password": password})
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def as_admin(client: TestClient) -> dict[str, str]:
    return auth_header(client, "pai", ADMIN_PASSWORD)


def as_child(client: TestClient) -> dict[str, str]:
    return auth_header(client, "filho", CHILD_PASSWORD)
