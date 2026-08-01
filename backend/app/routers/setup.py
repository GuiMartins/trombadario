from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import DbSession
from app.models import Role, User
from app.schemas import SetupRequest, UserOut
from app.security import hash_password
from app.setup_state import setup_required

router = APIRouter(prefix="/api/setup", tags=["setup"])


@router.post("", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def run_setup(payload: SetupRequest, db: DbSession) -> User:
    """The only unauthenticated write in the system, and it closes itself the
    instant the first admin exists. Replaces the old .env seed, which only made
    sense while a human was writing the file over SSH - installed from the
    CasaOS store, nobody ever sees a .env."""
    if not setup_required(db):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Este servidor já foi configurado",
        )

    if db.scalar(select(User).where(User.username == payload.username)) is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Esse usuário já existe")

    admin = User(
        username=payload.username,
        password_hash=hash_password(payload.password),
        display_name=payload.display_name,
        role=Role.ADMIN,
    )
    db.add(admin)
    db.commit()
    db.refresh(admin)
    return admin
