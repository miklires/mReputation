package io.github.miklires.mreputation.api;

import java.util.UUID;

public record ReputationStanding(UUID playerId, String playerName, int value, ReputationTier tier) { }
