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
}
