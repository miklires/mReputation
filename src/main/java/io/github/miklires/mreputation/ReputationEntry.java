package io.github.miklires.mreputation;
public record ReputationEntry(long timestamp, int delta, int value, String actor, String reason) {}
