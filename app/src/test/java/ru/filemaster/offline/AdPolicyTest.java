package ru.filemaster.offline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdPolicyTest {
    @Test
    public void interstitialNeedsThreeOutputsAndTwoMinutes() {
        AdPolicy policy = new AdPolicy(1_000L);
        policy.recordOutput();
        policy.recordOutput();
        assertFalse(policy.canShowInterstitial(200_000L));
        policy.recordOutput();
        assertFalse(policy.canShowInterstitial(120_999L));
        assertTrue(policy.canShowInterstitial(121_000L));
    }

    @Test
    public void showingInterstitialResetsCounterAndGap() {
        AdPolicy policy = new AdPolicy(0L);
        policy.recordOutput();
        policy.recordOutput();
        policy.recordOutput();
        assertTrue(policy.canShowInterstitial(120_000L));
        policy.markInterstitialShown(120_000L);
        assertEquals(0, policy.pendingOutputs());
        assertFalse(policy.canShowInterstitial(500_000L));
    }

    @Test
    public void bannerNeverRefreshesBeforeThirtySeconds() {
        assertEquals(30_000L, AdPolicy.bannerDelay(10_000L, 10_000L));
        assertEquals(1L, AdPolicy.bannerDelay(10_000L, 39_999L));
        assertEquals(0L, AdPolicy.bannerDelay(10_000L, 40_000L));
    }
}
