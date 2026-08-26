package io.github.miklires.mreputation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReputationPolicyTest {
 private final ReputationPolicy p=new ReputationPolicy(500,0,1000,500,1);
 @Test void clampsBounds(){assertEquals(0,p.change(5,-20));assertEquals(1000,p.set(2000));}
 @Test void limitsPositiveGainAboveThreshold(){assertEquals(501,p.change(500,50));}
 @Test void allowsFullRecoveryBelowThreshold(){assertEquals(490,p.change(450,40));}
}
