package io.github.miklires.mreputation;
public record ReputationPolicy(int defaultValue, int minimum, int maximum, int positiveThreshold, int aboveThresholdStep) {
 public ReputationPolicy { if(minimum>maximum) throw new IllegalArgumentException("minimum exceeds maximum"); defaultValue=clamp(defaultValue,minimum,maximum); positiveThreshold=clamp(positiveThreshold,minimum,maximum); aboveThresholdStep=Math.max(1,aboveThresholdStep); }
 public int change(int current,int requestedDelta){ long delta=requestedDelta; if(current>=positiveThreshold&&delta>aboveThresholdStep)delta=aboveThresholdStep; return clamp((long)current+delta,minimum,maximum); }
 public int set(int value){return clamp(value,minimum,maximum);}
 private static int clamp(long value,int min,int max){return (int)Math.max(min,Math.min(max,value));}
}
