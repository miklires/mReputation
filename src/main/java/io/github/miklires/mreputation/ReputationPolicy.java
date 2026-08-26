package io.github.miklires.mreputation;
public record ReputationPolicy(int defaultValue, int minimum, int maximum, int positiveThreshold, int aboveThresholdStep) {
 public ReputationPolicy { if(minimum>maximum) throw new IllegalArgumentException("minimum exceeds maximum"); defaultValue=clamp(defaultValue,minimum,maximum); aboveThresholdStep=Math.max(1,aboveThresholdStep); }
 public int change(int current,int requestedDelta){ int delta=requestedDelta; if(current>=positiveThreshold&&delta>aboveThresholdStep)delta=aboveThresholdStep; return clamp(current+delta,minimum,maximum); }
 public int set(int value){return clamp(value,minimum,maximum);}
 private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
