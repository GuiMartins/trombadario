from datetime import datetime

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator

from app.models import Periodicity, Role


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


class SetupRequest(BaseModel):
    """First-run wizard: creates the one account that owns the app."""

    username: str = Field(min_length=3, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    display_name: str = Field(min_length=1, max_length=120)


class TrombadiceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str
    occurred_at: datetime
    child_id: int
    author_id: int
    task_id: int | None
    created_at: datetime
    updated_at: datetime


class TrombadiceCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = ""
    # AwareDatetime, not datetime: a naive value would be ambiguous and the
    # storage layer rejects it anyway (see app/types.py). Better a 422 than a
    # 500, and better an explicit offset than a silent 3-hour shift.
    occurred_at: AwareDatetime
    child_id: int
    task_id: int | None = None


class TrombadiceUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    occurred_at: AwareDatetime | None = None
    task_id: int | None = None


class TaskOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    description: str
    periodicity: Periodicity
    weekdays: list[int]
    day_of_month: int | None
    child_id: int
    author_id: int
    is_active: bool
    created_at: datetime

    @field_validator("weekdays", mode="before")
    @classmethod
    def split_weekdays(cls, value: object) -> object:
        # Stored as "0,2,4" (see models.Task); the API speaks a real list.
        if isinstance(value, str):
            return [int(day) for day in value.split(",") if day.strip()]
        return value


class TaskCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    description: str = ""
    periodicity: Periodicity = Periodicity.DAILY
    weekdays: list[int] = Field(default_factory=list)
    day_of_month: int | None = Field(default=None, ge=1, le=31)
    child_id: int

    @field_validator("weekdays")
    @classmethod
    def check_weekdays(cls, value: list[int]) -> list[int]:
        if any(day < 0 or day > 6 for day in value):
            raise ValueError("dia da semana precisa estar entre 0 (segunda) e 6 (domingo)")
        return sorted(set(value))


class TaskUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    periodicity: Periodicity | None = None
    weekdays: list[int] | None = None
    day_of_month: int | None = Field(default=None, ge=1, le=31)
    is_active: bool | None = None


class PunishmentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    reason: str
    starts_at: datetime
    ends_at: datetime
    ended_early_at: datetime | None
    child_id: int
    author_id: int
    created_at: datetime
    trombadice_ids: list[int] = Field(default_factory=list)
    is_active: bool = False


class PunishmentCreate(BaseModel):
    reason: str = ""
    starts_at: AwareDatetime | None = None
    ends_at: AwareDatetime
    child_id: int
    trombadice_ids: list[int] = Field(default_factory=list)


class PunishmentUpdate(BaseModel):
    reason: str | None = None
    ends_at: AwareDatetime | None = None
    trombadice_ids: list[int] | None = None
    # True ends it now; the original ends_at is kept so the history shows what
    # was handed down as well as what was actually served.
    end_now: bool | None = None


class SplashMessageRandom(BaseModel):
    """Resposta do sorteio. `text` nulo quando o pai não cadastrou nenhuma frase
    que se aplique - o app pula a tela e vai direto pro conteúdo."""

    text: str | None = None


class Health(BaseModel):
    app: str
    server_id: str
    setup_required: bool


class SplashMessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    text: str
    child_id: int | None
    is_active: bool
    created_at: datetime


class SplashMessageCreate(BaseModel):
    text: str = Field(min_length=1, max_length=300)
    # None = vale pra todos os filhos.
    child_id: int | None = None


class SplashMessageUpdate(BaseModel):
    text: str | None = Field(default=None, min_length=1, max_length=300)
    child_id: int | None = None
    is_active: bool | None = None
