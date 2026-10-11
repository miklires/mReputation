package io.github.miklires.mreputation.api;

public enum ReputationVoteResult {
    SUCCESS,
    DISABLED,
    NEGATIVE_DISABLED,
    SELF_VOTE,
    NOT_ENOUGH_PLAYTIME,
    ACCOUNT_TOO_NEW,
    EVIDENCE_REQUIRED,
    RECIPROCAL_VOTE,
    COOLDOWN,
    DAILY_LIMIT,
    ALREADY_VOTED,
    REJECTED,
    UNAVAILABLE
}
