from datetime import datetime

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field

from app.models import Role


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    display_name: str
    role: Role
    is_active: bool
    created_at: datetime


class UserCreate(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=120)
    role: Role = Role.CHILD


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=120)
    password: str | None = Field(default=None, min_length=6, max_length=128)
    is_active: bool | None = None


class EventOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str
    occurred_at: datetime
    child_id: int
    author_id: int
    created_at: datetime
    updated_at: datetime


class EventCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = ""
    # AwareDatetime, not datetime: a naive value would be ambiguous and the
    # storage layer rejects it anyway (see app/types.py). Better a 422 than a
    # 500, and better an explicit offset than a silent 3-hour shift.
    occurred_at: AwareDatetime
    child_id: int


class EventUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    occurred_at: AwareDatetime | None = None


class Health(BaseModel):
    app: str
    server_id: str
