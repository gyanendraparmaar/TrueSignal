package com.truesignal.server.coordinator;

import com.truesignal.server.config.TrueSignalProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

@Component
public class ConsistentHashRing {

    private final TrueSignalProperties properties;
    private final TreeMap<Integer, String> ring = new TreeMap<>();
    private final Map<String, Integer> nodeVirtualCount = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public ConsistentHashRing(TrueSignalProperties properties) {
        this.properties = properties;
    }

    public void addNode(String nodeId) {
        rwLock.writeLock().lock();
        try {
            removeNodeUnsafe(nodeId);
            int virtualNodes = properties.getCoordinator().getVirtualNodes();
            for (int i = 0; i < virtualNodes; i++) {
                int hash = md5Hash(nodeId + "#" + i);
                ring.put(hash, nodeId);
            }
            nodeVirtualCount.put(nodeId, virtualNodes);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void removeNode(String nodeId) {
        rwLock.writeLock().lock();
        try {
            removeNodeUnsafe(nodeId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void removeNodeUnsafe(String nodeId) {
        Integer count = nodeVirtualCount.remove(nodeId);
        if (count == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int hash = md5Hash(nodeId + "#" + i);
            ring.remove(hash);
        }
    }

    public String getAssignedNode(Long monitorId) {
        rwLock.readLock().lock();
        try {
            return getAssignedNodeUnsafe(monitorId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<String> getAssignedNodes(Long monitorId, int count) {
        rwLock.readLock().lock();
        try {
            if (ring.isEmpty() || count <= 0) {
                return List.of();
            }
            int hash = md5Hash("monitor-" + monitorId);
            Map.Entry<Integer, String> start = ring.ceilingEntry(hash);
            if (start == null) {
                start = ring.firstEntry();
            }
            LinkedHashSet<String> distinct = new LinkedHashSet<>();
            Map.Entry<Integer, String> cur = start;
            for (int step = 0; step < ring.size() && distinct.size() < count; step++) {
                distinct.add(cur.getValue());
                if (distinct.size() >= count) {
                    break;
                }
                Map.Entry<Integer, String> next = ring.higherEntry(cur.getKey());
                if (next == null) {
                    next = ring.firstEntry();
                }
                cur = next;
            }
            return new ArrayList<>(distinct);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getNodeCount() {
        rwLock.readLock().lock();
        try {
            return nodeVirtualCount.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Long> getMonitorsForNode(String nodeId, List<Long> allMonitorIds) {
        rwLock.readLock().lock();
        try {
            List<Long> result = new ArrayList<>();
            for (Long monitorId : allMonitorIds) {
                String assigned = getAssignedNodeUnsafe(monitorId);
                if (nodeId.equals(assigned)) {
                    result.add(monitorId);
                }
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private String getAssignedNodeUnsafe(Long monitorId) {
        if (ring.isEmpty()) {
            return null;
        }
        int hash = md5Hash("monitor-" + monitorId);
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    private static int md5Hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((digest[3] & 0xFF) << 24) | ((digest[2] & 0xFF) << 16)
                    | ((digest[1] & 0xFF) << 8) | (digest[0] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            return key.hashCode();
        }
    }
}
