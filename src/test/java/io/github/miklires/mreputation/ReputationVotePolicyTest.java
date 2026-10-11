package io.github.miklires.mreputation;

import io.github.miklires.mreputation.api.ReputationVoteResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReputationVotePolicyTest {
    private static final long DAY = 86_400_000L;
    private final UUID voter = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final long now = 10 * DAY;
    private final ReputationVotePolicy policy = new ReputationVotePolicy(true, false, 72_000,
            7 * DAY, 5, DAY, 3, true);

    @Test void acceptsQualifiedEndorsement() {
        assertEquals(ReputationVoteResult.SUCCESS,
                policy.validate(voter, target, 72_000, now - 8 * DAY, 1, "Helpful", null, false, 0, now));
    }

    @Test void rejectsUnsafeVotingPatterns() {
        assertEquals(ReputationVoteResult.SELF_VOTE,
                policy.validate(voter, voter, 72_000, now - 8 * DAY, 1, "Helpful", null, false, 0, now));
        assertEquals(ReputationVoteResult.NEGATIVE_DISABLED,
                policy.validate(voter, target, 72_000, now - 8 * DAY, -1, "Reason", null, false, 0, now));
        assertEquals(ReputationVoteResult.RECIPROCAL_VOTE,
                policy.validate(voter, target, 72_000, now - 8 * DAY, 1, "Helpful", null, true, 0, now));
        assertEquals(ReputationVoteResult.DAILY_LIMIT,
                policy.validate(voter, target, 72_000, now - 8 * DAY, 1, "Helpful", null, false, 3, now));
    }

    @Test void requiresMatureAccountPlaytimeAndEvidence() {
        assertEquals(ReputationVoteResult.NOT_ENOUGH_PLAYTIME,
                policy.validate(voter, target, 71_999, now - 8 * DAY, 1, "Helpful", null, false, 0, now));
        assertEquals(ReputationVoteResult.ACCOUNT_TOO_NEW,
                policy.validate(voter, target, 72_000, now - DAY, 1, "Helpful", null, false, 0, now));
        assertEquals(ReputationVoteResult.EVIDENCE_REQUIRED,
                policy.validate(voter, target, 72_000, now - 8 * DAY, 1, "no", null, false, 0, now));
    }

    @Test void enforcesOneVoteAndChangeCooldown() {
        assertEquals(ReputationVoteResult.ALREADY_VOTED,
                policy.validate(voter, target, 72_000, now - 8 * DAY, 1, "Helpful",
                        new ReputationVotePolicy.Previous(1, now - 2 * DAY), false, 0, now));
        assertEquals(ReputationVoteResult.COOLDOWN,
                policy.validate(voter, target, 72_000, now - 8 * DAY, 1, "Changed",
                        new ReputationVotePolicy.Previous(-1, now - 1_000), false, 0, now));
    }
}
