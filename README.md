# enigmas-backend

This is the Java backend for PRESOS, a terminal puzzle game played in the browser. It runs the ciphers, checks answers, and keeps everyone in the same shared game. The project uses Java 21, Spring Boot, Maven, PostgreSQL, and Flyway.

## Run locally

1. Install JDK 21 and set JAVA_HOME to its directory.
2. Start PostgreSQL and create an empty database:

```sql
CREATE DATABASE enigmas;
```

3. Set these variables in the IDE run configuration:

| Variable    | Value                                              |
| ----------- | -------------------------------------------------- |
| DB_URL      | jdbc:postgresql://localhost:5432/enigmas (default) |
| DB_USERNAME | postgres (default)                                 |
| DB_PASSWORD | Your PostgreSQL password (required)                |
| PORT        | 8080 (default)                                     |

Run `br.com.enigmas.backend.EnigmasBackendApplication`, or use PowerShell:

```powershell
$env:DB_USERNAME = 'postgres'
$secret = Read-Host 'PostgreSQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
.\mvnw.cmd spring-boot:run
```

Flyway creates the game_sessions table. Hibernate validates the schema in every profile; it does not create or update tables. Add future schema changes as numbered SQL migrations under src/main/resources/db/migration. Do not edit migrations that have already run.

The Angular application proxies /api to this server. Start Angular separately with npm start or npm run start:lan. Its default backend address is http://127.0.0.1:8080; set BACKEND_URL on the Angular host to change it. Browsers use the Angular origin, including on phones. No permissive CORS configuration is needed.

For local development, the application explicitly imports the optional .env.properties file from the project working directory. This file is ignored by Git and stays outside the packaged resources. It can contain DB_PASSWORD=your-local-password. Environment variables remain available for IDE, shell and deployment configuration. Run with the backend project as the working directory. Plain .env files are not loaded automatically.

## Responsibilities and packages

```text
br.com.enigmas.backend/
├── enigma/
│   ├── controller/   Public computer catalog endpoint
│   ├── dto/          Public metadata, without answers or cipher definitions
│   ├── model/        Server-only campaign definitions
│   └── service/      Campaign loading and cipher transformations
├── terminal/
│   ├── controller/   Session and command endpoints
│   ├── dto/          Request/response contracts
│   ├── entity/       JPA session persistence
│   ├── model/        Working game snapshot
│   ├── repository/   Database access and session row locking
│   └── service/      Commands, transmission, transactions and server identity
└── shared/exception/ API error handling
```

Controllers delegate to services. Services own rules and transaction boundaries; repositories own persistence. API DTOs never expose JPA entities or puzzle definitions. Feature-specific code stays inside its feature. Test packages mirror production packages.

Game commands, riddles, answers, and dialogue stay in Portuguese so the examples match what players see. The setup instructions and technical explanations are in English.

The ten puzzle definitions live in src/main/resources/puzzles/computers.json. They are versioned campaign content, not editable database records. Change riddles, answers, routes, initial links, awake dialogue, and trance sequences there; implement cipher changes in CipherService. Both ends of each initial link must agree. Restart the backend after changing the campaign.

## API contract

| Method | Path                        | Purpose                                                   |
| ------ | --------------------------- | --------------------------------------------------------- |
| GET    | /api/computers              | Public id, name, location, serial and welcome text        |
| GET    | /api/session                | Current backend process identity                          |
| GET    | /api/games/events           | Live notifications when the shared game changes           |
| POST   | /api/master/reset           | Reset the shared game using the local master key          |
| POST   | /api/games                  | Join the shared campaign; returns 201 (legacy API status) |
| GET    | /api/games/current          | Fetch the latest game state                               |
| POST   | /api/games/current/commands | Execute one terminal command                              |

The two /current endpoints require the UUID returned as gameId in the X-Game-Session header. This is an anonymous session identifier, not a user account. Angular obtains the shared identifier on every connection; old private browser tokens are ignored. The server never accepts client-provided unlocked keys, histories, links or puzzle answers as state.

A command request contains:

```json
{
  "computerId": "chma_vva",
  "text": "dormindo",
  "requestId": "f1485f96-e5df-4075-8c24-d0d68918149a",
  "revision": 0,
  "serverSessionId": "091138c4-76ea-479e-99e3-1fa477c17642"
}
```

Use the revision and state.serverSessionId from the latest response and a fresh requestId per action. A response is { gameId, revision, state }. Malformed input returns 400, missing games/computers return 404, and outdated revisions or game cycle identifiers return 409. Messages are limited to 2000 UTF-16 characters. Sessions are locked during changes. Retrying the last accepted requestId returns its current snapshot without executing twice; older commands must not be replayed automatically.

## Session behavior

- PostgreSQL stores connections, modes, unlocked keys, counters, destination, recent output and command history.
- All browsers join the same campaign row, seeded by Flyway V2. Page reloads rejoin it. The Angular client reloads shared state on SSE notifications. Older private rows are preserved.
- Restarting the Spring process resets each game when it is next accessed. Restarting Angular alone does not reset it. This preserves the campaign's deliberate server-reset rule.
- This process-identity model is intended for a single backend instance.
- Existing browser-only saves are not imported; the migration starts a fresh server-managed game.
- Each computer retains its latest 500 entries and 500 commands.
- Clearing a terminal clears its displayed history and recall list.
- Failed sends are not automatically replayed. Angular reloads the snapshot and asks the player to check history.
- Puzzle completion requires the correct cipher output, full ordered route and sleeping mode.

## Verification

```powershell
.\mvnw.cmd verify
```

Tests use an isolated H2 database in PostgreSQL compatibility mode, apply the Flyway migration and exercise real HTTP endpoints, session persistence, shared progress, stale writes and idempotent retries. Frozen reference scenarios compare Java behavior with the original TypeScript engine, including the six completed puzzles and Unicode cipher examples.

H2 does not replace a real PostgreSQL integration check. The test profile is for automated verification only and H2 is not packaged in the production JAR.

The executable JAR is generated under target/. Build and run with JDK 21 for the configured project baseline.

## Live updates (SSE)

GET /api/games/events streams anonymous shared-campaign notifications. Commands remain HTTP POSTs. Notifications are delivered after transaction commit; heartbeats keep idle streams alive. Reconnecting clients must fetch current state, since events are hints and are not replayed. Proxies must disable buffering for this endpoint. This in-memory broadcaster supports one backend instance.

Each command includes the game revision and cycle identifier the player last received. If another action has changed the game, the server returns 409 without applying the command. The player keeps their draft and can review the updated state before trying again. If the connection fails instead, the command may already have been saved, so the client checks the history without automatically sending it again.

## Master time-loop reset

From this backend project folder, with Java running:

```powershell
powershell -File .\scripts\reset-game.ps1
```

Type `REINICIAR` to reset all players. Use `-Port 8081` if the backend uses another port. The script reads `master.reset-token` from the ignored local `.env.properties`; never put this key in Angular or commit it. An empty/unconfigured key disables the endpoint. Restart Java once after adding the key and endpoint; subsequent time-loop resets do not need a restart.

POST /api/master/reset requires both a loopback caller and the X-Master-Token header. The reset restores initial links, modes, keys and histories, and sends an SSE notification after commit. Each reset assigns a new cycle identity so in-flight commands from the old cycle are rejected and browser drafts are cleared. Puzzle definitions and old private session records remain intact. A failed HTTP response can be ambiguous: inspect the game before trying again.

## Impossible Form puzzle

`h_colinas` uses `encodeImpossibleForm`. The cube contains A–Z and space; coordinate rotation is (X,Y,Z) -> (Y,Z,X). Lowercase ASCII and accents normalize to uppercase unaccented letters; other symbols pass through. Three projections restore normalized input. Answer: PROJEÇÃO. Required route: chma_vva -> h_colinas. The inscription TTZZZQQQQBBMMMGGGGAAQQQ produces TZQBMGAQ at the flame and PROJECAO at the destination.

`current-route-solutions.json` contains tested inscriptions for all ten current puzzle routes. `ImpossibleFormTests` covers the cube and current routes. Historical migration snapshots use the frozen `legacy-computers.json` catalog so their old expectations remain meaningful; they do not validate the current campaign catalog.

## Abyss Carriers puzzle

`sr_grdabs` uses `encodeAbyssCarriers` with the answer TRAVESSIA and route inno_m1nvl -> chma_vva -> sr_grdabs. Each ASCII-letter word is processed in pairs: (a,b) maps to (b,(b-a) mod 26); an odd final letter advances by one. Separators and non-ASCII symbols are preserved. The carrier letter keeps its case; the encoded distance follows the first letter's case.

Examples: PORTA -> OZTCB, BPYRZ -> PORTA, TRAVESSIA -> RYVVSOIQB, CTFAMEKSZ -> TRAVESSIA. This cipher affects the solutions for sr_grdabs, tec_la, grd_s0nhadr, and sultao_d. AbyssCarriersTests checks the examples, all 676 pairs, word boundaries, and rejection of local/wrong-route solutions. Current-route tests verify the ten complete paths and success messages.

## Crawling Chaos puzzle

`caosra_st` uses `encodeCrawlingChaos`. Each word forms a circle: skip the first letter, remove the second, and continue counting from the next surviving letter. The output follows the removal order. Letters keep their case, and the cipher restarts for each A–Z/a–z run. Spaces and other characters stay in place.

For example, `PORTA` becomes `OTPAR`, while `RPAOT` produces `PORTA`. The puzzle answer is `ENGANO`, which requires `NEANOG` at the final terminal. Along the full route, `h_colinas → tec_la → caosra_st`, enter `NSMQAN` at `h_colinas`. The awake dialogue includes the mechanical clue, and the full riddle and solution are in the [frontend cipher guide](../enigmas-pc/README.md#caosra_st--the-crawling-chaos-cipher).

This change also affects the route to `sultao_d`. Both updated inscriptions are covered by the current route tests. `CrawlingChaosTests` checks the supplied examples, short and repeated-letter words, word boundaries, and incorrect routes.

## Primordial Source puzzle

`fnt_primdal` uses `encodePrimordialSource`. The normal alphabet maps to `AZBYCXDWEVFUGTHSIRJQKPLOMN`, built by alternating letters from its beginning and end. The substitution preserves case and leaves characters outside A–Z/a–z unchanged.

The answer is `RETORNO`, produced by `RINXRZX`. Along the required route, `chma_vva → tec_la → fnt_primdal`, enter `RRXXXIIIIZZNNNRRRRXX` at `chma_vva`. The full riddle and worked examples are in the [frontend cipher guide](../enigmas-pc/README.md#fnt_primdal--the-primordial-source-cipher).

The route to `sultao_d` also passes through this cipher, so its inscription has been updated. `PrimordialSourceTests` checks the full alphabet, the supplied examples, case handling, and incomplete routes. The current route tests cover all ten puzzle solutions.

## Trance dialogue

Each computer has six ordered `trancePhrases` in the private campaign catalog. `transe` starts a sequence at that computer. The first line is due after two seconds; the next five follow at two-second intervals. Once finished, the terminal stays in trance until a normal player message starts another round.

While a sequence is running, ordinary messages and repeated `transe` commands are ignored without changing history or revision. Control commands still work. `acordado`, a propagated `dormindo`, and the master reset cancel pending lines. `limpa` only clears history. Incoming cipher transmissions stop at a terminal in trance and never trigger its dialogue.

`TranceScheduler` checks the shared game every 250 ms. `GameSessionService` locks the session row, advances due lines, saves the snapshot, and notifies SSE listeners after commit. Each terminal stores its next line and deadline in the optional `trance` field. Delays never cause a burst of catch-up messages. The browser only displays the shared state; reloading or closing a tab does not create or cancel a sequence.

Automated tests normally disable the scheduler with `game.trance.enabled=false` and advance time explicitly. A separate integration test enables the real scheduler against its own H2 database. Tests cover all 60 lines, messages sent mid-sequence, replay, interruption, reset, SSE delivery, and UI rendering. The historical parity fixtures include the new `transe` entry in the command list.
