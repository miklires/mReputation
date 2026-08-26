package io.github.miklires.mreputation.api;
import io.github.miklires.mreputation.ReputationEntry;
import java.util.*;
public interface ReputationAPI {
 int get(UUID playerId);
 List<ReputationEntry> history(UUID playerId);
 boolean change(UUID playerId,String playerName,int delta,String actor,String reason);
 boolean set(UUID playerId,String playerName,int value,String actor,String reason);
}
