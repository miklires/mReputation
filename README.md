<div align="center">
  <h1>mReputation</h1>
  <p>Transparent, auditable reputation for Minecraft communities.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Available for Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Available for Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Available for Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mReputation"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/plugin/mreputation"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
    <a href="https://discord.gg/pes25cnWKy"><img alt="Discord" src="https://tr7zw.github.io/uikit/social_buttons_icon/Discord-Button-64.png"></a>
  </p>
  <p>
    <a href="https://bstats.org/plugin/bukkit/mReputation/33360"><img alt="bStats 33360" src="https://img.shields.io/badge/bStats-33360-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mReputation/releases"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mReputation?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## Features

- Configurable starting score and hard minimum/maximum bounds.
- Five ready-to-use reputation tiers with custom MiniMessage names and optional commands when entering a tier.
- Self/other score lookup, highest and lowest leaderboards, tier list and paginated audit history.
- Audited add, remove, set and reset operations with actor, reason, timestamp, delta and resulting value.
- Configurable positive-gain reduction above a threshold, matching social-credit-style moderation workflows.
- Atomic local YAML storage, coalesced write bursts, a last-known backup and recovery from an invalid primary file.
- Optional minimum-score console action, disabled by default and executed only on a real threshold crossing.
- English messages by default with bundled `ru_RU` localization.
- PlaceholderAPI placeholders, Bukkit Services API and cancellable change events.
- Paper, Purpur and Folia scheduling support plus a non-invasive Modrinth update check.

## Requirements

- Java 25
- Paper, Purpur or Folia 26.2
- PlaceholderAPI 2.12.3 or newer is optional

## Quick start

1. Put `mReputation-1.1.0.jar` in `plugins/` and start the server.
2. Review `plugins/mReputation/config.yml`; the generated defaults are safe to use immediately.
3. Give moderators `mreputation.command.modify` and `mreputation.command.history.other`.
4. Use `/rep show`, `/rep tiers`, and `/rep top` to inspect the system.

The default score is 500. Positive grants above 500 are reduced to one point per action, so a high score represents multiple recorded contributions instead of one oversized command.

## Commands

| Command | Purpose | Permission |
|---|---|---|
| `/rep show [player]` | Show score and tier | `mreputation.command.show[.other]` |
| `/rep top [page]` | Highest recorded scores | `mreputation.command.leaderboard` |
| `/rep bottom [page]` | Lowest recorded scores | `mreputation.command.leaderboard` |
| `/rep tiers` | List configured tiers | `mreputation.command.tiers` |
| `/rep history [player] [page]` | Show newest audit entries first | `mreputation.command.history.self` / `.other` |
| `/rep add <player> <points> <reason>` | Add reputation | `mreputation.command.modify` |
| `/rep remove <player> <points> <reason>` | Remove reputation | `mreputation.command.modify` |
| `/rep set <player> <value> <reason>` | Set a bounded value | `mreputation.command.modify` |
| `/rep reset <player> <reason>` | Restore the configured default | `mreputation.command.modify` |
| `/rep reload` | Validate and reload configuration/messages | `mreputation.command.reload` |

`/reputation`, `/rep`, and `/sc` are equivalent. UUIDs can be used for offline players. Names resolve only from online players or Paper's local cache, so commands never block on a remote profile lookup.

## Configuration and safety

Configuration values are checked on startup and reload. Invalid languages, limits, page sizes, IDs and dangerous automatic commands fall back to safe values. Reasons are required for moderator changes and capped at 160 characters; names, actors and persisted history are bounded and control characters are removed.

Tier `enter-commands` support `{player}`, `{uuid}`, and `{value}`. They run only when entering a different tier and are empty by default. `zero-action.enabled` is also false by default. Test all configured commands before enabling automatic actions.

Existing `data.yml` installations continue working. Saves use `data.yml.tmp` followed by an atomic replacement where supported, and keep `data.yml.bak`. Rapid changes collapse into the newest full snapshot instead of creating an unbounded disk-write queue.

## PlaceholderAPI

- `%mreputation_value%` — numeric score
- `%mreputation_tier%` or `%mreputation_tier_id%` — stable tier ID
- `%mreputation_tier_display%` — plain-text tier display name

## API

Obtain `ReputationAPI` from Bukkit's Services Manager. It provides score/tier reads, immutable history, bounded leaderboards, changes, sets and resets. `ReputationChangeEvent` is cancellable. Mutation calls fire a Bukkit event and should be made from a server-owned execution context appropriate for Paper or Folia.

## Telemetry and updates

mReputation sends anonymous server metrics to [bStats project 33360](https://bstats.org/plugin/bukkit/mReputation/33360). Scores, UUIDs, names, history and reasons are never collected. Set `metrics.enabled: false` to opt out.

The update check only reports a newer compatible Modrinth version. It never downloads or installs files and can be disabled with `updates.enabled: false`.

## Build

```bash
./gradlew clean build
```

The distributable file is `build/libs/mReputation-1.1.0.jar`.

[Report an issue](https://github.com/miklires/mReputation/issues) · [View source](https://github.com/miklires/mReputation) · [Join Discord](https://discord.gg/pes25cnWKy)

Licensed under the MIT License.
