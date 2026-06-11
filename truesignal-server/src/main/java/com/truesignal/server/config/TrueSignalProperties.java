package com.truesignal.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "truesignal")
public class TrueSignalProperties {

    private Quorum quorum = new Quorum();
    private Heartbeat heartbeat = new Heartbeat();
    private Coordinator coordinator = new Coordinator();
    private Ai ai = new Ai();

    public Quorum getQuorum() {
        return quorum;
    }

    public void setQuorum(Quorum quorum) {
        this.quorum = quorum;
    }

    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Heartbeat heartbeat) {
        this.heartbeat = heartbeat;
    }

    public Coordinator getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(Coordinator coordinator) {
        this.coordinator = coordinator;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public static class Quorum {

        private double threshold = 0.5;
        private long evaluationIntervalMs = 5000;

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public long getEvaluationIntervalMs() {
            return evaluationIntervalMs;
        }

        public void setEvaluationIntervalMs(long evaluationIntervalMs) {
            this.evaluationIntervalMs = evaluationIntervalMs;
        }
    }

    public static class Heartbeat {

        private long timeoutSuspectMs = 15000;
        private long timeoutDeadMs = 30000;
        private long checkIntervalMs = 10000;

        public long getTimeoutSuspectMs() {
            return timeoutSuspectMs;
        }

        public void setTimeoutSuspectMs(long timeoutSuspectMs) {
            this.timeoutSuspectMs = timeoutSuspectMs;
        }

        public long getTimeoutDeadMs() {
            return timeoutDeadMs;
        }

        public void setTimeoutDeadMs(long timeoutDeadMs) {
            this.timeoutDeadMs = timeoutDeadMs;
        }

        public long getCheckIntervalMs() {
            return checkIntervalMs;
        }

        public void setCheckIntervalMs(long checkIntervalMs) {
            this.checkIntervalMs = checkIntervalMs;
        }
    }

    public static class Coordinator {

        private int virtualNodes = 150;

        public int getVirtualNodes() {
            return virtualNodes;
        }

        public void setVirtualNodes(int virtualNodes) {
            this.virtualNodes = virtualNodes;
        }
    }

    public static class Ai {

        private boolean enabled = false;
        private String apiKey;
        private String model = "gemini-2.0-flash";
        private String baseUrl = "https://generativelanguage.googleapis.com";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
