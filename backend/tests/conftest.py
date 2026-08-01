import os
from collections.abc import Iterator

import pytest

# Must be set before app.config is imported anywhere, since Settings is cached.
os.environ.setdefault("SECRET_KEY", "test-secret")
os.environ.setdefault("ADMIN_USERNAME", "pai")
os.environ.setdefault("ADMIN_PASSWORD", "senha-do-pai")
os.environ.setdefault("DATABASE_URL", "sqlite://")

from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import Session, sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.database import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Role, User  # noqa: E402
from app.security import hash_password  # noqa: E402
from app.seed import seed_admin  # noqa: E402


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


@pytest.fixture
def admin(db: Session) -> User:
    seed_admin(db)
    return db.query(User).filter(User.username == "pai").one()


@pytest.fixture
def child(db: Session) -> User:
    user = User(
        username="filho",
        password_hash=hash_password("senha-do-filho"),
        display_name="Filho",
        role=Role.CHILD,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def auth_header(client: TestClient, username: str, password: str) -> dict[str, str]:
    response = client.post(
        "/api/auth/login", data={"username": username, "password": password}
    )
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['access_token']}"}
