<div align="center">
  <h1>mReputation</h1>
  <p>Transparent, auditable player reputation for Minecraft communities.</p>
  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mReputation"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/project/mreputation"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
    <a href="https://discord.gg/pes25cnWKy"><img alt="Discord" src="https://tr7zw.github.io/uikit/social_buttons_icon/Discord-Button-64.png"></a>
  </p>
  <p>
    <a href="https://bstats.org/plugin/bukkit/mReputation/33360"><img alt="bStats" src="https://img.shields.io/badge/bStats-33360-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mReputation/releases"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mReputation?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## What it does

- Starts players at a configurable reputation value (500 by default).
- Lets players inspect their own or another player's score.
- Records timestamp, actor, reason, delta, and resulting value for every change.
- Restricts large positive grants above a configurable threshold to prevent inflation.
- Runs a configurable console command when a non-exempt player reaches the minimum.
- Exposes a Bukkit Services API and cancellable `ReputationChangeEvent`.

## Requirements

- Java 25
- Paper, Purpur, or Folia 26.2

## Install

1. Put `mReputation-1.0.0.jar` in `plugins`.
2. Start the server once.
3. Review `plugins/mReputation/config.yml`, especially `zero-action.command`.

Data is stored locally in `data.yml`. Writes run on a dedicated executor and are flushed during shutdown.

## Commands and permissions

- `/reputation show [player]` — `mreputation.command.show` / `.show.other`
- `/reputation add <player|uuid> <points> [reason]`
- `/reputation remove <player|uuid> <points> [reason]`
- `/reputation set <player|uuid> <points> [reason]` — modify commands use `mreputation.command.modify`
- `/reputation history <player|uuid>` — `mreputation.command.history`
- `/reputation reload` — `mreputation.command.reload`
- `mreputation.exempt.zero` prevents the automatic zero action for an online player.

Offline players can always be addressed by UUID. Names are resolved only from online players and Paper's cache, avoiding a blocking profile lookup.

## Telemetry and updates

mReputation uses anonymous [bStats metrics](https://bstats.org/plugin/bukkit/mReputation/33360). Disable them with `metrics.enabled: false`. Scores, history, UUIDs, names, and reasons are never collected.

Set `updates.enabled: false` to disable version checks.

## Build

```bash
./gradlew clean build
```

Licensed under the MIT License.
