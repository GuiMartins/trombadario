from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select

from app.deps import CurrentUser, DbSession
from app.models import User
from app.schemas import Token, UserOut
from app.security import create_access_token, verify_password

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/login", response_model=Token)
def login(
    form_data: Annotated[OAuth2PasswordRequestForm, Depends()],
    db: DbSession,
) -> Token:
    user = db.scalar(select(User).where(User.username == form_data.username))
    # Same message for unknown user, wrong password and disabled account: no
    # point telling whoever is guessing which half they got right.
    if user is None or not user.is_active or not verify_password(form_data.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuário ou senha inválidos",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return Token(access_token=create_access_token(user.username, user.role.value))


@router.get("/me", response_model=UserOut)
def me(current_user: CurrentUser) -> User:
    return current_user
