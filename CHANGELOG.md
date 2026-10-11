# Changelog

## 1.2.0 - 2026-10-11

- Added an opt-in player-voting API for mProfile with positive and separately controlled negative votes.
- Added evidence, minimum playtime/account age, cooldown, daily unique-target limits, and reciprocal-vote protection.
- Stored votes and reputation history in one coalesced atomic YAML snapshot with backup recovery.
- Added capability methods so integrations hide voting controls while the feature is disabled.
- Added policy tests for endorsements, duplicates, cooldowns, and abuse protections.

## 1.1.1 - 2026-10-10

- Added Minecraft 26.3 compatibility and verified the clean build and automated test suite against Paper API 26.3.

## 1.1.0 - 2026-09-03

### Added

- Configurable reputation tiers and optional tier-entry commands.
- Highest/lowest leaderboards, tier listing, paginated history and reset command.
- PlaceholderAPI value and tier placeholders.
- Leaderboard, tier and reset methods in the Bukkit Services API.
- Configuration validation and fallback defaults.

### Changed

- English remains the default language; Russian stays selectable with `language: ru_RU`.
- Moderator changes now require a bounded reason and positive add/remove amounts.
- Update checks now compare numeric semantic versions.
- Automatic minimum-score actions are disabled by default and trigger only when crossing the minimum.

### Fixed

- Prevented integer overflow from extreme reputation deltas.
- Escaped dynamic MiniMessage values and restricted locale file selection.
- Replaced unbounded full-file write queues with coalesced atomic saves and backup recovery.
- Bounded loaded names, actors, reasons, history, leaderboard sizes and configured commands.

## 1.0.0 - 2026-08-26

- Added configurable player reputation, audited modification history and limits.
- Added optional minimum-score action, offline UUID support and Bukkit Services API.
