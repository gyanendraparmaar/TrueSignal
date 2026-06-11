package com.truesignal.server;

import com.truesignal.server.config.TrueSignalProperties;
import com.truesignal.server.coordinator.ConsistentHashRing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    private ConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        TrueSignalProperties props = new TrueSignalProperties();
        TrueSignalProperties.Coordinator coord = new TrueSignalProperties.Coordinator();
        coord.setVirtualNodes(150);
        props.setCoordinator(coord);
        ring = new ConsistentHashRing(props);
    }

    @Test
    void testAddAndRemoveNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");
        assertEquals(3, ring.getNodeCount());

        ring.removeNode("node-2");
        assertEquals(2, ring.getNodeCount());
    }

    @Test
    void testMonitorAssignment() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        for (long i = 1; i <= 100; i++) {
            String node = ring.getAssignedNode(i);
            assertNotNull(node);
            assertTrue(Set.of("node-1", "node-2", "node-3").contains(node));
        }
    }

    @Test
    void testConsistentAssignment() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        String first = ring.getAssignedNode(42L);
        String second = ring.getAssignedNode(42L);
        assertEquals(first, second);
    }

    @Test
    void testDistribution() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        Map<String, Integer> counts = new HashMap<>();
        for (long i = 1; i <= 10000; i++) {
            String node = ring.getAssignedNode(i);
            counts.merge(node, 1, Integer::sum);
        }

        assertTrue(counts.size() >= 2, "At least 2 nodes should have monitors");
        for (int count : counts.values()) {
            assertTrue(count >= 100, "Each node should get a meaningful share, got " + count);
        }
    }

    @Test
    void testGetAssignedNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        List<String> nodes = ring.getAssignedNodes(42L, 2);
        assertEquals(2, nodes.size());
        assertNotEquals(nodes.get(0), nodes.get(1));
    }

    @Test
    void testGetAssignedNodesMoreThanAvailable() {
        ring.addNode("node-1");
        ring.addNode("node-2");

        List<String> nodes = ring.getAssignedNodes(42L, 5);
        assertEquals(2, nodes.size());
    }

    @Test
    void testRemoveNodeReassigns() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        String original = ring.getAssignedNode(42L);
        ring.removeNode(original);

        String newNode = ring.getAssignedNode(42L);
        assertNotNull(newNode);
        assertNotEquals(original, newNode);
    }

    @Test
    void testEmptyRing() {
        assertNull(ring.getAssignedNode(42L));
    }

    @Test
    void testGetMonitorsForNode() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        List<Long> allMonitors = LongStream.rangeClosed(1, 100).boxed().toList();
        Set<Long> accounted = new HashSet<>();

        for (String nodeId : List.of("node-1", "node-2", "node-3")) {
            List<Long> assigned = ring.getMonitorsForNode(nodeId, allMonitors);
            for (Long m : assigned) {
                assertTrue(accounted.add(m), "Monitor " + m + " assigned to multiple nodes");
            }
        }
        assertEquals(100, accounted.size());
    }
}
