from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Annotated
from urllib.parse import urlencode

from fastapi import APIRouter, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy import or_, select

from app.deps import DbSession
from app.models import (
    CATEGORIAS_POR_TIPO,
    Assunto,
    Category,
    Kind,
    Pedido,
    Periodicity,
    Punishment,
    RequestKind,
    RequestStatus,
    Role,
    SplashMessage,
    Task,
    TaskCompletion,
    Trombadice,
    User,
    categoria_combina,
    categoria_padrao,
)
from app.periodo import (
    data_local,
    e_aniversario,
    hoje_local,
    intervalo,
    mes_anterior,
    mes_seguinte,
    semanas_do_mes,
)
from app.routers.reports import report
from app.routers.tasks import estado_da_tarefa
from app.security import create_access_token, hash_password, verify_password
from app.setup_state import setup_required
from app.visto import marcar_assunto_visto, marcar_visto_pai
from app.web.deps import SESSION_COOKIE, AdminWeb, MaybeUser, RedirectTo

BASE_DIR = Path(__file__).resolve().parent
templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))

router = APIRouter(tags=["web"])

WEEKDAY_NAMES = ["Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"]

# Como chamar "o período de agora" de cada periodicidade. "Feito neste período"
# está certo e não é como ninguém fala.
ROTULO_DO_PERIODO = {
    Periodicity.DAILY: "hoje",
    Periodicity.WEEKLY: "hoje",
    Periodicity.MONTHLY: "este mês",
    Periodicity.ONCE: "",
}
WEEKDAY_INICIAIS = ["S", "T", "Q", "Q", "S", "S", "D"]
MESES = [
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
]

# Janelas do relatório. Uma semana, um mês e um trimestre cobrem "como foi
# esses dias", "como foi o mês" e "está melhorando?".
JANELAS = {7: "7 dias", 30: "30 dias", 90: "3 meses"}

# Rótulo de cada categoria. Fica aqui e não no enum porque é texto de tela: o
# modelo guarda o valor, a apresentação escolhe como chamar.
CATEGORIA_ROTULOS = {
    Category.DESRESPEITO: "Falta de respeito",
    Category.EDUCACAO: "Falta de educação",
    Category.NAO_FEZ: "Não fez o que devia",
    Category.MENTIRA: "Mentira",
    Category.BIRRA: "Birra / descontrole",
    Category.ESCOLA: "Escola",
    Category.AGRESSAO: "Agressão",
    Category.OUTRA: "Outra",
    Category.AJUDOU: "Ajudou sem pedir",
    Category.RESPONSABILIDADE: "Foi responsável",
    Category.ESTUDOU: "Mandou bem na escola",
    Category.GENTILEZA: "Foi gentil",
    Category.INICIATIVA: "Teve iniciativa",
    Category.SUPEROU: "Superou uma dificuldade",
    Category.CUIDOU: "Cuidou bem das coisas",
    Category.OUTRA_BOA: "Outra coisa boa",
}

TIPO_ROTULOS = {Kind.TROMBADICE: "Trombadice", Kind.CONQUISTA: "Conquista"}


def _rotulos_do_tipo(kind: Kind) -> dict:
    return {c: CATEGORIA_ROTULOS[c] for c in CATEGORIAS_POR_TIPO[kind]}


def _redirect(url: str) -> RedirectResponse:
    # 303 so the browser switches to GET - without it a refresh would resubmit
    # the form.
    return RedirectResponse(url, status_code=303)


def _tarefa_do_filho(db: DbSession, task_id: str, child_id: int) -> int | None:
    """Só aceita a tarefa se ela for daquele filho - atrelar a tarefa de um
    irmão faria o registro afirmar uma coisa falsa. Na dúvida, sem tarefa."""
    if not task_id:
        return None
    task = db.get(Task, int(task_id))
    return task.id if task is not None and task.child_id == child_id else None


def _campos_da_trombadice(
    db: DbSession,
    title: str,
    child_id: int,
    task_id: str,
    categoria_trombadice: str,
    categoria_conquista: str,
    kind: str,
) -> tuple[str, int, int | None, Category, Kind]:
    """Resolve título, filho, tarefa e categoria a partir do que o formulário
    mandou.

    Com tarefa escolhida, a tarefa é que manda em duas coisas: **de quem é**
    (ela pertence a um filho só) e, se ninguém escreveu título, **qual é o
    título** - o que aconteceu foi não ter feito aquilo. Por isso esses dois
    campos somem da tela quando há tarefa: já estão respondidos.

    No caso de edição o título continua vindo preenchido no campo escondido,
    então corrigir um "machou" com tarefa atrelada preserva a correção.
    """
    tipo = Kind(kind) if kind in {k.value for k in Kind} else Kind.TROMBADICE

    # Conquista não se atrela a tarefa: o vínculo existe pra dizer o que **não**
    # foi cumprido, e diria o contrário do que significa.
    tarefa = db.get(Task, int(task_id)) if task_id and tipo is Kind.TROMBADICE else None
    if tarefa is not None:
        child_id = tarefa.child_id

    # O formulário manda as duas listas de categoria, e só a do tipo escolhido
    # é a que vale. Assim nenhuma delas precisa ser desabilitada no navegador -
    # o CSS esconde a outra e o servidor ignora o que ela mandou.
    bruto = categoria_conquista if tipo is Kind.CONQUISTA else categoria_trombadice
    escolhida = Category(bruto) if bruto in {c.value for c in Category} else None
    categoria = (
        escolhida
        if escolhida is not None and categoria_combina(escolhida, tipo)
        else categoria_padrao(tipo)
    )

    titulo = title.strip() or (tarefa.name if tarefa is not None else "")
    return titulo, child_id, (tarefa.id if tarefa is not None else None), categoria, tipo


def _filho_valido(db: DbSession, child_id: str) -> int | None:
    """Vazio, ou qualquer coisa que não seja uma conta de filho de verdade,
    quer dizer "pra todos os filhos"."""
    if not child_id:
        return None
    child = db.get(User, int(child_id))
    return child.id if child is not None and child.role is Role.CHILD else None


def _children(db: DbSession) -> list[User]:
    return list(db.scalars(select(User).where(User.role == Role.CHILD).order_by(User.display_name)))


def _local_input(moment: datetime) -> str:
    """Formats for <input type="datetime-local">, which has no timezone."""
    return moment.astimezone().strftime("%Y-%m-%dT%H:%M")


# Para os formulários de edição preencherem o campo de data sem cada rota ter
# que formatar tudo de novo antes de renderizar.
templates.env.filters["hora_local"] = _local_input


def _parse_local(value: str) -> datetime:
    """<input type="datetime-local"> submits wall-clock with no offset; the
    server attaches its own zone, which is the same house."""
    return datetime.fromisoformat(value).astimezone()


def _parse_dia(value: str) -> date | None:
    """<input type="date"> manda "AAAA-MM-DD", ou vazio quando a pessoa limpou
    o campo - e limpar é a única forma de desfazer uma data cadastrada errada.

    Estoura ValueError no que não for data, em vez de devolver None junto com o
    campo vazio: quem chama decide, e as duas decisões são diferentes - criando
    conta não há data anterior pra preservar, editando há.

    Nada de `astimezone()` aqui, ao contrário do `_parse_local` acima: dia de
    aniversário não tem hora, e colar o fuso do servidor num dia é o jeito de
    ele virar o dia anterior em algum lugar."""
    return date.fromisoformat(value) if value.strip() else None


TEMA_COOKIE = "trombadario_tema"
TEMAS = ("sistema", "claro", "escuro")


def _tema(request: Request) -> str:
    """Nunca devolve o que veio no cookie sem conferir: esse valor vai parar
    dentro de um atributo do <html>."""
    escolhido = request.cookies.get(TEMA_COOKIE, "")
    return escolhido if escolhido in TEMAS else "sistema"


def _render(request: Request, template: str, user: User | None = None, **context) -> HTMLResponse:
    return templates.TemplateResponse(
        request=request,
        name=template,
        context={"user": user, "tema": _tema(request), **context},
    )


@router.post("/tema")
def tema_set(
    request: Request,
    valor: Annotated[str, Form()],
    next: Annotated[str, Form()] = "/",
):
    # Só caminho de dentro de casa. Sem isso, um "next" com http:// ou //outro
    # transformaria o botão de tema em redirecionamento para fora.
    destino = next if next.startswith("/") and not next.startswith("//") else "/"

    response = _redirect(destino)
    if valor == "sistema":
        response.delete_cookie(TEMA_COOKIE)
    elif valor in TEMAS:
        # Um ano: é preferência de aparência, não sessão. Sem HttpOnly de
        # propósito - não protege nada e não custa nada.
        response.set_cookie(TEMA_COOKIE, valor, max_age=31536000, samesite="strict")
    return response


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


def _mes_pedido(mes: str | None, dia_escolhido: date | None) -> date:
    """Qual mês o calendário abre: o pedido, senão o do dia filtrado, senão o
    de hoje."""
    if mes:
        try:
            return date.fromisoformat(f"{mes}-01")
        except ValueError:
            pass
    return (dia_escolhido or hoje_local()).replace(day=1)


@router.get("/trombadices", response_class=HTMLResponse)
def trombadices_page(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    child_id: int | None = None,
    kind: str | None = None,
    category: str | None = None,
    dia: date | None = None,
    q: str | None = None,
    mes: str | None = None,
    editar: int | None = None,
):
    categoria = Category(category) if category in {c.value for c in Category} else None
    tipo = Kind(kind) if kind in {k.value for k in Kind} else None

    base = select(Trombadice)
    if child_id:
        base = base.where(Trombadice.child_id == child_id)
    if tipo is not None:
        base = base.where(Trombadice.kind == tipo)
    if categoria is not None:
        base = base.where(Trombadice.category == categoria)
    if q and (termo := q.strip()):
        alvo = f"%{termo}%"
        base = base.where(or_(Trombadice.title.ilike(alvo), Trombadice.description.ilike(alvo)))

    # O calendário mostra os dias que existem **com os outros filtros já
    # aplicados**: filtrar por "agressão" e ver dias clicáveis sem nenhuma
    # agressão seria mentira.
    dias_com_registro = {data_local(t.occurred_at) for t in db.scalars(base)}

    query = base.order_by(Trombadice.occurred_at.desc(), Trombadice.id.desc())
    if dia is not None:
        inicio, fim = intervalo(dia, dia)
        query = query.where(Trombadice.occurred_at >= inicio, Trombadice.occurred_at < fim)

    children = _children(db)
    mes_aberto = _mes_pedido(mes, dia)
    return _render(
        request,
        "trombadices.html",
        user=user,
        trombadices=list(db.scalars(query)),
        children=children,
        children_by_id={c.id: c for c in children},
        tasks=list(db.scalars(select(Task).where(Task.is_active).order_by(Task.name))),
        selected_child=child_id,
        # Os chips de categoria seguem o tipo filtrado: com "Conquistas" ligado,
        # oferecer "falta de respeito" seria oferecer um filtro que nunca acha
        # nada. Sem tipo escolhido, valem as dezesseis.
        categorias=_rotulos_do_tipo(tipo) if tipo else CATEGORIA_ROTULOS,
        categoria_escolhida=categoria,
        # Cada tipo com a sua lista: "falta de respeito" não descreve coisa boa,
        # e "ajudou sem pedir" não descreve trombadice.
        categorias_trombadice=_rotulos_do_tipo(Kind.TROMBADICE),
        categorias_conquista=_rotulos_do_tipo(Kind.CONQUISTA),
        tipos=TIPO_ROTULOS,
        tipo_escolhido=tipo,
        busca=q or "",
        dia_escolhido=dia,
        agora=_local_input(datetime.now(UTC)),
        # Editar reaproveita o formulário de cima em vez de abrir uma página
        # nova: é o mesmo formulário, com os campos preenchidos.
        editando=db.get(Trombadice, editar) if editar else None,
        url=_construtor_de_url(
            "/trombadices",
            {
                "child_id": child_id,
                "kind": tipo.value if tipo else None,
                "category": categoria.value if categoria else None,
                "q": q,
                "dia": dia,
                "mes": mes,
            },
        ),
        **_calendario(mes_aberto, dias_com_registro),
    )


def _construtor_de_url(base: str, atual: dict):
    """Devolve uma função que remonta o endereço da página trocando um filtro e
    mantendo os outros.

    Existe porque montar isso no template com concatenação vira uma sopa de
    `if` e um `&` esquecido em algum ramo. Aqui é uma linha e o urlencode cuida
    do escape."""

    def url(**mudancas) -> str:
        params = {**atual, **mudancas}
        limpos = {k: str(v) for k, v in params.items() if v not in (None, "")}
        return f"{base}?{urlencode(limpos)}" if limpos else base

    return url


def _calendario(mes_aberto: date, dias_com_registro: set[date]) -> dict:
    return {
        "mes_aberto": mes_aberto,
        "mes_nome": f"{MESES[mes_aberto.month - 1]} de {mes_aberto.year}",
        "mes_anterior": mes_anterior(mes_aberto).strftime("%Y-%m"),
        "mes_seguinte": mes_seguinte(mes_aberto).strftime("%Y-%m"),
        "semanas": semanas_do_mes(mes_aberto),
        "dias_com_registro": dias_com_registro,
        "iniciais": WEEKDAY_INICIAIS,
        "hoje": hoje_local(),
    }


@router.post("/trombadices")
def trombadice_create(
    db: DbSession,
    user: AdminWeb,
    child_id: Annotated[int, Form()],
    occurred_at: Annotated[str, Form()],
    title: Annotated[str, Form()] = "",
    description: Annotated[str, Form()] = "",
    task_id: Annotated[str, Form()] = "",
    kind: Annotated[str, Form()] = "trombadice",
    categoria_trombadice: Annotated[str, Form()] = "outra",
    categoria_conquista: Annotated[str, Form()] = "outra_boa",
):
    titulo, filho, tarefa, categoria, tipo = _campos_da_trombadice(
        db, title, child_id, task_id, categoria_trombadice, categoria_conquista, kind
    )
    if not titulo:
        # Sem tarefa e sem título não sobra registro nenhum: volta sem gravar.
        return _redirect("/trombadices")

    db.add(
        Trombadice(
            title=titulo,
            description=description.strip(),
            kind=tipo,
            category=categoria,
            occurred_at=_parse_local(occurred_at),
            child_id=filho,
            task_id=tarefa,
            author_id=user.id,
        )
    )
    db.commit()
    return _redirect("/trombadices")


@router.post("/trombadices/{trombadice_id}/editar")
def trombadice_edit(
    trombadice_id: int,
    db: DbSession,
    user: AdminWeb,
    child_id: Annotated[int, Form()],
    occurred_at: Annotated[str, Form()],
    title: Annotated[str, Form()] = "",
    description: Annotated[str, Form()] = "",
    task_id: Annotated[str, Form()] = "",
    categoria_trombadice: Annotated[str, Form()] = "outra",
    categoria_conquista: Annotated[str, Form()] = "outra_boa",
):
    """Corrigir o que já foi cadastrado - um "machou" que era "machucou".

    Só o pai chega aqui: `AdminWeb` é a mesma dependência de todo o painel, e o
    painel inteiro é admin-only por decisão de segurança (ver CLAUDE.md)."""
    trombadice = db.get(Trombadice, trombadice_id)
    if trombadice is None:
        return _redirect("/trombadices")

    # O tipo não se edita - trombadice não vira conquista. Vale o que já está
    # gravado, e a categoria tem que ser da lista dele.
    titulo, filho, tarefa, categoria, _ = _campos_da_trombadice(
        db, title, child_id, task_id, categoria_trombadice, categoria_conquista,
        trombadice.kind.value,
    )
    if not titulo:
        return _redirect("/trombadices")

    trombadice.title = titulo
    trombadice.description = description.strip()
    trombadice.category = categoria
    trombadice.occurred_at = _parse_local(occurred_at)
    trombadice.child_id = filho
    trombadice.task_id = tarefa
    # `author_id` fica como está: quem cadastrou continua sendo quem cadastrou.
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
def tasks_page(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    child_id: int | None = None,
    editar: int | None = None,
):
    query = select(Task).order_by(Task.is_active.desc(), Task.name)
    if child_id:
        query = query.where(Task.child_id == child_id)
    children = _children(db)
    editando = db.get(Task, editar) if editar else None
    tarefas = list(db.scalars(query))
    hoje = hoje_local()
    return _render(
        request,
        "tarefas.html",
        user=user,
        tasks=tarefas,
        # Se o período de agora já foi cumprido é conta de calendário, não de
        # template: quem sabe fazer é a mesma função que a API usa, senão o
        # painel e o app responderiam diferente sobre a mesma tarefa.
        estado={t.id: estado_da_tarefa(t, hoje) for t in tarefas},
        rotulo_do_periodo=ROTULO_DO_PERIODO,
        children=children,
        children_by_id={c.id: c for c in children},
        selected_child=child_id,
        weekday_names=WEEKDAY_NAMES,
        editando=editando,
        # A lista de dias vem pronta: comparar dentro do template com o campo
        # "0,2,4" cru dava um `in` que casava "1" com "10,12".
        editando_dias=(
            [int(d) for d in editando.weekdays.split(",") if d] if editando else []
        ),
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


@router.post("/tarefas/{task_id}/editar")
def task_edit(
    task_id: int,
    db: DbSession,
    user: AdminWeb,
    name: Annotated[str, Form()],
    child_id: Annotated[int, Form()],
    periodicity: Annotated[str, Form()],
    description: Annotated[str, Form()] = "",
    weekdays: Annotated[list[int] | None, Form()] = None,
    day_of_month: Annotated[str, Form()] = "",
):
    task = db.get(Task, task_id)
    if task is None:
        return _redirect("/tarefas")

    period = Periodicity(periodicity)
    task.name = name.strip()
    task.description = description.strip()
    task.periodicity = period
    # Zera o campo que a nova periodicidade não usa, senão sobra lixo de antes:
    # uma tarefa "todo dia" guardando "segunda e quarta" de quando era semanal.
    task.weekdays = (
        ",".join(str(d) for d in sorted(set(weekdays or [])))
        if period is Periodicity.WEEKLY
        else ""
    )
    task.day_of_month = (
        int(day_of_month) if period is Periodicity.MONTHLY and day_of_month else None
    )
    task.child_id = child_id
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


@router.post("/tarefas/{task_id}/conclusoes/{completion_id}/apagar")
def task_completion_delete(task_id: int, completion_id: int, db: DbSession, user: AdminWeb):
    """Desmarcar um "feito" - o mesmo que o filho faz pelo app, aqui sem limite
    de período: o pai corrige qualquer registro, como em todo o resto."""
    completion = db.get(TaskCompletion, completion_id)
    if completion is not None and completion.task_id == task_id:
        db.delete(completion)
        db.commit()
    return _redirect("/tarefas")


# --------------------------------------------------------------------------
# Castigos
# --------------------------------------------------------------------------


@router.get("/castigos", response_class=HTMLResponse)
def punishments_page(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    child_id: int | None = None,
    category: str | None = None,
    dia: date | None = None,
    q: str | None = None,
    mes: str | None = None,
    editar: int | None = None,
    erro: str | None = None,
):
    now = datetime.now(UTC)
    categoria = Category(category) if category in {c.value for c in Category} else None

    base = select(Punishment)
    if child_id:
        base = base.where(Punishment.child_id == child_id)
    if categoria is not None:
        # Castigo não tem categoria própria: herda a das trombadices que o
        # causaram. "Filtrar por agressão" quer dizer "castigos que vieram de
        # alguma agressão", que é a pergunta de verdade.
        base = base.where(Punishment.trombadices.any(Trombadice.category == categoria))
    if q and (termo := q.strip()):
        base = base.where(Punishment.reason.ilike(f"%{termo}%"))

    todos = list(db.scalars(base.order_by(Punishment.starts_at.desc())))

    # Todo dia em que houve castigo, não só o de início: senão o calendário não
    # deixaria clicar no meio de uma semana de castigo.
    dias_com_registro: set[date] = set()
    for p in todos:
        fim = min(p.ended_early_at, p.ends_at) if p.ended_early_at else p.ends_at
        d = data_local(p.starts_at)
        while d <= data_local(fim):
            dias_com_registro.add(d)
            d += timedelta(days=1)

    if dia is not None:
        inicio, fim_dia = intervalo(dia, dia)
        todos = [p for p in todos if p.starts_at < fim_dia and p.ends_at >= inicio]

    children = _children(db)
    editando = db.get(Punishment, editar) if editar else None
    return _render(
        request,
        "castigos.html",
        user=user,
        punishments=[(p, p.is_active_at(now)) for p in todos],
        children=children,
        children_by_id={c.id: c for c in children},
        trombadices=list(
            db.scalars(select(Trombadice).order_by(Trombadice.occurred_at.desc()).limit(50))
        ),
        fim_sugerido=_local_input(datetime.now(UTC) + timedelta(days=1)),
        editando=editando,
        editando_trombadices=[t.id for t in editando.trombadices] if editando else [],
        erro=erro,
        selected_child=child_id,
        # Castigo só vem de trombadice, então só a lista dela faz sentido aqui.
        categorias=_rotulos_do_tipo(Kind.TROMBADICE),
        categoria_escolhida=categoria,
        tipos=None,
        tipo_escolhido=None,
        busca=q or "",
        dia_escolhido=dia,
        url=_construtor_de_url(
            "/castigos",
            {
                "child_id": child_id,
                "category": categoria.value if categoria else None,
                "q": q,
                "dia": dia,
                "mes": mes,
            },
        ),
        **_calendario(_mes_pedido(mes, dia), dias_com_registro),
    )


def _causas(db: DbSession, trombadice_ids: list[int] | None, child_id: int) -> list[Trombadice]:
    """As trombadices que justificam o castigo, já filtradas pelo filho escolhido
    - marcar a de um irmão colocaria o nome dele neste castigo.

    Devolve lista vazia quando não sobra nenhuma, e quem chama trata isso como
    erro: castigo sem causa não existe (ver `_resolve_trombadices` na API)."""
    if not trombadice_ids:
        return []
    chosen = db.scalars(select(Trombadice).where(Trombadice.id.in_(trombadice_ids)))
    return [t for t in chosen if t.child_id == child_id and t.kind is Kind.TROMBADICE]


@router.post("/castigos")
def punishment_create(
    db: DbSession,
    user: AdminWeb,
    child_id: Annotated[int, Form()],
    ends_at: Annotated[str, Form()],
    reason: Annotated[str, Form()] = "",
    trombadice_ids: Annotated[list[int] | None, Form()] = None,
):
    causas = _causas(db, trombadice_ids, child_id)
    if not causas:
        # O navegador não sabe exigir "pelo menos um checkbox marcado", então
        # quem recusa é o servidor - o mesmo que já recusa na API.
        raise RedirectTo("/castigos?erro=sem-trombadice")

    punishment = Punishment(
        reason=reason.strip(),
        starts_at=datetime.now(UTC),
        ends_at=_parse_local(ends_at),
        child_id=child_id,
        author_id=user.id,
    )
    punishment.trombadices = causas
    db.add(punishment)
    db.commit()
    return _redirect("/castigos")


@router.post("/castigos/{punishment_id}/editar")
def punishment_edit(
    punishment_id: int,
    db: DbSession,
    user: AdminWeb,
    child_id: Annotated[int, Form()],
    ends_at: Annotated[str, Form()],
    reason: Annotated[str, Form()] = "",
    trombadice_ids: Annotated[list[int] | None, Form()] = None,
):
    punishment = db.get(Punishment, punishment_id)
    if punishment is None:
        return _redirect("/castigos")

    causas = _causas(db, trombadice_ids, child_id)
    if not causas:
        raise RedirectTo(f"/castigos?editar={punishment_id}&erro=sem-trombadice#editar")

    punishment.reason = reason.strip()
    punishment.ends_at = _parse_local(ends_at)
    punishment.child_id = child_id
    punishment.trombadices = causas
    # `starts_at` não se edita: quando o castigo começou é fato, não opinião.
    # Para soltar antes da hora existe o botão Encerrar, que preserva o
    # `ends_at` original.
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
# Relatório
# --------------------------------------------------------------------------


@router.get("/relatorio", response_class=HTMLResponse)
def report_page(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    child_id: int | None = None,
    dias: int = 30,
):
    # Chama o mesmo código que serve o app, não uma segunda contagem paralela:
    # duas implementações dariam dois números para a mesma pergunta.
    if dias not in JANELAS:
        dias = 30
    ate = hoje_local()
    dados = report(admin=user, db=db, child_id=child_id, de=ate - timedelta(days=dias - 1), ate=ate)

    children = _children(db)
    return _render(
        request,
        "relatorio.html",
        user=user,
        dados=dados,
        children=children,
        selected_child=child_id,
        dias=dias,
        janelas=JANELAS,
        categorias={c.value: r for c, r in CATEGORIA_ROTULOS.items()},
        # O maior valor da série é o que define a altura das barras. Sem ele o
        # template teria que fazer a conta, e Jinja é ruim nisso.
        pico=max([d.total for d in dados.por_dia] or [0]),
    )


# --------------------------------------------------------------------------
# Frases de abertura
# --------------------------------------------------------------------------


@router.get("/frases", response_class=HTMLResponse)
def splash_page(request: Request, db: DbSession, user: AdminWeb, editar: int | None = None):
    children = _children(db)
    return _render(
        request,
        "frases.html",
        user=user,
        messages=list(db.scalars(select(SplashMessage).order_by(SplashMessage.id.desc()))),
        children=children,
        children_by_id={c.id: c for c in children},
        editando=db.get(SplashMessage, editar) if editar else None,
    )


@router.post("/frases")
def splash_create(
    db: DbSession,
    user: AdminWeb,
    text: Annotated[str, Form()],
    child_id: Annotated[str, Form()] = "",
):
    db.add(SplashMessage(text=text.strip(), child_id=_filho_valido(db, child_id), author_id=user.id))
    db.commit()
    return _redirect("/frases")


@router.post("/frases/{message_id}/editar")
def splash_edit(
    message_id: int,
    db: DbSession,
    user: AdminWeb,
    text: Annotated[str, Form()],
    child_id: Annotated[str, Form()] = "",
):
    message = db.get(SplashMessage, message_id)
    if message is None:
        return _redirect("/frases")

    message.text = text.strip()
    message.child_id = _filho_valido(db, child_id)
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
    users = list(db.scalars(select(User).order_by(User.role, User.display_name)))
    hoje = hoje_local()
    return _render(
        request,
        "contas.html",
        user=user,
        users=users,
        # Quem faz aniversário hoje. Calculado aqui e não no template porque a
        # regra do 29 de fevereiro mora em `app/periodo.py` - repetir um
        # `if` de dia e mês no Jinja seria a segunda resposta pra mesma
        # pergunta.
        aniversariantes={
            u.id for u in users if u.birth_date and e_aniversario(u.birth_date, hoje)
        },
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
    birth_date: Annotated[str, Form()] = "",
):
    if db.scalar(select(User).where(User.username == username.strip())) is not None:
        raise RedirectTo("/contas?erro=usuario-existe")

    try:
        nascimento = _parse_dia(birth_date)
    except ValueError:
        # Conta nova não tem data anterior pra proteger, e o aniversário é
        # opcional: nasce sem, e o pai cadastra depois no cartão da conta.
        nascimento = None

    db.add(
        User(
            username=username.strip(),
            password_hash=hash_password(password),
            display_name=display_name.strip() or username.strip(),
            role=Role(role),
            birth_date=nascimento,
        )
    )
    db.commit()
    return _redirect("/contas")


@router.post("/contas/{user_id}/editar")
def user_edit(
    user_id: int,
    db: DbSession,
    user: AdminWeb,
    display_name: Annotated[str, Form()],
):
    """Corrigir o nome. O `username` não entra: é o login, e trocar ele deixaria
    a criança trancada para fora sem aviso nenhum."""
    if (target := db.get(User, user_id)) is not None and display_name.strip():
        target.display_name = display_name.strip()
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


@router.post("/contas/{user_id}/aniversario")
def user_birthday(
    user_id: int,
    db: DbSession,
    user: AdminWeb,
    birth_date: Annotated[str, Form()] = "",
):
    """Cadastra (ou limpa) o dia de nascimento. Campo vazio apaga: é a única
    forma de desfazer uma data digitada errada, e uma data errada faz a festa
    no dia errado.

    Formulário próprio como o de senha e o de nome, e não um campo a mais no
    de renomear: são coisas diferentes, e o de cima é pra criar conta nova."""
    target = db.get(User, user_id)
    if target is None:
        return _redirect("/contas")

    try:
        target.birth_date = _parse_dia(birth_date)
    except ValueError:
        # Data que não é data não apaga a que estava certa. O <input type="date">
        # nunca manda isso, mas navegador antigo renderiza o campo como texto -
        # e aí quem digita "25/03/2014" perderia o que já estava salvo.
        return _redirect("/contas")

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


@router.post("/contas/{user_id}/toggle-pedidos")
def user_toggle_can_request(user_id: int, db: DbSession, user: AdminWeb):
    # Rota própria, não um parâmetro genérico em cima de /toggle: os três
    # toggles significam coisas diferentes (conta ligada, pode pedir, pode
    # levantar assunto), e um parâmetro tipo string pra escolher qual campo
    # mexer é o tipo de ramificação implícita que este projeto evita.
    if (target := db.get(User, user_id)) is not None:
        target.can_request = not target.can_request
        db.commit()
    return _redirect("/contas")


@router.post("/contas/{user_id}/toggle-assuntos")
def user_toggle_can_discuss(user_id: int, db: DbSession, user: AdminWeb):
    if (target := db.get(User, user_id)) is not None:
        target.can_discuss = not target.can_discuss
        db.commit()
    return _redirect("/contas")


# --------------------------------------------------------------------------
# Assuntos pra conversar
# --------------------------------------------------------------------------


@router.get("/assuntos", response_class=HTMLResponse)
def assuntos_page(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    child_id: int | None = None,
    pendentes: str | None = None,
    editar: int | None = None,
):
    base = select(Assunto).order_by(Assunto.created_at.desc())
    if child_id:
        base = base.where(Assunto.child_id == child_id)
    if pendentes == "sim":
        base = base.where(Assunto.talked_at.is_(None))
    elif pendentes == "nao":
        base = base.where(Assunto.talked_at.is_not(None))
    assuntos = list(db.scalars(base))

    # O pai abrindo a página é o "visto" dele - dos assuntos que o filho
    # trouxe, que é o que a função sabe distinguir (ver app/visto.py).
    marcar_assunto_visto(db, assuntos, user)

    children = _children(db)
    editando = db.get(Assunto, editar) if editar else None
    return _render(
        request,
        "assuntos.html",
        user=user,
        assuntos=assuntos,
        children=children,
        children_by_id={c.id: c for c in children},
        selected_child=child_id,
        pendentes=pendentes,
        # Só o próprio autor corrige, então o formulário de edição não abre pro
        # que o filho escreveu - a mesma regra da API, não uma segunda.
        editando=editando if editando is not None and editando.author_id == user.id else None,
        url=_construtor_de_url(
            "/assuntos", {"child_id": child_id, "pendentes": pendentes}
        ),
    )


@router.post("/assuntos")
def assunto_create(
    db: DbSession,
    user: AdminWeb,
    title: Annotated[str, Form()],
    child_id: Annotated[int, Form()],
    description: Annotated[str, Form()] = "",
):
    if not title.strip():
        raise RedirectTo("/assuntos")
    db.add(
        Assunto(
            title=title.strip(),
            description=description.strip(),
            child_id=child_id,
            author_id=user.id,
        )
    )
    db.commit()
    return _redirect("/assuntos")


@router.post("/assuntos/{assunto_id}/editar")
def assunto_edit(
    assunto_id: int,
    db: DbSession,
    user: AdminWeb,
    title: Annotated[str, Form()],
    child_id: Annotated[int, Form()],
    description: Annotated[str, Form()] = "",
):
    assunto = db.get(Assunto, assunto_id)
    # Nem o pai reescreve a pauta que o filho trouxe, e o que já foi conversado
    # é história - as duas regras são as mesmas da API.
    if assunto is None or assunto.author_id != user.id or assunto.talked_at is not None:
        return _redirect("/assuntos")

    if title.strip():
        assunto.title = title.strip()
    assunto.description = description.strip()
    assunto.child_id = child_id
    db.commit()
    return _redirect("/assuntos")


@router.post("/assuntos/{assunto_id}/conversado")
def assunto_conversado(
    assunto_id: int,
    db: DbSession,
    user: AdminWeb,
    note: Annotated[str, Form()] = "",
):
    if (assunto := db.get(Assunto, assunto_id)) is not None:
        assunto.talked_at = datetime.now(UTC)
        assunto.talked_by_id = user.id
        assunto.talked_note = note.strip()
        db.commit()
    return _redirect("/assuntos")


@router.post("/assuntos/{assunto_id}/reabrir")
def assunto_reabrir(assunto_id: int, db: DbSession, user: AdminWeb):
    """Desmarcar, pelo mesmo motivo que desmarcar tarefa existe: um clique
    errado não pode encerrar pra sempre uma conversa que não aconteceu."""
    if (assunto := db.get(Assunto, assunto_id)) is not None:
        assunto.talked_at = None
        assunto.talked_by_id = None
        assunto.talked_note = ""
        db.commit()
    return _redirect("/assuntos")


@router.post("/assuntos/{assunto_id}/delete")
def assunto_delete(assunto_id: int, db: DbSession, user: AdminWeb):
    if (assunto := db.get(Assunto, assunto_id)) is not None:
        db.delete(assunto)
        db.commit()
    return _redirect("/assuntos")


# --------------------------------------------------------------------------
# Pedidos
# --------------------------------------------------------------------------


@router.get("/pedidos", response_class=HTMLResponse)
def pedidos_page(
    request: Request,
    db: DbSession,
    user: AdminWeb,
    status: str | None = None,
    child_id: int | None = None,
    kind: str | None = None,
):
    filtro = RequestStatus(status) if status in {s.value for s in RequestStatus} else None
    filtro_kind = RequestKind(kind) if kind in {k.value for k in RequestKind} else None

    base = select(Pedido)
    if filtro is not None:
        base = base.where(Pedido.status == filtro)
    if filtro_kind is not None:
        base = base.where(Pedido.kind == filtro_kind)
    if child_id:
        base = base.where(Pedido.child_id == child_id)
    pedidos = list(db.scalars(base.order_by(Pedido.created_at.desc())))

    # O pai vendo a lista é o próprio "visto" - mesma ideia de visto.py, só
    # que na direção contrária (ver comentário lá).
    marcar_visto_pai(db, [p for p in pedidos if p.status is RequestStatus.PENDENTE])

    children = _children(db)
    return _render(
        request,
        "pedidos.html",
        user=user,
        pedidos=pedidos,
        children=children,
        children_by_id={c.id: c for c in children},
        status_escolhido=filtro,
        kind_escolhido=filtro_kind,
        selected_child=child_id,
        categorias={c.value: r for c, r in CATEGORIA_ROTULOS.items()},
        url=_construtor_de_url(
            "/pedidos",
            {
                "status": filtro.value if filtro else None,
                "child_id": child_id,
                "kind": filtro_kind.value if filtro_kind else None,
            },
        ),
    )


def _promover_se_conquista(pedido: Pedido, db: DbSession, autor_id: int) -> None:
    """Aprovar uma proposta de conquista cria, além de decidir o pedido, a
    Trombadice de verdade - inserção nova, não o Pedido mutado com um status
    especial. author_id é de quem aprovou: o pai confirmando, não o filho que
    teve a ideia, preservando a regra de que autor é sempre quem registrou."""
    if pedido.kind is not RequestKind.CONQUISTA_PROPOSTA:
        return
    db.add(
        Trombadice(
            kind=Kind.CONQUISTA,
            title=pedido.title,
            description=pedido.justification,
            category=pedido.category,
            occurred_at=pedido.decided_at,
            child_id=pedido.child_id,
            author_id=autor_id,
        )
    )


@router.post("/pedidos/{pedido_id}/aprovar")
def pedido_aprovar(
    pedido_id: int,
    db: DbSession,
    user: AdminWeb,
    decision_note: Annotated[str, Form()] = "",
):
    pedido = db.get(Pedido, pedido_id)
    if pedido is not None and pedido.status is RequestStatus.PENDENTE:
        pedido.status = RequestStatus.APROVADO
        pedido.decision_note = decision_note.strip()
        pedido.decided_at = datetime.now(UTC)
        pedido.decided_by_id = user.id
        _promover_se_conquista(pedido, db, user.id)
        db.commit()
    return _redirect("/pedidos")


@router.post("/pedidos/{pedido_id}/negar")
def pedido_negar(
    pedido_id: int,
    db: DbSession,
    user: AdminWeb,
    decision_note: Annotated[str, Form()] = "",
):
    pedido = db.get(Pedido, pedido_id)
    if pedido is not None and pedido.status is RequestStatus.PENDENTE:
        pedido.status = RequestStatus.NEGADO
        pedido.decision_note = decision_note.strip()
        pedido.decided_at = datetime.now(UTC)
        pedido.decided_by_id = user.id
        db.commit()
    return _redirect("/pedidos")
