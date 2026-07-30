# DailyQ — Design Spec (v0.1)

- Date: 2026-07-30
- Status: `active`
- Autonomy: `autonomous`
- Repository (to be created at scaffold): `carmelosantana/minecraft-daily-q`
- Maven group / artifactId: `org.xpfarm` / `daily-q`
- Java package: `org.xpfarm.dailyq`

DailyQ is the first slice of a broader questing system for `play.xpfarm.org`. v0.1 delivers
daily login rewards, a small rotating set of daily tasks, a claim mailbox, and a "today"
greeting — with event seams the future questing framework, leaderboards, and a duel board can
subscribe to later.

## 1. Scope boundary

**In v0.1:**
1. Daily login rewards (28-day looping streak calendar) + a "today's rewards" join message.
2. Daily tasks (3/day, server-wide shared set) with auto-tracked progress and a completion bonus.
3. Reward delivery via a claim mailbox.
4. Farmers Market integration at the **economy level** (rewards are raw materials / diamonds /
   consumables that feed the market economy) and **convention level** (SQLite persistence,
   Cumulus Bedrock forms) — not code coupling.

**Deferred to Phase 2+ (recorded, not built):**
- Quest-token soft currency + redemption shop. The reward data model reserves room for it.
- Market-activity task types ("sell N at the Farmers Market") — needs an FM sale-event API,
  which FM does not expose today.
- Minigames / PvP "duel a friend" board and leaderboards.
- Crediting Farmers Market's diamond ledger directly (v0.1 grants physical items only; FM
  registers no public Bukkit service today).
- The broader questing framework itself — only the event seams exist in v0.1.

## 2. Player experience

On the first join of a server-day, DailyQ shows a **Today message**: streak status + today's
claimable login reward, and the 3 daily tasks with current progress. Rendered as a chat card on
Java and a toast + form entry on Bedrock, with a one-click path into the claim mailbox.

Tasks tick automatically from normal play — no "accept quest" step. Each day's set is one
easy-guaranteed + one medium + one stretch task. Completing all 3 grants a **completion bonus
larger than the sum of the per-task rewards**, so players finish the set rather than cherry-pick.
Everything claimable lands in the **mailbox**, so no reward is ever lost to a full inventory.

### Design principles carried from research

Sourced from the DailyQ deep-research pass (ESO endeavors, Genshin commissions, WoW
callings/rested-XP, Destiny seasonal challenges, gacha login calendars):

- **Bounded task count (3/day), forever** — a finishable list, not a treadmill.
- **Completion bonus > per-task reward** — reward finishing the set (Genshin +20 pattern).
- **Auto-tracked from normal play** — no pickup friction (ESO/Destiny).
- **Milestone spikes, gentle drip between** — day 7 / 14 / 28 feel like events (gacha rhythm).
- **Humane anti-burnout, day one:** streak forgiveness (one miss never resets; two consecutive
  misses step down, never to zero), one retroactive make-up day, and **cosmetic/material rewards
  not power** so skipping never makes a player weaker than others.

## 3. Login streak

A 28-day looping calendar. Rising drip between milestone spikes at **day 7 / 14 / 28**. On join,
`StreakService` computes the new streak value from `last_login` and the current server-day:

- Same day already claimed → no change.
- Next consecutive day → streak + 1.
- One missed day → streak held (forgiveness), Today message notes the missed day is claimable
  once via the make-up mechanic.
- Two or more consecutive missed days → streak steps down by one tier, never below the floor.

The calendar's 28 reward entries are config-driven payloads. Milestone days carry the largest
material/cosmetic rewards.

## 4. Daily tasks

Exactly 3 tasks per server-day, identical for every player (date-seeded selection from the pool,
so `DailyRotation` is deterministic given the date — no per-player task state needed for
selection). Archetypes, all vanilla-trackable in v0.1:

| Archetype | Tracked via | Example |
|---|---|---|
| Gather / mine | `BlockBreakEvent` | Mine 64 iron ore |
| Farm / harvest | `BlockBreakEvent` (crop age) | Harvest 128 wheat |
| Craft | `CraftItemEvent` | Craft any 10 tools |
| Kill hostiles | `EntityDeathEvent` | Kill 20 hostile mobs |
| Place blocks | `BlockPlaceEvent` | Place 100 blocks |
| Villager trade | `InventoryClickEvent` (merchant) | Complete 5 villager trades |

Each day's set = one easy + one medium + one stretch, chosen by tier weight.

## 5. Architecture / components

- `DailyQPlugin` — bootstrap; wires services, listeners, commands, storage.
- **Task engine** — `TaskDefinition` (archetype + target + count + tier), config-driven
  `TaskPool`, `DailyRotation` (date-seeded → today's 3 shared tasks), `TaskProgressTracker`
  (event listeners increment per-player progress; emits completion).
- **Streak engine** — `StreakService` (forgiveness + make-up logic), `LoginCalendar` (28-entry
  reward table).
- **Reward system** — `Reward` interface; `ItemReward` impl for v0.1 (raw materials, diamonds,
  consumables). `TokenReward` is the Phase-2 slot-in. `RewardMailbox` holds pending rewards per
  player until claimed; partial claims leave the remainder pending.
- **Delivery / UI** — `TodayMessage` (join card), `DailyHubUI` + `MailboxUI` rendered as a chest
  GUI on Java and a Cumulus form on Bedrock (mirrors Farmers Market). Floodgate-aware identity so
  streaks and progress survive Java↔Bedrock for linked accounts.
- **Persistence** — SQLite (matching FM's `sqlite-jdbc` stack and async executor pattern; driver
  package not relocated when shaded).
- **Framework seam** — fires `DailyTaskCompletedEvent` and `DailyStreakClaimedEvent` so the
  future questing system, leaderboards, and duel board can subscribe without DailyQ depending on
  them.

## 6. Commands (menu-first; `/daily` root)

| Command | Who | Purpose |
|---|---|---|
| `/daily` | all | Open the DailyQ hub (tasks + streak + mailbox) |
| `/daily tasks` | all | Today's 3 tasks + progress |
| `/daily streak` | all | Login calendar + streak status |
| `/daily claim` | all | Open / claim the reward mailbox |
| `/daily admin reload` | admin | Reload configuration |
| `/daily admin grant <player> <reward>` | admin | Grant a reward (support / testing) |
| `/daily admin reset <player>` | admin | Reset a player's daily / streak state |

No player action requires typing a command — every path has a UI equivalent (accessibility +
Bedrock parity). Every player command is **no-argument, player-runnable, and opens a UI**, and
must behave identically whether typed by the player or dispatched by another plugin on the
player's behalf (see §13). Permission checks are enforced at execution time, not just at command
registration.

## 7. Permissions

| Node | Default | Gates |
|---|---|---|
| `dailyq.use` | true | All player commands and UI |
| `dailyq.admin` | op | `/daily admin ...` subcommands |

## 8. Configuration (`config.yml`)

- `tasks.pool` — list of archetype definitions (archetype, target material/entity, count, tier).
- `tasks.per-day` — default `3`.
- `tasks.completion-bonus` — reward payload granted when all daily tasks complete.
- `login.calendar` — 28 entries, each a reward payload.
- `login.milestone-days` — default `[7, 14, 28]`.
- `streak.forgiveness-misses` — consecutive misses tolerated before step-down (default `1`).
- `streak.make-up-enabled` — default `true`.
- `reset.hour` + `reset.timezone` — daily rollover boundary.
- `messages.*` — toggles for the Today card and toasts.
- `storage.db-file` + `storage.busy-timeout-ms` — mirror FM defaults.

## 9. Events

**Consumed:** `PlayerJoinEvent` (Today message + streak compute), `BlockBreakEvent`,
`BlockPlaceEvent`, `CraftItemEvent`, `EntityDeathEvent`, `InventoryClickEvent` (villager trade +
UI interactions).

**Fired:** `DailyTaskCompletedEvent`, `DailyStreakClaimedEvent` — framework seams for later
phases.

## 10. Persistence

SQLite. Tables:

- `player_state` — `player_uuid`, `streak`, `last_login_day`, `make_up_used`, `last_claim_day`.
- `task_progress` — `server_day`, `task_id`, `player_uuid`, `count`, `claimed`.
- `mailbox` — `id`, `player_uuid`, `reward_payload`, `created_day`, `claimed`.

Reward payloads serialize items via a Bukkit item codec (mirrors FM's `BukkitItemCodec`).

## 11. External integrations

None. No Ollama, Umami, or other outside-service calls in v0.1.

## 12. Acceptance checks (testable)

1. First daily join renders the Today message with 3 tasks + streak status.
2. Consecutive-day logins increase the streak.
3. One skipped day does not reset the streak; two consecutive skips step it down (never to zero).
4. The make-up day is claimable exactly once.
5. A tracked action increments only the matching task; completing all 3 grants the completion
   bonus to the mailbox.
6. Claiming from the mailbox moves items to inventory; on a full inventory, the remainder stays
   pending.
7. All online players receive an identical task set for a given `server_day` (date-seeded).
8. A Bedrock player sees Cumulus form equivalents; a Java player sees the chest GUI.
9. Streak, task progress, and mailbox contents survive a server restart.
10. Each player command opens its UI correctly when dispatched by another plugin **as the
    player** (Pizza `run-as: player`), not only when typed in chat.
11. A player lacking `dailyq.admin` is refused `/daily admin ...` even when it is dispatched on
    their behalf.

## 13. Menu / Pizza integration

DailyQ is designed to be driven by the **Pizza** menu plugin (`xpfarm-pizza`) so younger players
can reach every daily-quest action from touch buttons instead of typing — **without DailyQ
writing any Pizza-specific code and without either plugin depending on the other**.

Pizza is a config-driven front end: a button carries a `command:` string that Pizza dispatches
with `run-as: player | console | player-elevated`, gated by a `command-allowlist` of command
roots (fail-closed, because the audience is children). It already drives Farmers Market this way
(`run-as: player`, `command: market`). DailyQ inherits the identical pattern for free by holding
to this contract:

- **Command surface is menu-shaped.** `/daily`, `/daily tasks`, `/daily streak`, `/daily claim`
  are each no-argument, player-runnable, and open a UI. A Pizza button maps one-to-one onto each.
- **Dispatch-safe.** Each command resolves the sender as the acting player and produces the same
  result whether typed or dispatched by Pizza on the player's behalf.
- **Permission-safe.** `dailyq.use` / `dailyq.admin` are enforced at execution, so a child
  running a `run-as: player` button can never reach an admin action — defense in depth on top of
  Pizza's allowlist.
- **Bedrock/Java chaining is normal.** A Pizza Cumulus button that dispatches `/daily claim`
  chains into DailyQ's own Cumulus form; the Java chest menu chains chest→chest. No special
  handling needed.

**The Pizza side is a config edit, not code, and is deferred until DailyQ's commands exist.**
Once DailyQ ships its command surface, wiring is: add root `daily` to Pizza's `command-allowlist`
and add buttons (e.g. a "Daily Quests" button with `run-as: player`, `command: daily`) to Pizza's
`config.yml`. Adding a plugin to the Pizza menu is explicitly "a config edit, not a release."
This is tracked as a cross-plugin follow-up (see §1 dependencies / limitations), not DailyQ v0.1
code.

## 14. Known limitations

- No quest-token currency or redemption shop (Phase 2; data model reserves room).
- No market-activity task types (Phase 2; needs an FM sale-event API).
- No minigames / duel board (Phase 2+).
- No direct crediting of FM's diamond ledger (v0.1 grants physical items only).
- Broader questing framework: only event seams exist, not the framework.

No gates are intentionally withheld — DailyQ is a fully active plugin that runs the whole
lifecycle.
