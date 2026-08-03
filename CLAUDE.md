# Trombadário

Registro de acontecimentos do dia a dia como pai. App Android
(Kotlin/Jetpack Compose) + backend próprio (FastAPI + SQLite) rodando no
servidor ZimaOS de casa, na rede local.

Dois perfis: **pai (`admin`)**, que cadastra os acontecimentos, e **filho
(`child`)**, que **só visualiza** — não cria, não edita, não comenta, não
apaga.

O nome é trocadilho de trombadinha + diário.

## Regra que vale pra sempre

**Toda funcionalidade da conta pai nasce nos dois lugares**: app Android **e**
painel web. Não existe recurso do pai que só um dos dois tenha. Ao implementar
qualquer coisa nova pro pai, os dois são parte do mesmo trabalho, não uma
sequência de "depois eu faço no outro".

O filho é o contrário: **só o app**. Ver a seção do painel web abaixo pro
porquê.

## Princípios gerais

- **Nada de gambiarra.** Toda escolha de ferramenta, biblioteca ou padrão
  arquitetural segue recomendação oficial (documentação do Android/Google,
  docs oficiais da lib) ou um padrão já consolidado de mercado — não uma
  solução improvisada só porque "funciona agora". Na dúvida entre o jeito
  oficial e um atalho mais rápido, o oficial vence, mesmo que dê mais
  trabalho. Mesmo princípio do projeto Casshole, de onde este repo herda
  boa parte das convenções.
- **O filho tem o APK na mão.** Nenhuma regra de permissão pode existir só
  na UI. Esconder botão é UX; a checagem de verdade é sempre no backend.

## Stack

### Backend (`backend/`)

- **FastAPI + Uvicorn**, SQLAlchemy 2.0, **Alembic** pras migrations.
- **SQLite** em volume (`data/trombadario.db`).
- **Auth**: fluxo OAuth2 password + JWT, o do tutorial oficial de segurança
  do FastAPI. Senhas com `passlib[bcrypt]`. O papel (`admin`/`child`) vai no
  token.
- **Migrations nunca destrutivas** — o banco guarda dado real; toda mudança
  de schema é uma `Migration` explícita do Alembic.

### App (`android/`)

- **UI**: Jetpack Compose + Material3, tema claro/escuro/sistema.
- **Rede**: Retrofit + OkHttp + kotlinx.serialization. A API é o ponto
  central do app (diferente do Casshole, que é offline-first).
- **Token**: `EncryptedSharedPreferences` (`androidx.security:security-crypto`)
  — é credencial de conta real.
- **URL do servidor**: DataStore (dado não sensível).
- **SDK**: `compileSdk`/`targetSdk` 34, `minSdk` 26, JVM target 17, Kotlin
  1.9.24, AGP 8.5.2, compose-bom `2024.06.00`, compiler extension 1.5.14.

## Identidade visual: caderno de papel

Veio de um mockup do usuário (Claude Design, 02/08/2026). O que vale dele é a
**linguagem visual**, não posicionamento de elemento nem features — ele foi
explícito nisso.

**As cores foram amostradas pixel a pixel do mockup**, não estimadas:

| | claro | papel |
|---|---|---|
| folha | `#FBF4E2` | fundo de tudo |
| pauta | `#E6D9B8` | linhas horizontais |
| margem | `#E7A79A` | linha vertical da lateral |
| card | `#FFFDF7` | "papelzinho colado", com borda âmbar |
| recado | `#FFF0C2` + borda tracejada `#FDE4AB` | formulário / destaque |
| tinta | `#3A2E22` / `#8A7357` | texto e texto secundário |
| acentos | `#40A4A1` `#E0A030` `#E4634F` | teal, âmbar, coral |

**Onde a paleta do mockup foi ajustada, foi por contraste medido — não por
gosto.** Dois casos, os dois com número:

O **vermelho**: o do mockup (`#E8562D`) dá **3,3:1** sobre o papel — passa em
texto grande e reprova em texto pequeno e com branco por cima. `#C13A14` é a
mesma matiz mais funda, **4,9:1** no papel e **5,4:1** com branco. Então:
- `#E8562D` (`MarkerRed` / `--marker`) só na assinatura grande — o nome do app.
- `#C13A14` (`MarkerInk` / `--marker-ink`) em tudo que é lido de perto: datas nos
  cards, fundo de botão, aba ativa.

O **texto secundário**: `#8A7357` reprovava nas três superfícies (**4,10** no
papel, **4,42** no card, **3,96** no recado; o mínimo é 4,5). Virou `#6F5C40`,
que dá **5,8 / 6,3 / 5,6** e continua claramente secundário contra os 12:1 do
texto principal. O tema escuro já passava (5,3 a 7,1) e não mudou.

**Fontes vendorizadas**, nunca CDN (mesmo motivo do HTMX — o ZimaOS pode estar
sem internet):
- **Caveat Brush** só em título e cabeçalho. Texto corrido em letra de mão cansa,
  e o filho é quem mais lê aqui.
- **Nunito** no resto.
Ambas OFL; a licença acompanha os arquivos em `web/static/fonts/`.

**A folha é desenhada, não é imagem.** No Android, `NotebookBackground` usa
`drawBehind` e fica **na raiz** — desenhada uma vez, aparece em toda tela sem
cada uma se lembrar dela (dentro do `MainNavHost` cada tela também desenha a sua,
para poder virar — ver "Trocar de tela é virar a página"). Por isso todo
`Scaffold` e `TopAppBar` do app é
`Color.Transparent`: um container opaco tapa a folha. Na web é
`repeating-linear-gradient`. Nos dois casos as linhas acompanham qualquer altura
sem esticar nem cortar.

**Ninguém escreve em cima da linha vermelha.** A margem fica em
`NotebookMarginX` (24dp) e o conteúdo recua `NotebookGutter` (mais 24dp),
aplicado num lugar só — no `AdaptiveScreen` e no título da `TopAppBar`. Na web,
`main` tem `padding-left` maior que `--margin-x` pelo mesmo motivo.

**Em tela larga quem centraliza é a folha, não só o conteúdo.** Acima de 648dp
(`PAGE_MAX_WIDTH`) a página limita e centraliza sobre uma "mesa"
(`DeskLight`/`DeskDark`, cor própria — reusar `surfaceVariant` deixava a mesa
amarelo-gema competindo com o papel). Sem isso a margem ficava colada na borda
esquerda da tela enquanto o texto estava no meio: uma linha vermelha solta longe
de tudo. **Verificado num AVD de tablet real** (`trombadario_tablet`,
1280x800dp), não só no papel.

> Ao mexer nisso, cuidado com a ordem dos modifiers: `fillMaxSize()` **antes** do
> `widthIn` fixa min=max na largura do pai e o teto é silenciosamente ignorado.
> Foi exatamente o que aconteceu na primeira versão, e só apareceu no tablet.

**`secondaryContainer` precisa estar definido** no tema do Android. Sem ele o
Material entrega o lilás padrão em chip selecionado e no indicador da nav bar —
destoa de tudo e não é óbvio de onde vem.

### Trocar de tela é virar a página

A folha gira presa pela **lombada esquerda**, do lado da margem vermelha, e a de
baixo assenta. Mesmos 360ms e mesmos ângulos nos dois lados — `PAGE_TURN_MS` no
Android, `folha-virando`/`folha-assentando` na web.

Três coisas aqui não são óbvias e quebram em silêncio se mexidas:

**O `NavHost` declara `EnterTransition.None` de propósito.** As transições
prontas do Compose não sabem rotacionar em 3D; quem anima é a `graphicsLayer` de
cada tela, dentro do `PageSheet`. O que segura a tela viva durante a virada é a
própria animação de ângulo — sem ela a troca vira corte seco. É o que o
`PageTurnTest` protege.

**A folha é desenhada dentro do `PageSheet`, não só na raiz.** Se a pauta e a
margem ficassem paradas no fundo, o que se veria era o texto deslizando sobre um
papel imóvel. A da raiz continua lá, e é ela que aparece como "próxima página"
enquanto a de cima está levantada.

**O sentido sai do nome da rota** (`pageDepth`), não de ida-e-volta na pilha:
assim o botão "voltar" do sistema e o toque na aba anterior viram para o mesmo
lado. Aba nova entra na tabela; tela que se abre de dentro de uma aba não
precisa, cai no ramo "mais fundo".

> **Como olhar a animação.** Não dá rodando o app: a `MainActivity` é
> `FLAG_SECURE`, então captura e gravação saem em branco. O `PageTurnTest` roda
> numa activity de teste, que não é secreta, para o relógio no meio do movimento
> e grava o quadro no `cacheDir`. Para pegar o arquivo é preciso instalar os dois
> APKs na mão e chamar `am instrument` — o `connectedAndroidTest` desinstala tudo
> no fim e leva o cache junto.

Na web é a mesma virada com `@view-transition`, em CSS puro. Navegador sem
suporte simplesmente troca de página como antes — nada depende disso para
funcionar. **Só foi conferida a olho no Android**; na web foi conferido que as
regras são entendidas pelo navegador, não o movimento.

## Decisões de arquitetura (não reverter sem motivo)

### O app só funciona em casa — e a prova é o backend responder

Requisito explícito do usuário: fora de casa o app não exibe dado nenhum,
só uma tela dizendo que precisa estar em casa.

A detecção é **alcançabilidade do backend**, não GPS. `GET /api/health` com
timeout curto (~3s); respondeu **e** se identificou como Trombadário
(`app == "trombadario"`) → está em casa. Qualquer outra coisa (timeout,
connection refused, resposta que não é do Trombadário) → bloqueado.

**A checagem é sem estado, de propósito.** A primeira versão comparava o
`server_id` contra o valor gravado no pareamento. Foi removido em 02/08/2026
porque o custo era real e o ganho não:

- **Custo**: o `server_id` mora no banco, então zerar ou restaurar o banco muda
  ele e **todo app pareado passa a dizer "você não está em casa"** até refazer o
  pareamento. Isso aconteceu de verdade, ao limpar os dados de teste.
- **Ganho**: só distinguiria "outro Trombadário respondendo nesse endereço" —
  exigiria alguém rodando esta mesma app, na mesma faixa de IP, na mesma porta,
  em outra rede.
- **E não protegia de nada**: um filho decidido não precisa burlar o
  `server_id`; é só ir em Configurações → Trocar de servidor e apontar pra outro
  lugar. A comparação só atrapalhava quem estava sendo honesto.

O campo `server_id` continua no `/api/health` como informação de diagnóstico —
nada no app depende dele.

Por que não GPS (foi considerado e descartado pelo usuário): exigiria
`ACCESS_FINE_LOCATION`, Play Services, tratamento de fix indoor ruim e de
app de mock location. A alcançabilidade é honesta por construção — o
backend vive num IP privado, que não é roteável de fora, então se ele
responde o aparelho está na rede de casa. O `server_id` (UUID gerado uma vez
e guardado no banco) fecha o caso da rede alheia que por acaso tenha algo na
mesma porta.

`HomeNetworkGate` é um singleton com `StateFlow<HomeState>`
(`Checking`/`AtHome`/`AwayFromHome`), reavaliado no start, ao voltar do
background (`Lifecycle.Event.ON_START`) e a cada mudança de rede
(`ConnectivityManager.registerDefaultNetworkCallback`). **Fecha por
padrão**: só `AtHome` libera. Enquanto não for `AtHome`, o `NavHost` inteiro
é substituído por `AwayFromHomeScreen` — troca de raiz, não diálogo por
cima, então não existe tela com dado renderizada por baixo. O gate vem
**antes do login**: fora de casa nem a tela de senha aparece.

### Três conceitos distintos, que não se misturam

- **Trombadice** — o que o filho fez. Tem data editável (`occurred_at`), porque
  o pai registra depois do fato.
- **Tarefa** — o que ele deveria fazer, com periodicidade (diária / dias da
  semana / dia do mês / avulsa). Trocar a periodicidade **limpa** os campos que
  deixaram de valer, senão a tela mostraria "diária, às segundas e quintas".
- **Castigo** — o que veio depois. Aponta as trombadices que o causaram (N:N).

Uma trombadice pode apontar a tarefa não cumprida (`task_id`), mas **só tarefa
do mesmo filho** — o vínculo cruzado afirmaria algo falso. Mesma regra para as
trombadices de um castigo.

`ondelete` importa e não é acidente: `task_id` é **SET NULL** (apagar a tarefa
não pode apagar o registro do que aconteceu por causa dela), `child_id` é
CASCADE, `author_id` é RESTRICT (a história sobrevive à conta de quem escreveu).

### Frases de abertura: o sorteio é do servidor

O pai cadastra N frases; ao abrir o app, a conta de filho vê uma delas como tela
de carregamento. Cada frase é **de um filho específico ou de todos** (`child_id`
nulo = todos) — a decisão é por frase, não uma configuração global.

- **O sorteio acontece no backend** (`GET /api/splash-messages/random`), não no
  app. Assim o aparelho não influencia a escolha e **nunca recebe frase escrita
  pra um irmão** — mandar a lista pro cliente filtrar entregaria o texto alheio
  junto. A rota de listagem é só do admin.
- **A duração é fixa (`SPLASH_DURATION_MS`, 2,5s)**, não "enquanto carrega". Na
  LAN os dados chegam em ~200ms, então uma barra honesta piscaria e sumiria
  antes de dar pra ler — e a frase existe pra ser lida.
- **Sem frase que se aplique, a tela é pulada** em vez de aparecer vazia
  (`text` nulo na resposta).
- **Só o filho vê.** É o pai falando com ele; o pai reencontrar a própria
  mensagem a cada abertura seria só ruído.

### Castigo ativo é calculado, nunca guardado

`starts_at <= agora < ends_at` e `ended_early_at is null`
(`Punishment.is_active_at`). Um booleano em coluna precisaria de algo rodando
pra virá-lo e ficaria errado no intervalo entre execuções. Encerrar antes da
hora grava `ended_early_at` e **preserva o `ends_at` original**, então o
histórico mostra o que foi dado e o que foi cumprido.

### Categoria é lista fechada

`models.Category`, oito valores. Campo livre viraria dez jeitos de escrever
"falta de respeito" e nenhum relatório sairia. A **ordem do enum é a ordem na
tela**, do mais comum ao menos. Acrescentar valor é migration; tirar valor exige
decidir o que fazer com o que já está gravado, então na prática só se aposenta
escondendo da tela.

**Sem título e com tarefa atrelada, o título vira o nome da tarefa** — o que
aconteceu foi não ter feito aquilo, e obrigar a repetir na mão só produziria duas
versões do mesmo nome. Sem tarefa, o título continua obrigatório (422).

> **`Enum(..., native_enum=False)` guarda o NOME do membro, não o valor.**
> `Role` grava `"ADMIN"`, `Periodicity` grava `"DAILY"`, `Category` grava
> `"OUTRA"`. Um `server_default` de migration escrito como `"outra"` passa em
> toda a suíte e estoura `LookupError` na primeira leitura em produção — porque
> os testes montam o schema com `create_all` e nunca passam pelas migrations.
> É o que `tests/test_migrations.py` existe para pegar: ele roda a migration de
> verdade contra um banco com linha dentro e lê de volta pelo ORM. **Migration
> nova que mexa em coluna de enum precisa de um caso lá.**

### Editar o que já foi cadastrado

Só o pai, e isso não é botão escondido: todo `PATCH` da API exige `AdminUser` e
o painel inteiro é `AdminWeb`. No painel, editar **reaproveita o formulário de
cima** (`?editar={id}`) em vez de abrir página nova — formulário separado seria
um segundo lugar para lembrar de mexer.

Três coisas que não se editam, de propósito:
- **`author_id`** — quem cadastrou continua sendo quem cadastrou. Corrigir um
  "machou" que era "machucou" não é assumir o registro do outro.
- **`Punishment.starts_at`** — quando o castigo começou é fato. Para soltar
  antes da hora existe Encerrar, que preserva o `ends_at` original.
- **`User.username`** — é o login. Trocar trancaria a criança para fora sem
  aviso nenhum. O nome de exibição, esse sim.

Ao trocar a periodicidade de uma tarefa, o campo que a nova não usa é **zerado**
— senão sobra "segunda e quarta" numa tarefa que virou de todo dia.

### "Visto pelo filho" acontece sozinho, na leitura

`Trombadice.seen_at` e `Punishment.seen_at`, nulo = ainda não viu. Requisito do
usuário, literal: **"tem que ser automático, sem o filho precisar dizer que
viu"**. Não existe botão, e nem chamada extra que o app precise lembrar de
fazer — quem carimba é `app/visto.py`, chamado pelas próprias leituras do filho.

- **Instante e não booleano**: "viu" sem "quando" não responde a pergunta que o
  pai faz de verdade, que é se ele viu antes ou depois de alguma coisa.
- **Idempotente, primeira vez é que vale.** Abrir o app de novo não reescreve o
  carimbo — senão "visto às 20h" viraria a hora da última olhada.
- **Só conta de filho marca, e só o que é dele.** O pai conferindo a lista não
  marca nada; se marcasse, o campo deixaria de responder o que ele pergunta.
- **`/current` marca só o castigo ativo**, porque é só ele que aparece na tela.

> **É escrita dentro de um GET**, o que normalmente é errado. Vale aqui porque
> não há cache nem prefetch entre app e servidor (a leitura só acontece com a
> tela aberta), a escrita é idempotente, e a alternativa — o app avisar depois
> de mostrar — depende de cada tela nova lembrar de avisar, e a que esquecer
> vira uma mentira silenciosa para o pai. **Se algum desses três deixar de
> valer, revisar.**

### Relatório conta em Python, sobre dia local

`routers/reports.py`. Nada de `GROUP BY date(occurred_at)`: o banco guarda UTC e
o agrupamento que interessa é por dia **local** — no Brasil, tudo que acontece
depois das 21h cairia no dia seguinte. `app/periodo.py` faz essa tradução num
lugar só, pro fuso não voltar a ser decidido em cinco lugares.

O volume é o de uma casa, algumas centenas de linhas por ano. Se um dia virar
dezenas de milhares, é aqui que se mexe.

Duas escolhas de leitura que não são acidente: a série diária **inclui os dias
zerados** (sem eles a sequência limpa, que é a notícia boa, some do gráfico), e a
média é sobre **os dias que tiveram alguma coisa** — dividir por 30 num mês com
duas trombadices dá 0,07 e não diz nada.

### O painel web é só do pai — e isso é segurança

`app/web/`, Jinja2 + HTMX servidos pelo mesmo FastAPI. Zero Node, zero build
step, mesma imagem.

**Conta de filho não entra**, nem com token válido forjado no cookie: toda rota
web passa por `require_admin_web`. O motivo é concreto — navegador tira print à
vontade e não existe `FLAG_SECURE` na web. Deixar o filho entrar por aqui
anularia o bloqueio de captura do app. Ele recebe uma página explicando que o
acesso dele é pelo celular.

**Autenticação por cookie, não Bearer**: navegador não manda `Authorization` em
navegação normal. O mesmo JWT vai num cookie `HttpOnly` + `SameSite=Strict`
(`Secure=False` porque é HTTP na LAN). A API continua Bearer pro Android — mesmo
token, dois transportes.

> **CSRF**: a defesa é `SameSite=Strict`, sem token de double-submit. O serviço
> não é roteável de fora e `SameSite=Strict` impede qualquer site externo de
> disparar um POST autenticado. **Se um dia isso sair da LAN, revisar.**

Nada de CDN: CSS próprio e HTMX vendorizado em `app/web/static/`. O ZimaOS pode
estar sem internet e a página tem que abrir do mesmo jeito.

**Filtro troca só a lista, nunca a página inteira.** Os `<a>` de filtro têm
`href` **e** `hx-get`: sem JavaScript o link funciona como sempre, com ele só o
`#lista-*` é trocado. Não é enfeite — como navegação normal, o filtro recarregava
a página e apagava o que já estava digitado no formulário acima (era um bug
relatado). O cabeçalho não entra na troca, então quem depende do endereço atual
precisa vir junto por `hx-select-oob` (é o caso do `#tema-next`).

**Claro e escuro com `light-dark()`, uma cor em um lugar só.** Cada variável é
declarada duas vezes: o valor claro puro e depois o `light-dark(claro, escuro)`.
Navegador que não conhece a função descarta a segunda linha e fica no claro — a
página não quebra, só não escurece. Um bloco `@media` separado obrigaria a manter
duas listas de cores em sincronia na mão. O alternador do topo só muda
`color-scheme`; sem escolha feita, quem manda é o sistema.

> **`TZ` no compose não é enfeite.** O `<input type="datetime-local">` manda a
> hora que a pessoa digitou **sem fuso nenhum**, e o servidor cola o dele. Em UTC
> — o padrão do container — o campo "Quando" abre 3h à frente e o que é cadastrado
> pela web é **gravado 3h errado**. A imagem já tem `tzdata`; basta a variável.

### A configuração começa no backend, pelo navegador

Enquanto não existe **admin ativo**, o servidor está em modo setup e toda rota
web redireciona pra `/setup`, que cria a conta do pai. Criada, `/setup` some.

- A condição é derivada do banco (`app/setup_state.py`), **não** de uma flag em
  disco — flag dessincroniza e ou tranca o dono pra fora, ou reabre o assistente
  num app já em uso.
- **Não existe seed por `.env`.** Ele só fazia sentido enquanto um humano
  escrevia o arquivo por SSH; instalado pela loja do CasaOS ninguém vê um `.env`.
- **`SECRET_KEY` é gerada e guardada no banco** na primeira subida
  (`app/server_identity.py`), pelo mesmo motivo. Env sobrescreve se você quiser.
  Um default fixo faria toda instalação do mundo assinar token com a mesma chave.
- `GET /api/health` expõe `setup_required` pro app não oferecer um login que
  ninguém consegue passar.

### Sem cache local — isso é segurança, não simplicidade

O app não grava evento nenhum em disco. Não tem Room, não tem cache de
resposta. Busca da API e mostra loading/erro.

Isso começou como "evitar abstração prematura", mas virou requisito: é o que
garante que, quando o gate bloqueia, não existe dado no aparelho pra vazar.
**Não adicionar cache offline sem revisitar a regra de "só em casa" acima.**

### Screenshot bloqueado

`window.setFlags(FLAG_SECURE, FLAG_SECURE)` no `onCreate` da `MainActivity`
— API oficial do `WindowManager`, vale pro app inteiro. Bloqueia captura de
tela, gravação de tela, `adb shell screencap`, e apaga o thumbnail do app na
lista de recentes.

- **Ligado em todos os build types, inclusive debug.** Sem escape hatch: o
  requisito é "impossibilitar", e o APK debug é justamente o que vai pro
  celular.
- **Consequência prática, pra não estranhar depois**: screenshot do emulador
  sai **preta**. Isso não é bug. A evidência de teste manual no PR passa a
  ser descrição do fluxo + trecho de `./dev.sh logs`, não print.
- **Limite honesto**: não impede alguém fotografar a tela com outro celular.
  Não existe defesa Android pra isso.

### `child_id` no evento desde o v1

Mesmo com um filho só. Sem ele, um segundo filho depois vira migration de
dados chata. O filho só enxerga eventos com o `child_id` dele — checado no
backend, não na query do cliente.

### Navegação: `NavigationBar` padrão, não a barra flutuante do Casshole

O Casshole tem uma `FloatingNavigationBar` própria no slot
`floatingActionButton` do `Scaffold`. Aqui usamos o `NavigationBar` do
Material3 no slot `bottomBar` — é o componente oficial e não havia pedido de
visual específico. A barra só aparece nos destinos de aba; telas filhas
(detalhe, formulário) usam a altura toda e a seta de voltar.

Transições do `NavHost`: slide + fade de 220ms configurado **uma vez** nos
parâmetros do `NavHost` (vale pra todas as rotas, não repetir por
`composable()`). Tocar numa aba sempre leva pra raiz dela, sem
`saveState`/`restoreState` — restaurar reabriria uma tela filha que o usuário
já tinha deixado.

### Injeção de dependência manual (`AppContainer`)

Sem Hilt. O grafo é pequeno (3 stores + repositório + gate) e a orientação
oficial do Android é começar assim. `viewModelFactory { }`
(`ui/ViewModelFactory.kt`) é a ponte pro `viewModel()`, que exige uma factory.

### O feed recarrega no `ON_RESUME`, não no `init` do ViewModel

`LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` em `FeedScreen`. O ViewModel
sobrevive à navegação pro formulário e pro detalhe, então carregar só na
construção deixava o feed desatualizado depois de cadastrar, editar ou excluir
— bug observado ao vivo (o evento entrava no servidor e a lista continuava
"Nada registrado ainda").

### Nav bar de 4 abas; Contas mora em Configurações

Trombadices · Tarefas · Castigo · Configurações, iguais pros dois papéis — o
que muda é o conteúdo, não a estrutura. **Contas ficou fora da barra** e vira um
item dentro de Configurações (só pro pai): com ela seriam 5 abas, o limite do
`NavigationBar` do Material3 e apertado demais num celular.

A tela de **Castigo do filho** existe pra responder uma coisa só, e responde
grande: ícone, "Você está de castigo" e até quando — ou "Você não está de
castigo". `is_active` vem calculado do servidor; o relógio do celular não decide
isso.

### i18n: só português

Diferente do Casshole (PT/EN/ES). É um app familiar de uso privado; três
idiomas seriam trabalho sem uso. Strings continuam **nunca hardcoded** —
sempre `res/values/strings.xml` via `stringResource`, pra não custar caro
adicionar idioma depois.

## Convenções de código

- **Código em inglês** — identificadores (classes, funções, variáveis, nomes
  de arquivo, pacotes) e comentários. Texto de UI é em português e vive em
  `strings.xml`.
- **Comentários só quando explicam um "porquê" não óbvio** — uma invariante
  escondida, um workaround, um comportamento que surpreenderia quem lê. Não
  comentar o óbvio.
- **Sem abstração prematura.** Duplicação pequena (2-3 linhas) é aceitável;
  extrair componente compartilhado só quando o mesmo bloco não-trivial
  aparece em 2+ lugares de verdade.
- **Composables repetidos em 2+ telas vão pra `ui/components/`.**

## Fluxo de trabalho (git) — GitFlow

Mesmo fluxo do Casshole. Duas branches de longa duração:

- **`develop`** — branch de integração, **default branch no GitHub**. Todo
  trabalho do dia a dia (`feature/*`, `fix/*`) nasce daqui e volta via PR.
- **`main`** — reflete o que foi de fato buildado/instalado no celular. Só
  recebe merge via `release/*` ou `hotfix/*`, **sempre squash**.

### Mensagens de commit e PR: Gitmoji + Conventional Commits

```
<tipo>: <emoji> <descrição>
```

Ex: `feat: ✨ gate de rede de casa`, `fix: 🐛 token expirado não deslogava`.

**O tipo vem antes do emoji, não depois**: `mathieudutour/github-tag-action`
calcula o bump com regex ancorada no início da string (`^feat:`, `^fix:`) —
um emoji na frente quebra a detecção.

| Tipo        | Emoji | Quando usar                                      |
|-------------|-------|--------------------------------------------------|
| `feat:`     | ✨    | funcionalidade nova                               |
| `fix:`      | 🐛    | correção de bug                                   |
| `docs:`     | 📝    | só documentação                                   |
| `refactor:` | ♻️    | reestrutura sem mudar comportamento               |
| `test:`     | ✅    | adiciona/corrige teste                            |
| `chore:`    | 🔧    | config, CI, tooling, dependências                 |
| `style:`    | 💄    | mudança visual/UI sem lógica nova                 |
| `perf:`     | ⚡️    | performance                                       |
| `security:` | 🔒️    | correção de segurança                             |
| `revert:`   | ⏪️    | reverte um commit/PR anterior                     |

**Cuidado**: a string literal `BREAKING CHANGE` em qualquer lugar do corpo
do commit/PR é lida pela action como bump major de verdade, mesmo quando só
está sendo *mencionada*. Nunca escrever à toa.

**O título do PR `develop -> main` É o commit que a action vê** (squash
colapsa tudo num commit só) — precisa começar com `feat:`/`fix:`, nunca com
`release:` ou outro prefixo genérico, senão cai no `default_bump: patch`.

Passos, sem pedir confirmação a cada um:

1. `git checkout -b feature/nome-descritivo` a partir de `develop` atualizado.
2. Implementar, buildar (`./dev.sh build`), testar ao vivo no emulador.
   A suíte automatizada roda **só no CI**, nunca localmente.
3. `git add` arquivos específicos (nunca `-A` sem checar o `git status`).
4. `git push -u origin <branch>`.
5. `gh pr create --base develop` com corpo descrevendo a mudança + evidência
   do teste manual (descrição do fluxo + logcat — **não print**, ver
   `FLAG_SECURE` acima).
6. `gh pr checks <número> --watch`, depois `gh pr merge --squash --delete-branch`.
7. `git fetch origin --prune && git checkout develop && git pull`.

**Nunca fazer `git merge main` dentro de `develop`** — `develop` já contém
tudo que foi squash-mergeado pra `main`. A divergência que aparece no GitHub
é cosmética (squash quebra ancestralidade), não falta nada. Se o PR
`develop -> main` aparecer como não-mergeável, resolver num branch
descartável a partir de `develop`, mantendo o conteúdo de `develop` nos
conflitos, e squash-mergear esse branch.

## CI (GitHub Actions)

`.github/workflows/ci.yml` roda em todo PR e em push em `main`/`develop`.
`dorny/paths-filter` decide o que executa:

- **`backend-tests`** — `ruff check` + `pytest` (só se `backend/**` mudou).
- **`unit-tests`** — `./gradlew testDebugUnitTest` (sempre; custo baixo).
- **`instrumented-tests`** — emulador API 30, só se `android/**` mudou. Quando
  pulado, o GitHub reporta "skipped", que **conta como passou** pros required
  status checks (comportamento oficial de job condicional, não gambiarra).
  **Sem cache de snapshot do AVD** — mesma decisão do Casshole, onde a dança de
  dois boots bateu num bug real e o emulador nunca saía de "device offline".

Os testes instrumentados existem porque `EncryptedSharedPreferences` passa pelo
keystore do Android e o DataStore grava por `Context` — nenhum dos dois tem
caminho só-JVM que valha a pena fingir.

## Release automática

`.github/workflows/release.yml` roda em todo push em `main`:
`mathieudutour/github-tag-action` calcula o SemVer pelos Conventional Commits →
`assembleRelease` assinado e minificado (R8) → `softprops/action-gh-release`
publica `trombadario-vX.Y.Z.apk`.

### Keystore

`trombadario-release.jks` (PKCS12, alias `trombadario-release`, validade até
2053). **Nunca vai pro repo** (`*.jks` no `.gitignore`); vive como backup do
usuário fora do repo e como o secret `RELEASE_KEYSTORE_BASE64`, decodificado
num arquivo temporário só durante o job e apagado com `if: always()`.

Quatro secrets, criados manualmente pelo usuário (o assistente não pode criar
secrets): `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

**Perder a keystore = nunca mais publicar update assinado com a mesma
identidade** — só reinstalando do zero nos celulares.

`app/proguard-rules.pro` foi validado buildando `assembleRelease` de verdade e
rodando o APK no emulador: pareamento, login e o feed inteiro funcionam sob R8.
As regras que importam são as de kotlinx.serialization (a `.serializer()` do
companion é achada por reflexão — sem o keep, os DTOs falham **só em release**)
e as de Retrofit/Tink.

## Deploy (ZimaOS)

- Vive em `/DATA/AppData/trombadario/`, subido com `docker compose up -d`.
  **Nunca `docker run` manual** — o CasaOS marca como "Legacy App".
- Toda sessão de compose por SSH precisa de `export DOCKER_CONFIG=/tmp/dockercfg`
  antes: `HOME=/DATA` não é legível e a descoberta de plugins do Docker CLI
  morre com "unknown command: docker compose".
- `sudo` no servidor pede senha — não contar com ele.
- **Porta 8090** no host. Servidor em `192.168.31.172` (DHCP — por isso a URL
  é configurável no app em vez de compilada no APK).

## Dev loop / testes

- `android/dev.sh <comando>`: `emulator`, `build`, `install`, `start`, `run`
  (build+install+start), `logs`.
- Reusa o AVD `money_hole_test` do Casshole — o nome é só do dispositivo do
  emulador, recriar não traz ganho.
- **Testes automatizados rodam só no CI.** Localmente a verificação é teste
  manual ao vivo no emulador. Ao implementar lógica de negócio, escrever o
  teste junto do código mesmo assim — ele roda depois, no PR.
- Backend local: `cd backend && python -m pytest`.
