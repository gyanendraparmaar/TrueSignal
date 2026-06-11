package com.truesignal.monitor.reporter;

import com.truesignal.common.protocol.CheckResultReport;
import com.truesignal.monitor.config.MonitorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ResultReporter {

    private static final Logger log = LoggerFactory.getLogger(ResultReporter.class);

    private final RestTemplate restTemplate;
    private final MonitorConfig monitorConfig;

    public ResultReporter(RestTemplate restTemplate, MonitorConfig monitorConfig) {
        this.restTemplate = restTemplate;
        this.monitorConfig = monitorConfig;
    }

    public void reportResult(CheckResultReport report) {
        String url = monitorConfig.effectiveServerBaseUrl() + "/api/internal/results";
        try {
            restTemplate.postForEntity(url, report, Void.class);
            log.debug("Reported check result: {}", report);
        } catch (Exception e) {
            log.error("Failed to report check result for monitor {}", report.monitorId(), e);
        }
    }
}
