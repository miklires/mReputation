# Changelog

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
