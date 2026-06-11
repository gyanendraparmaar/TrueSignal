package com.truesignal.server;

import com.truesignal.common.enums.CheckStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuorumLogicTest {

    private CheckStatus evaluateQuorum(List<CheckStatus> votes, double threshold) {
        long downVotes = votes.stream()
                .filter(s -> s == CheckStatus.DOWN || s == CheckStatus.DEGRADED)
                .count();
        double downRatio = (double) downVotes / votes.size();
        return downRatio > threshold ? CheckStatus.DOWN : CheckStatus.UP;
    }

    @Test
    void testAllUp() {
        assertEquals(CheckStatus.UP,
                evaluateQuorum(List.of(CheckStatus.UP, CheckStatus.UP, CheckStatus.UP), 0.5));
    }

    @Test
    void testAllDown() {
        assertEquals(CheckStatus.DOWN,
                evaluateQuorum(List.of(CheckStatus.DOWN, CheckStatus.DOWN, CheckStatus.DOWN), 0.5));
    }

    @Test
    void testMajorityDown() {
        assertEquals(CheckStatus.DOWN,
                evaluateQuorum(List.of(CheckStatus.DOWN, CheckStatus.DOWN, CheckStatus.UP), 0.5));
    }

    @Test
    void testMajorityUp() {
        assertEquals(CheckStatus.UP,
                evaluateQuorum(List.of(CheckStatus.DOWN, CheckStatus.UP, CheckStatus.UP), 0.5));
    }

    @Test
    void testExactThreshold() {
        assertEquals(CheckStatus.UP,
                evaluateQuorum(List.of(CheckStatus.DOWN, CheckStatus.UP), 0.5));
    }

    @Test
    void testDegradedCountsAsDown() {
        assertEquals(CheckStatus.DOWN,
                evaluateQuorum(List.of(CheckStatus.DEGRADED, CheckStatus.DEGRADED, CheckStatus.UP), 0.5));
    }

    @Test
    void testSingleNodeDown() {
        assertEquals(CheckStatus.DOWN,
                evaluateQuorum(List.of(CheckStatus.DOWN), 0.5));
    }

    @Test
    void testSingleNodeUp() {
        assertEquals(CheckStatus.UP,
                evaluateQuorum(List.of(CheckStatus.UP), 0.5));
    }

    @Test
    void testHighThreshold() {
        assertEquals(CheckStatus.UP,
                evaluateQuorum(List.of(CheckStatus.DOWN, CheckStatus.DOWN, CheckStatus.UP), 0.8));
    }

    @Test
    void testFiveNodesMajorityDown() {
        assertEquals(CheckStatus.DOWN,
                evaluateQuorum(List.of(CheckStatus.DOWN, CheckStatus.DOWN, CheckStatus.DOWN,
                        CheckStatus.UP, CheckStatus.UP), 0.5));
    }
}
