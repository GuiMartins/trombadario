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

## Decisões de arquitetura (não reverter sem motivo)

### O app só funciona em casa — e a prova é o backend responder

Requisito explícito do usuário: fora de casa o app não exibe dado nenhum,
só uma tela dizendo que precisa estar em casa.

A detecção é **alcançabilidade do backend**, não GPS. `GET /api/health` com
timeout curto (~3s); respondeu **e** o `server_id` bate com o que o app
gravou no pareamento inicial → está em casa. Qualquer outra coisa (timeout,
connection refused, `server_id` diferente) → bloqueado.

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

### Castigo ativo é calculado, nunca guardado

`starts_at <= agora < ends_at` e `ended_early_at is null`
(`Punishment.is_active_at`). Um booleano em coluna precisaria de algo rodando
pra virá-lo e ficaria errado no intervalo entre execuções. Encerrar antes da
hora grava `ended_early_at` e **preserva o `ends_at` original**, então o
histórico mostra o que foi dado e o que foi cumprido.

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
