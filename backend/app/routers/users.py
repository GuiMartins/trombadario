from fastapi import APIRouter, HTTPException, status
from sqlalchemy import func, select

from app.deps import AdminUser, DbSession
from app.models import Role, Trombadice, User
from app.schemas import UserCreate, UserOut, UserUpdate
from app.security import hash_password

router = APIRouter(prefix="/api/users", tags=["users"])


def _get_or_404(db: DbSession, user_id: int) -> User:
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Usuário não encontrado")
    return user


def _count_active_admins(db: DbSession) -> int:
    return db.scalar(
        select(func.count()).select_from(User).where(User.role == Role.ADMIN, User.is_active)
    )


@router.get("", response_model=list[UserOut])
def list_users(admin: AdminUser, db: DbSession) -> list[User]:
    return list(db.scalars(select(User).order_by(User.id)))


@router.post("", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def create_user(payload: UserCreate, admin: AdminUser, db: DbSession) -> User:
    if db.scalar(select(User).where(User.username == payload.username)) is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Esse usuário já existe")

    user = User(
        username=payload.username,
        password_hash=hash_password(payload.password),
        display_name=payload.display_name,
        role=payload.role,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.patch("/{user_id}", response_model=UserOut)
def update_user(user_id: int, payload: UserUpdate, admin: AdminUser, db: DbSession) -> User:
    user = _get_or_404(db, user_id)
    data = payload.model_dump(exclude_unset=True)

    if password := data.pop("password", None):
        user.password_hash = hash_password(password)

    # Deactivating the last admin would lock everyone out of managing the app,
    # with no way back other than editing the database by hand.
    if data.get("is_active") is False and user.role is Role.ADMIN and _count_active_admins(db) <= 1:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Não dá pra desativar o único admin",
        )

    for field, value in data.items():
        setattr(user, field, value)
    db.commit()
    db.refresh(user)
    return user


@router.delete("/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_user(user_id: int, admin: AdminUser, db: DbSession) -> None:
    user = _get_or_404(db, user_id)

    if user.role is Role.ADMIN and _count_active_admins(db) <= 1:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Não dá pra apagar o único admin",
        )

    # author_id is ondelete=RESTRICT: the history must not disappear because the
    # parent who wrote it was removed. Deactivating is the way out here.
    if db.scalar(
        select(func.count()).select_from(Trombadice).where(Trombadice.author_id == user.id)
    ):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Esse usuário cadastrou trombadices - desative a conta em vez de apagar",
        )

    db.delete(user)
    db.commit()
