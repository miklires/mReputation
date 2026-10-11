package io.github.miklires.mreputation.api;
import io.github.miklires.mreputation.ReputationEntry;
import java.util.*;
public interface ReputationAPI {
 int get(UUID playerId);
 ReputationTier tier(UUID playerId);
 List<ReputationEntry> history(UUID playerId);
 List<ReputationStanding> leaderboard(int limit, boolean lowestFirst);
 boolean change(UUID playerId,String playerName,int delta,String actor,String reason);
 boolean set(UUID playerId,String playerName,int value,String actor,String reason);
 boolean reset(UUID playerId,String playerName,String actor,String reason);
 default boolean playerVotingEnabled() { return false; }
 default boolean negativePlayerVotingEnabled() { return false; }
 default ReputationVoteResult vote(UUID voterId, String voterName, long voterPlaytimeTicks,
                                   UUID targetId, String targetName, int direction) {
  return vote(voterId, voterName, voterPlaytimeTicks, 0L, targetId, targetName, direction, "");
 }
 default ReputationVoteResult vote(UUID voterId, String voterName, long voterPlaytimeTicks, long voterFirstPlayedMillis,
                                   UUID targetId, String targetName, int direction, String evidence) {
  return ReputationVoteResult.UNAVAILABLE;
 }
}
