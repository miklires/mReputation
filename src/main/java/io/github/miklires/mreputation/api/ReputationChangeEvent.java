package io.github.miklires.mreputation.api;
import org.bukkit.event.*;
import java.util.UUID;
public final class ReputationChangeEvent extends Event implements Cancellable {
 private static final HandlerList HANDLERS=new HandlerList(); private final UUID playerId; private final int oldValue,newValue; private final String actor,reason; private boolean cancelled;
 public ReputationChangeEvent(UUID playerId,int oldValue,int newValue,String actor,String reason){this.playerId=playerId;this.oldValue=oldValue;this.newValue=newValue;this.actor=actor;this.reason=reason;}
 public UUID getPlayerId(){return playerId;} public int getOldValue(){return oldValue;} public int getNewValue(){return newValue;} public String getActor(){return actor;} public String getReason(){return reason;}
 public boolean isCancelled(){return cancelled;} public void setCancelled(boolean cancelled){this.cancelled=cancelled;} public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}
