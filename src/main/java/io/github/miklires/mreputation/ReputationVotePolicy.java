package io.github.miklires.mreputation;

import io.github.miklires.mreputation.api.ReputationVoteResult;

import java.util.UUID;

record ReputationVotePolicy(boolean enabled, boolean allowNegative, long minimumPlaytimeTicks,
                            long minimumAccountAgeMillis, int minimumEvidenceLength, long cooldownMillis,
                            int dailyLimit, boolean blockReciprocal) {
    record Previous(int direction, long timestamp) { }

    ReputationVoteResult validate(UUID voterId, UUID targetId, long playtimeTicks, long firstPlayedMillis,
                                  int direction, String evidence, Previous previous, boolean reciprocal,
                                  long recentUniqueTargets, long now) {
        if (!enabled) return ReputationVoteResult.DISABLED;
        if (voterId == null || targetId == null || voterId.equals(targetId)) return ReputationVoteResult.SELF_VOTE;
        direction = Integer.signum(direction);
        if (direction == 0) return ReputationVoteResult.REJECTED;
        if (direction < 0 && !allowNegative) return ReputationVoteResult.NEGATIVE_DISABLED;
        if (Math.max(0, playtimeTicks) < minimumPlaytimeTicks) return ReputationVoteResult.NOT_ENOUGH_PLAYTIME;
        if (firstPlayedMillis <= 0 || now - firstPlayedMillis < minimumAccountAgeMillis)
            return ReputationVoteResult.ACCOUNT_TOO_NEW;
        if (evidence == null || evidence.length() < minimumEvidenceLength)
            return ReputationVoteResult.EVIDENCE_REQUIRED;
        if (blockReciprocal && reciprocal) return ReputationVoteResult.RECIPROCAL_VOTE;
        if (previous != null && previous.direction() == direction) return ReputationVoteResult.ALREADY_VOTED;
        if (previous != null && now - previous.timestamp() < cooldownMillis) return ReputationVoteResult.COOLDOWN;
        if (recentUniqueTargets >= dailyLimit
                && (previous == null || now - previous.timestamp() >= 86_400_000L))
            return ReputationVoteResult.DAILY_LIMIT;
        return ReputationVoteResult.SUCCESS;
    }
}
