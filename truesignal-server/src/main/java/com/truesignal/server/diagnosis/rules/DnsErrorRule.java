package com.truesignal.server.diagnosis.rules;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.diagnosis.DiagnosisResult;
import com.truesignal.server.diagnosis.DiagnosisRule;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DnsErrorRule implements DiagnosisRule {

    private static final Pattern DNS_PATTERN = Pattern.compile(
            "(?i)(unknown\\s*host|UnknownHostException|name.*resolution|NXDOMAIN|dns.*fail|resolve.*fail|getaddrinfo|SERVFAIL|no.*address.*associated)");

    @Override
    public Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        String combinedErrors = failedResults.stream()
                .map(CheckResultEntity::getError)
                .filter(e -> e != null && !e.isBlank())
                .collect(java.util.stream.Collectors.joining(" | "));

        if (combinedErrors.isBlank()) {
            return Optional.empty();
        }

        if (DNS_PATTERN.matcher(combinedErrors).find()) {
            boolean allNodesFail = failedResults.stream()
                    .map(CheckResultEntity::getNodeRegion)
                    .distinct()
                    .count() > 1;

            if (allNodesFail) {
                return Optional.of(new DiagnosisResult(
                        "DNS resolution failure — domain not resolving from multiple regions",
                        DiagnosisCategory.DNS,
                        DiagnosisConfidence.HIGH,
                        "The domain name could not be resolved to an IP address from multiple monitoring regions. The DNS records may have been deleted, the domain may have expired, or the authoritative DNS server is down.",
                        "Check domain registration status and DNS records using 'dig' or 'nslookup'. Verify the authoritative nameservers are responding. Check domain registrar for expiration.",
                        "RULE_BASED"));
            }

            return Optional.of(new DiagnosisResult(
                    "DNS resolution failure — domain not resolving",
                    DiagnosisCategory.DNS,
                    DiagnosisConfidence.HIGH,
                    "The domain name could not be resolved to an IP address. This prevents any connection from being established. The DNS records may be misconfigured, the domain may have expired, or the DNS server is not responding.",
                    "Verify DNS records with 'nslookup' or 'dig'. Check if the domain registration is still active. If using custom nameservers, verify they are operational.",
                    "RULE_BASED"));
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 2;
    }
}
