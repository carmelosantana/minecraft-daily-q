# DailyQ

Daily quests, login-streak rewards, and a claim mailbox — the first slice of the xpfarm questing
system.

Log in each day for an escalating streak reward, complete a small rotating set of daily tasks that
tick automatically as you play, and collect everything from a claim mailbox so nothing is ever
lost to a full inventory. Rewards are real materials that feed back into the
[Farmers Market](https://xpfarm.org) economy. Designed to be humane: a missed day never wipes your
streak, there is a make-up day, and rewards are materials and cosmetics rather than power — so
skipping a day never leaves you behind.

## Playing

Join at **`play.xpfarm.org`** — the same hostname for Java and Bedrock Edition.

Type `/daily` (or `/dq`, or `/quests`) to open DailyQ: today's tasks, your login streak, and the
reward mailbox. Younger players can reach the same actions from the [Pizza](https://xpfarm.org)
touch menu without typing anything.

## Bedrock and Java

Bedrock players get native Cumulus forms; Java players get a chest-inventory menu with the same
options. Floodgate is optional — when it is absent, DailyQ still enables and serves the chest menu
to everyone.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/daily` | Open DailyQ (tasks, streak, mailbox). Aliases `/dq`, `/quests` | `dailyq.use` (default: everyone) |
| `/daily tasks` | Today's tasks and progress | `dailyq.use` |
| `/daily streak` | Login calendar and streak status | `dailyq.use` |
| `/daily claim` | Open and claim the reward mailbox | `dailyq.use` |
| `/daily admin reload` | Reload the configuration | `dailyq.admin` (default: op) |
| `/daily admin grant <player> <reward>` | Grant a reward (support/testing) | `dailyq.admin` |
| `/daily admin reset <player>` | Reset a player's daily/streak state | `dailyq.admin` |

## Permissions

| Node | Default | Gates |
|---|---|---|
| `dailyq.use` | everyone | Opening DailyQ and using its player commands |
| `dailyq.admin` | op | The `/daily admin ...` subcommands |

## Building

Requires Java 25.

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

## License

AGPL-3.0-or-later. See [LICENSE](LICENSE).
