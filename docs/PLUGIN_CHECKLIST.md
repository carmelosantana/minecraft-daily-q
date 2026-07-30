# New or Edited Plugin Checklist

Copy this file for one plugin and replace every `<...>` field. Leave an unchecked box with a short explanation when a gate is not complete; do not silently remove inapplicable checks.

- Plugin name: `DailyQ`
- Slug: `daily-q`
- Repository: `carmelosantana/minecraft-daily-q`
- Owner: `Carmelo Santana`
- Target version: `0.1.0`
- Paper version: `26.1.2 build 74`
- Java version: `25`
- Updater destination: `daily-q.jar`
- External services: `none`
- Status: `active`
- Autonomy: `autonomous`

Gate 1 completed `2026-07-30`. Design spec: `docs/superpowers/specs/2026-07-30-daily-q-design.md`.

## 1. Scope

- [x] Status is explicitly recorded as active, experimental, or excluded.
- [x] Purpose, commands, events, permissions, configuration, persistence, and acceptance checks are defined.
- [x] Known limitations and any intentionally withheld gates are recorded.

### Player-facing purpose

The first slice of a broader questing system for `play.xpfarm.org`. On join, DailyQ greets the
player with a "today" card: their login-streak status and today's claimable reward, plus 3 daily
tasks (shared server-wide) that tick automatically from normal play — mining, farming, crafting,
killing mobs, placing blocks, trading with villagers. Finishing all 3 grants a completion bonus.
Rewards are real items — raw materials, diamonds, consumables — delivered to a claim mailbox so
nothing is ever lost to a full inventory, and those materials feed back into the Farmers Market
economy. Humane by design: streak forgiveness, a make-up day, and material/cosmetic rewards
rather than power, so no one is punished for missing a day. Java players use a chest GUI; Bedrock
players use Cumulus forms.

### Naming chain

Established here; `minecraft-plugin-scaffold` implements and verifies it, and no later gate may
rename a link.

| Link | Value |
|---|---|
| Slug | `daily-q` |
| Repository | `carmelosantana/minecraft-daily-q` |
| Maven group | `org.xpfarm` |
| Maven `artifactId` | `daily-q` |
| Java package | `org.xpfarm.dailyq` |
| Releasable JAR | `daily-q-0.1.0.jar` |
| Updater destination | `daily-q.jar` |
| `plugin.yml` `name` | `DailyQ` |

### Commands

Menu-first (`/daily` root). No player action requires typing a command — every path has a UI
equivalent for accessibility and Bedrock parity. Every player command is no-argument,
player-runnable, opens a UI, and behaves identically whether typed or **dispatched by another
plugin on the player's behalf** — this is what lets the Pizza menu drive DailyQ with no DailyQ
code (see Menu / Pizza integration below). Permission checks are enforced at execution time.

| Command | Arguments | Who | Purpose |
|---|---|---|---|
| `/daily` | — | all | Opens the DailyQ hub UI (tasks + streak + mailbox). |
| `/daily tasks` | — | all | Today's 3 tasks and progress. |
| `/daily streak` | — | all | Login calendar and current streak status. |
| `/daily claim` | — | all | Opens / claims the reward mailbox. |
| `/daily admin reload` | — | admin | Reloads configuration without a server hot reload. |
| `/daily admin grant` | `<player> <reward>` | admin | Grants a reward payload (support / testing). |
| `/daily admin reset` | `<player>` | admin | Resets a player's daily and streak state. |

### Events

**Consumed:** `PlayerJoinEvent` (today message + streak compute), `BlockBreakEvent` (mine /
harvest), `BlockPlaceEvent` (place), `CraftItemEvent` (craft), `EntityDeathEvent` (kill),
`InventoryClickEvent` (villager trade + UI interactions).

**Fired:** `DailyTaskCompletedEvent`, `DailyStreakClaimedEvent` — framework seams so the future
questing system, leaderboards, and duel board can subscribe without DailyQ depending on them.

### Permissions

| Node | Default | Gates |
|---|---|---|
| `dailyq.use` | true | All player commands and UI. |
| `dailyq.admin` | op | `/daily admin ...` subcommands. |

### Configuration

| Key | Type | Default | Notes |
|---|---|---|---|
| `tasks.pool` | list | archetype set | Each: archetype, target material/entity, count, tier. |
| `tasks.per-day` | int | `3` | Bounded on purpose; not a treadmill. |
| `tasks.completion-bonus` | reward payload | — | Granted when all daily tasks complete. |
| `login.calendar` | list(28) | reward payloads | Looping 28-day streak calendar. |
| `login.milestone-days` | list(int) | `[7, 14, 28]` | Milestone reward spikes. |
| `streak.forgiveness-misses` | int | `1` | Consecutive misses tolerated before step-down. |
| `streak.make-up-enabled` | bool | `true` | Allows one retroactive make-up claim. |
| `reset.hour` | int | `0` | Daily rollover hour. |
| `reset.timezone` | string | server tz | Rollover timezone. |
| `messages.*` | bool | — | Toggles for the Today card and toasts. |
| `storage.db-file` | string | `daily-q.db` | SQLite database filename. |
| `storage.busy-timeout-ms` | int | mirror FM | SQLite busy timeout. |

### Persistence

SQLite (matching Farmers Market's `sqlite-jdbc` stack + async executor; driver package not
relocated when shaded). Tables:

- `player_state` — `player_uuid`, `streak`, `last_login_day`, `make_up_used`, `last_claim_day`.
- `task_progress` — `server_day`, `task_id`, `player_uuid`, `count`, `claimed`.
- `mailbox` — `id`, `player_uuid`, `reward_payload`, `created_day`, `claimed`.

Reward payloads serialize items via a Bukkit item codec (mirrors FM's `BukkitItemCodec`).

### Dependencies

- **Hard:** Paper 26.1.2 build 74 API.
- **Soft:** Floodgate (Bedrock identity resolution), Geyser (Cumulus forms for Bedrock UI). Both
  are runtime-optional — Java-only behavior must degrade gracefully if absent.
- **No hard dependency on Farmers Market.** v0.1 integration is economy-level (item rewards feed
  the market) and convention-level (SQLite, Cumulus), not code coupling. Load order: none required.
- **No dependency on Pizza (`xpfarm-pizza`), in either direction.** Pizza is a config-driven menu
  front end that dispatches DailyQ's existing player commands via `run-as: player` (the same way
  it already drives Farmers Market's `/market`). DailyQ stays "open enough" simply by keeping its
  player commands no-argument, player-runnable, dispatch-safe, and permission-checked at
  execution — no Pizza-specific code. Wiring the menu (adding root `daily` to Pizza's
  `command-allowlist` and adding buttons to Pizza's `config.yml`) is a Pizza config edit done
  after DailyQ's commands exist; it is a cross-plugin follow-up, not DailyQ v0.1 code.

### External integrations

`none`. No Ollama, Umami, or other outside-service calls in v0.1.

### Acceptance checks

1. First daily join renders the Today message with 3 tasks + streak status.
2. Consecutive-day logins increase the streak.
3. One skipped day does not reset the streak; two consecutive skips step it down, never to zero.
4. The make-up day is claimable exactly once.
5. A tracked action increments only the matching task; completing all 3 grants the completion
   bonus to the mailbox.
6. Claiming from the mailbox moves items to inventory; on a full inventory the remainder stays
   pending.
7. All online players receive an identical task set for a given `server_day` (date-seeded).
8. A Bedrock player sees Cumulus form equivalents; a Java player sees the chest GUI.
9. Streak, task progress, and mailbox contents survive a server restart.
10. Each player command opens its UI correctly when dispatched by another plugin as the player
    (Pizza `run-as: player`), not only when typed in chat.
11. A player lacking `dailyq.admin` is refused `/daily admin ...` even when dispatched on their
    behalf.

### Known limitations

Deferred to Phase 2+ (recorded, not built in v0.1):

- Quest-token soft currency + redemption shop. The reward data model reserves room for it.
- Market-activity task types ("sell N at the Farmers Market") — needs an FM sale-event API,
  which Farmers Market does not expose today.
- Minigames / PvP "duel a friend" board and leaderboards.
- Direct crediting of Farmers Market's diamond ledger (FM registers no public Bukkit service
  today; v0.1 grants physical items only).
- The broader questing framework itself — only the `DailyTaskCompletedEvent` /
  `DailyStreakClaimedEvent` seams exist in v0.1.

Cross-plugin follow-up (not a DailyQ code deliverable): once DailyQ's commands exist, wire the
Pizza menu by adding root `daily` to `xpfarm-pizza`'s `command-allowlist` and adding buttons to
its `config.yml`. This is a Pizza config edit, tracked so it is not forgotten.

No gates are intentionally withheld. DailyQ is a fully active plugin that runs the whole
lifecycle. `plugin.yml` `name` is `DailyQ`; `api-version` will be `'26.1'`.

Gate 2/3 completed `2026-07-30` by `minecraft-plugin-scaffold`. Repository:
`carmelosantana/minecraft-daily-q` (SSH origin, `main`). Files added: `LICENSE`, `pom.xml`,
`src/main/resources/{plugin.yml,config.yml}`, `src/main/java/org/xpfarm/dailyq/DailyQPlugin.java`
(stub), `src/test/java/org/xpfarm/dailyq/PluginDescriptorTest.java`, `.github/workflows/build.yml`,
`README.md`. Verifications: herobrinesystems scan returns exactly one hit (this checklist's own
Gate 3 item — same benign case documented in sibling plugins); artifactId `daily-q` / `plugin.yml`
`name: DailyQ` / version `0.1.0` all agree with the naming chain. Local sanity build
`mvn --batch-mode --no-transfer-progress clean verify` = BUILD SUCCESS, 7/7 descriptor tests
passing, shaded `daily-q-0.1.0.jar` produced (gate 6 remains dev's to record formally).

## 2. Repository

- [x] Repository is `carmelosantana/minecraft-daily-q` with an SSH `origin` and `main` branch.
- [x] Existing user-owned worktree changes were identified and preserved (brand-new repo — none existed).
- [x] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation.
      `rg -n 'herobrinesystems' . --hidden -g '!target/**' -g '!.git/**'` returns exactly one hit:
      this checklist's own Gate 3 verification item, not an identity reference.

## 3. Metadata

- [x] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent.
- [x] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present.
- [x] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented (`README.md`).
- [x] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented.
- [x] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent.
- [x] No secrets committed in source, defaults, tests, logs, history, or documentation.

Gate 4/5/6/7a completed `2026-07-30` by `minecraft-plugin-dev`. Implemented via subagent-driven
development (10 tasks, per-task review, final whole-branch review; ledger:
`.superpowers/sdd/progress.md`). Plan: `docs/superpowers/plans/2026-07-30-daily-q-v0.1.0.md`.
Final: `mvn clean verify` = BUILD SUCCESS, **133/133 tests**; shaded `daily-q-0.1.0.jar` inspected.

## 4. Compatibility

- [x] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '26.1'`, matching the API compiled against.
- [x] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared. Hard: paper-api. Soft: `floodgate` (Bedrock forms). SQLite via `libraries:`. No FM/Pizza code dependency.
- [x] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior. Only `FloodgateBridge` imports `org.geysermc.*`, constructed behind an `isPluginEnabled`+availability guard; every Bedrock path falls back to the Java chest menu when Floodgate is absent. No Java-only chat-input prompts. Runtime: floodgate/Geyser/ViaVersion all started green alongside DailyQ.

## 5. External services

- [x] No external integrations exist in v0.1 — trivially satisfied (no Ollama/Umami/HTTP calls).
- [x] N/A — no external endpoints.
- [x] N/A — startup cannot be affected by an absent external service because there are none. SQLite is local file I/O.

## 6. Tests and build

- [x] Unit tests cover separable logic: config parsing/validation, day-boundary, reward codec, SQLite DAOs, streak rules (table-driven), date-seeded rotation determinism, progress evaluation, mailbox partial-claim, command routing/permissions. 133 tests.
- [x] `PluginDescriptorTest` parses `plugin.yml` and `config.yml` with SnakeYAML and asserts name, main, `String`-typed `api-version`, fully-substituted `version`, the `daily` command, `dailyq.use`/`dailyq.admin`, the `floodgate` softdepend, and the sqlite-jdbc `libraries:` coordinate matching pom.xml.
- [x] `mvn --batch-mode --no-transfer-progress clean verify` succeeds (BUILD SUCCESS, 133/133).
- [x] The shaded `daily-q-0.1.0.jar` and embedded `plugin.yml` were inspected; version resolved to `0.1.0`; no `org/bukkit`, `org/sqlite`, or `org/geysermc` bundled; no `original-*` shipped; no secrets.

## 7. Matrix

- [ ] 7b (full-roster matrix) — **out-of-band, not required for release** (`minecraft-plugin-matrix`, triggered by updater/stack changes). Not run in this dev cycle.
- [ ] 7b — per-plugin fresh-volume behavior recorded separately: deferred to a matrix run.
- [x] 7a: Paper, Geyser, Floodgate, and ViaVersion started successfully together on a fresh disposable Legendary stack; RCON `plugins` showed DailyQ + floodgate + Geyser-Spigot + ViaVersion all green.
- [x] 7a: Commands and config reload exercised over RCON — `/daily` and `/daily tasks` from console gave the graceful player-only rejection (proving registration + execution-time sender/permission checks, i.e. dispatch-safety); `/daily admin reload` reloaded config; SQLite driver loaded at runtime via `libraries:`; plugin enabled cleanly with no exceptions or leaked secrets in the logs.
- [x] N/A — no Ollama/Umami endpoints to negative-path test.

### 7a — behaviors NOT reachable headlessly (gate-12 play-test obligations)

No client attaches to the runtime stack by design, and the RCON test-harness plugin does not exist
yet, so the following must be verified on `play.xpfarm.org` by the team and recorded at gate 12 by
`minecraft-plugin-handoff`:

- **Event→progress crediting under real gameplay**: that `BlockBreak` (MINE/HARVEST), `BlockPlace`,
  `CraftItem`, `EntityDeath` (KILL, incl. HOSTILE/PASSIVE category resolution), and merchant-trade
  clicks actually increment the matching daily task, fire `DailyTaskCompletedEvent`, and grant the
  per-task reward and completion bonus.
- **Login streak on real joins**: `PlayerJoinEvent` computing the streak, granting the calendar
  reward + one-time make-up to the mailbox, firing `DailyStreakClaimedEvent`, and rendering the
  "today" card.
- **What a client renders**: the Java chest menu and the Bedrock Cumulus forms (hub/tasks/streak/
  mailbox), including the mailbox claim depositing items to inventory and leaving partial fits
  pending.

## 8. CI/CD

- [x] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior (`.github/workflows/build.yml`, byte-identical to the standard contract).
- [ ] Successful main Actions run is recorded before tagging. — First `main` run (id `30554542881`) already observed **green** at scaffold time; `minecraft-plugin-release` (gate 8b) formally records the run it tags against.
- [x] Workflow permissions contain no broader access than the documented contract (`contents: write`, nothing more).

## 9. Release

- [ ] Semantic version matches the POM, plugin metadata, and `v<version>` tag.
- [ ] Successful tag Actions run and GitHub release are recorded.
- [ ] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR.
- [ ] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`.

## 10. Updater

- [ ] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin.
- [ ] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass.
- [ ] Updater dry-run uses a disposable directory and never a production plugin directory.
- [ ] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup.

## 11. Deployment

Not a gate. Deployment is updater pickup: a verified release plus a correct manifest entry is all
this lifecycle owes. Leaving this section entirely unticked is the normal resting state and blocks
nothing — not release, not enrolment, not handoff.

- [ ] Enrolment confirmed live and correct: release sound, manifest entry on `origin/main`, gate 10 genuinely completed.
- [ ] Deployment evidence recorded, if and only if an operator relayed some. Otherwise note "enrolled, not known to be deployed" and leave unticked.

## 12. Handoff

- [ ] Current-state documentation refreshed with release, CI, updater, deployment, and local pending state.
- [ ] Known limitations, skipped checks, configuration or migration notes, rollback guidance, and follow-up owner are recorded.
- [ ] Evidence distinguishes source commit, published tag/release, updater state, and deployed state without exposing secrets.
- [ ] Client play-test obligation recorded with a named owner and a target date: `<owner>` / `<date>`.
- [ ] Client play-test outcome recorded once performed, covering Java join, Bedrock join, and any form, inventory, or rendered item behavior this plugin introduces. Leave unchecked with the owner and date above until the team has run it; an unchecked box here does not block a release, but an unrecorded obligation is a gate 12 failure.
- [ ] Public deployment reachability confirmed during that pass: `play.xpfarm.org` reaches the intended Java and Bedrock entry points.
