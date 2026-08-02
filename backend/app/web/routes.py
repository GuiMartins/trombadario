from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy import select

from app.deps import DbSession
from app.models import Periodicity, Punishment, Role, SplashMessage, Task, Trombadice, User
from app.security import create_access_token, hash_password, verify_password
from app.setup_state import setup_required
from app.web.deps import SESSION_COOKIE, AdminWeb, MaybeUser, RedirectTo

BASE_DIR = Path(__file__).resolve().parent
templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))

router = APIRouter(tags=["web"])

WEEKDAY_NAMES = ["Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"]


def _redirect(url: str) -> RedirectResponse:
    # 303 so the browser switches to GET - without it a refresh would resubmit
    # the form.
    return RedirectResponse(url, status_code=303)


def _children(db: DbSession) -> list[User]:
    return list(db.scalars(select(User).where(User.role == Role.CHILD).order_by(User.display_name)))


def _local_input(moment: datetime) -> str:
    """Formats for <input type="datetime-local">, which has no timezone."""
    return moment.astimezone().strftime("%Y-%m-%dT%H:%M")


def _parse_local(value: str) -> datetime:
    """<input type="datetime-local"> submits wall-clock with no offset; the
    server attaches its own zone, which is the same house."""
    return datetime.fromisoformat(value).astimezone()


def _render(request: Request, template: str, user: User | None = None, **context) -> HTMLResponse:
    return templates.TemplateResponse(
        request=request, name=template, context={"user": user, **context}
    )


# --------------------------------------------------------------------------
# Primeiro acesso
# --------------------------------------------------------------------------


@router.get("/setup", response_class=HTMLResponse)
def setup_form(request: Request, db: DbSession):
    if not setup_required(db):
        return _redirect("/login")
    return _render(request, "setup.html")


@router.post("/setup")
def setup_submit(
    request: Request,
    db: DbSession,
    username: Annotated[str, Form()],
    display_name: Annotated[str, Form()],
    password: Annotated[str, Form()],
    password_confirm: Annotated[str, Form()],
):
    if not setup_required(db):
        return _redirect("/login")

    error = None
    if len(username.strip()) < 3:
        error = "O usuário precisa de pelo menos 3 caracteres"
    elif len(password) < 6:
        error = "A senha precisa de pelo menos 6 caracteres"
    elif password != password_confirm:
        error = "As senhas não conferem"

    if error:
        return _render(request, "setup.html", error=error, username=username,
                       display_name=display_name)

    db.add(
        User(
            username=username.strip(),
            password_hash=hash_password(password),
            display_name=display_name.strip() or username.strip(),
            role=Role.ADMIN,
        )
    )
    db.commit()
    return _redirect("/login")


# --------------------------------------------------------------------------
# Sessão
# --------------------------------------------------------------------------


@router.get("/login", response_class=HTMLResponse)
def login_form(request: Request, db: DbSession, user: MaybeUser):
    if setup_required(db):
        return _redirect("/setup")
    if user is not None and user.role is Role.ADMIN:
        return _redirect("/")
    return _render(request, "login.html")


@router.post("/login")
def login_submit(
    request: Request,
    db: DbSession,
    username: Annotated[str, Form()],
    password: Annotated[str, Form()],
):
    user = db.scalar(select(User).where(User.username == username))
    if user is None or not user.is_active or not verify_password(password, user.password_hash):
        return _render(request, "login.html", error="Usuário ou senha inválidos")

    if user.role is not Role.ADMIN:
        return _render(request, "login.html", child_blocked=True)

    response = _redirect("/")
    response.set_cookie(
        SESSION_COOKIE,
        create_access_token(user.username, user.role.value),
        httponly=True,
        samesite="strict",
        # HTTP on the home LAN, so Secure would make the cookie undeliverable.
        secure=False,
        max_age=60 * 60 * 24 * 30,
    )
    return response


@router.post("/logout")
def logout():
    response = _redirect("/login")
    response.delete_cookie(SESSION_COOKIE)
    return response


# --------------------------------------------------------------------------
# Painel
# --------------------------------------------------------------------------


@router.get("/", response_class=HTMLResponse)
def dashboard(request: Request, db: DbSession, user: AdminWeb):
    now = datetime.now(UTC)
    punishments = list(db.scalars(select(Punishment).order_by(Punishment.starts_at.desc())))
    recentes = list(
        db.scalars(select(Trombadice).order_by(Trombadice.occurred_at.desc()).limit(10))
    )
    return _render(
        request,
        "index.html",
        user=user,
        ativos=[p for p in punishments if p.is_active_at(now)],
        recentes=recentes,
        children={c.id: c for c in _children(db)},
        total_tarefas=len(list(db.scalars(select(Task).where(Task.is_active)))),
    )


# --------------------------------------------------------------------------
# Trombadices
# --------------------------------------------------------------------------


@router.get("/trombadices", response_class=HTMLResponse)
def trombadices_page(request: Request, db: DbSession, user: AdminWeb, child_id: int | None = None):
    query = select(Trombadice).order_by(Trombadice.occurred_at.desc(), Trombadice.id.desc())
    if child_id:
        query = query.where(Trombadice.child_id == child_id)
    children = _children(db)
    return _render(
        request,
        "trombadices.html",
        user=user,
        trombadices=list(db.scalars(query)),
        children=children,
        children_by_id={c.id: c for c in children},
        tasks=list(db.scalars(select(Task).where(Task.is_active).order_by(Task.name))),
        selected_child=child_id,
        agora=_local_input(datetime.now(UTC)),
    )


@router.post("/trombadices")
def trombadice_create(
    db: DbSession,
    user: AdminWeb,
    title: Annotated[str, Form()],
    child_id: Annotated[int, Form()],
    occurred_at: Annotated[str, Form()],
    description: Annotated[str, Form()] = "",
    task_id: Annotated[str, Form()] = "",
):
    task = int(task_id) if task_id else None
    if task is not None:
        found = db.get(Task, task)
        if found is None or found.child_id != child_id:
            task = None

    db.add(
        Trombadice(
            title=title.strip(),
            description=description.strip(),
            occurred_at=_parse_local(occurred_at),
            child_id=child_id,
            task_id=task,
            author_id=user.id,
        )
    )
    db.commit()
    return _redirect("/trombadices")


@router.post("/trombadices/{trombadice_id}/delete")
def trombadice_delete(trombadice_id: int, db: DbSession, user: AdminWeb):
    if (trombadice := db.get(Trombadice, trombadice_id)) is not None:
        db.delete(trombadice)
        db.commit()
    return _redirect("/trombadices")


# --------------------------------------------------------------------------
# Tarefas
# --------------------------------------------------------------------------


@router.get("/tarefas", response_class=HTMLResponse)
def tasks_page(request: Request, db: DbSession, user: AdminWeb, child_id: int | None = None):
    query = select(Task).order_by(Task.is_active.desc(), Task.name)
    if child_id:
        query = query.where(Task.child_id == child_id)
    children = _children(db)
    return _render(
        request,
        "tarefas.html",
        user=user,
        tasks=list(db.scalars(query)),
        children=children,
        children_by_id={c.id: c for c in children},
        selected_child=child_id,
        weekday_names=WEEKDAY_NAMES,
    )


@router.post("/tarefas")
def task_create(
    db: DbSession,
    user: AdminWeb,
    name: Annotated[str, Form()],
    child_id: Annotated[int, Form()],
    periodicity: Annotated[str, Form()],
    description: Annotated[str, Form()] = "",
    weekdays: Annotated[list[int] | None, Form()] = None,
    day_of_month: Annotated[str, Form()] = "",
):
    period = Periodicity(periodicity)
    dias = sorted(set(weekdays or [])) if period is Periodicity.WEEKLY else []
    dia_mes = int(day_of_month) if period is Periodicity.MONTHLY and day_of_month else None

    db.add(
        Task(
            name=name.strip(),
            description=description.strip(),
            periodicity=period,
            weekdays=",".join(str(d) for d in dias),
            day_of_month=dia_mes,
            child_id=child_id,
            author_id=user.id,
        )
    )
    db.commit()
    return _redirect("/tarefas")


@router.post("/tarefas/{task_id}/toggle")
def task_toggle(task_id: int, db: DbSession, user: AdminWeb):
    if (task := db.get(Task, task_id)) is not None:
        task.is_active = not task.is_active
        db.commit()
    return _redirect("/tarefas")


@router.post("/tarefas/{task_id}/delete")
def task_delete(task_id: int, db: DbSession, user: AdminWeb):
    if (task := db.get(Task, task_id)) is not None:
        db.delete(task)
        db.commit()
    return _redirect("/tarefas")


# --------------------------------------------------------------------------
# Castigos
# --------------------------------------------------------------------------


@router.get("/castigos", response_class=HTMLResponse)
def punishments_page(request: Request, db: DbSession, user: AdminWeb):
    now = datetime.now(UTC)
    children = _children(db)
    punishments = list(db.scalars(select(Punishment).order_by(Punishment.starts_at.desc())))
    return _render(
        request,
        "castigos.html",
        user=user,
        punishments=[(p, p.is_active_at(now)) for p in punishments],
        children=children,
        children_by_id={c.id: c for c in children},
        trombadices=list(
            db.scalars(select(Trombadice).order_by(Trombadice.occurred_at.desc()).limit(50))
        ),
        fim_sugerido=_local_input(datetime.now(UTC) + timedelta(days=1)),
    )


@router.post("/castigos")
def punishment_create(
    db: DbSession,
    user: AdminWeb,
    child_id: Annotated[int, Form()],
    ends_at: Annotated[str, Form()],
    reason: Annotated[str, Form()] = "",
    trombadice_ids: Annotated[list[int] | None, Form()] = None,
):
    punishment = Punishment(
        reason=reason.strip(),
        starts_at=datetime.now(UTC),
        ends_at=_parse_local(ends_at),
        child_id=child_id,
        author_id=user.id,
    )
    if trombadice_ids:
        chosen = list(db.scalars(select(Trombadice).where(Trombadice.id.in_(trombadice_ids))))
        # Only this child's own trombadices - anything else would put a
        # sibling's name on the record.
        punishment.trombadices = [t for t in chosen if t.child_id == child_id]
    db.add(punishment)
    db.commit()
    return _redirect("/castigos")


@router.post("/castigos/{punishment_id}/encerrar")
def punishment_end(punishment_id: int, db: DbSession, user: AdminWeb):
    if (punishment := db.get(Punishment, punishment_id)) is not None:
        punishment.ended_early_at = datetime.now(UTC)
        db.commit()
    return _redirect("/castigos")


@router.post("/castigos/{punishment_id}/delete")
def punishment_delete(punishment_id: int, db: DbSession, user: AdminWeb):
    if (punishment := db.get(Punishment, punishment_id)) is not None:
        db.delete(punishment)
        db.commit()
    return _redirect("/castigos")


# --------------------------------------------------------------------------
# Frases de abertura
# --------------------------------------------------------------------------


@router.get("/frases", response_class=HTMLResponse)
def splash_page(request: Request, db: DbSession, user: AdminWeb):
    children = _children(db)
    return _render(
        request,
        "frases.html",
        user=user,
        messages=list(db.scalars(select(SplashMessage).order_by(SplashMessage.id.desc()))),
        children=children,
        children_by_id={c.id: c for c in children},
    )


@router.post("/frases")
def splash_create(
    db: DbSession,
    user: AdminWeb,
    text: Annotated[str, Form()],
    child_id: Annotated[str, Form()] = "",
):
    # Vazio = pra todos os filhos.
    alvo = int(child_id) if child_id else None
    if alvo is not None:
        child = db.get(User, alvo)
        if child is None or child.role is not Role.CHILD:
            alvo = None

    db.add(SplashMessage(text=text.strip(), child_id=alvo, author_id=user.id))
    db.commit()
    return _redirect("/frases")


@router.post("/frases/{message_id}/toggle")
def splash_toggle(message_id: int, db: DbSession, user: AdminWeb):
    if (message := db.get(SplashMessage, message_id)) is not None:
        message.is_active = not message.is_active
        db.commit()
    return _redirect("/frases")


@router.post("/frases/{message_id}/delete")
def splash_delete(message_id: int, db: DbSession, user: AdminWeb):
    if (message := db.get(SplashMessage, message_id)) is not None:
        db.delete(message)
        db.commit()
    return _redirect("/frases")


# --------------------------------------------------------------------------
# Contas
# --------------------------------------------------------------------------


@router.get("/contas", response_class=HTMLResponse)
def users_page(request: Request, db: DbSession, user: AdminWeb):
    return _render(
        request,
        "contas.html",
        user=user,
        users=list(db.scalars(select(User).order_by(User.role, User.display_name))),
    )


@router.post("/contas")
def user_create(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    username: Annotated[str, Form()],
    display_name: Annotated[str, Form()],
    password: Annotated[str, Form()],
    role: Annotated[str, Form()] = "child",
):
    if db.scalar(select(User).where(User.username == username.strip())) is not None:
        raise RedirectTo("/contas?erro=usuario-existe")

    db.add(
        User(
            username=username.strip(),
            password_hash=hash_password(password),
            display_name=display_name.strip() or username.strip(),
            role=Role(role),
        )
    )
    db.commit()
    return _redirect("/contas")


@router.post("/contas/{user_id}/senha")
def user_password(
    user_id: int,
    db: DbSession,
    user: AdminWeb,
    password: Annotated[str, Form()],
):
    if (target := db.get(User, user_id)) is not None and len(password) >= 6:
        target.password_hash = hash_password(password)
        db.commit()
    return _redirect("/contas")


@router.post("/contas/{user_id}/toggle")
def user_toggle(user_id: int, db: DbSession, user: AdminWeb):
    target = db.get(User, user_id)
    # Never let the panel deactivate the account being used to reach it, nor the
    # last admin - either would lock everyone out with no way back but SQL.
    if target is not None and target.id != user.id:
        target.is_active = not target.is_active
        db.commit()
    return _redirect("/contas")
