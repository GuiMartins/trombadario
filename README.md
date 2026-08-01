# Trombadário

Registro dos acontecimentos do dia a dia como pai — o que o filho apronta,
com data, título e descrição. O pai cadastra; o filho vê.

App Android (Kotlin/Jetpack Compose) + backend próprio (FastAPI + SQLite)
rodando num servidor de casa. Sem nuvem, sem conta de terceiro, sem
telemetria: os dados nunca saem da rede local.

*Trombadinha + diário.*

## Como funciona

| | Pai (`admin`) | Filho (`child`) |
|---|---|---|
| Ver os acontecimentos | ✅ todos | ✅ só os dele |
| Cadastrar / editar / apagar | ✅ | ❌ |
| Gerenciar contas | ✅ | ❌ |

Duas regras valem pros dois perfis:

- **Só funciona em casa.** Fora da rede de casa o app não mostra nada — nem
  a tela de login. Só um aviso de que é preciso estar em casa.
- **Não dá pra tirar print.** Captura e gravação de tela ficam bloqueadas
  (`FLAG_SECURE`).

## Estrutura

```
android/    app Kotlin/Compose
backend/    API FastAPI + SQLite
```

## Rodando o backend

```bash
cp .env.example .env   # preencha ADMIN_PASSWORD e SECRET_KEY
docker compose up -d
```

A API sobe em `http://<ip-do-servidor>:8090`. Confira com:

```bash
curl http://localhost:8090/api/health
```

No primeiro start a conta de admin é criada a partir do `.env`. Depois
disso, contas se gerenciam dentro do app.

## Rodando o app

Precisa do Android SDK e de um emulador ou celular conectado:

```bash
cd android && ./dev.sh run
```

Na primeira abertura o app pede a URL do servidor (ex:
`http://192.168.31.172:8090`).

Os APKs de release saem assinados no
[GitHub Releases](../../releases) a cada merge em `main`.

## Licença

MIT — veja [LICENSE](LICENSE).
