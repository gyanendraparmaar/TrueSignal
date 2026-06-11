package com.truesignal.cli;

import com.truesignal.cli.client.ApiClient;
import com.truesignal.cli.formatter.TableFormatter;
import com.truesignal.common.dto.*;
import com.truesignal.common.enums.MonitorType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrueSignalCli {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ApiClient api;

    public TrueSignalCli(String serverUrl) {
        this.api = new ApiClient(serverUrl);
    }

    public static void main(String[] args) {
        System.out.println("\n  \u001B[1m\u001B[36mTrueSignal CLI v1.0\u001B[0m");
        System.out.println("  Distributed Uptime Monitoring\n");

        String serverUrl = resolveServerUrl(args);
        String[] filtered = filterServerArg(args);

        if (filtered.length == 0) {
            printHelp();
            return;
        }

        TrueSignalCli cli = new TrueSignalCli(serverUrl);
        try {
            switch (filtered[0].toLowerCase()) {
                case "add" -> cli.addMonitor(filtered);
                case "list" -> cli.listMonitors();
                case "status" -> cli.showStatus();
                case "detail" -> cli.showDetail(filtered);
                case "pause" -> cli.pauseMonitor(filtered);
                case "delete" -> cli.deleteMonitor(filtered);
                case "nodes" -> cli.showNodes();
                case "incidents" -> cli.showIncidents();
                case "help", "--help", "-h" -> printHelp();
                default -> {
                    System.out.println("  Unknown command: " + filtered[0]);
                    printHelp();
                }
            }
        } catch (Exception e) {
            System.err.println("\n  \u001B[31mError: " + e.getMessage() + "\u001B[0m\n");
        }
    }

    private void addMonitor(String[] args) {
        Map<String, String> flags = parseFlags(args, 1);
        String name = flags.getOrDefault("--name", flags.get("-n"));
        String url = flags.getOrDefault("--url", flags.get("-u"));
        String typeStr = flags.getOrDefault("--type", "HTTP");

        if (name == null || url == null) {
            System.out.println("  Usage: add --name <name> --url <url> [--type HTTP|TCP|SSL|DNS] [--interval <s>] [--timeout <ms>]");
            return;
        }

        MonitorType type = MonitorType.valueOf(typeStr.toUpperCase());
        int interval = Integer.parseInt(flags.getOrDefault("--interval", "60"));
        int timeout = Integer.parseInt(flags.getOrDefault("--timeout", "10000"));
        int expectedStatus = Integer.parseInt(flags.getOrDefault("--expected-status", "200"));
        String keyword = flags.get("--keyword");
        String project = flags.getOrDefault("--project", "default");

        CreateMonitorRequest req = new CreateMonitorRequest(name, url, type, interval, timeout, expectedStatus, keyword, project);
        MonitorResponse resp = api.createMonitor(req);
        System.out.println(TableFormatter.header("Monitor Created"));
        System.out.println("  ID:       " + resp.id());
        System.out.println("  Name:     " + resp.name());
        System.out.println("  URL:      " + resp.url());
        System.out.println("  Type:     " + resp.type());
        System.out.println("  Interval: " + resp.intervalSeconds() + "s");
        System.out.println("  Project:  " + resp.projectSlug());
        System.out.println();
    }

    private void listMonitors() {
        List<MonitorResponse> monitors = api.listMonitors();
        if (monitors.isEmpty()) {
            System.out.println("  No monitors configured. Use 'add' to create one.\n");
            return;
        }
        System.out.println(TableFormatter.header("Monitors (" + monitors.size() + ")"));
        String[] headers = {"ID", "Name", "URL", "Type", "Status", "Uptime", "Interval"};
        List<String[]> rows = new ArrayList<>();
        for (MonitorResponse m : monitors) {
            rows.add(new String[]{
                    String.valueOf(m.id()),
                    m.name(),
                    truncate(m.url(), 40),
                    m.type().name(),
                    TableFormatter.statusIcon(m.currentStatus() != null ? m.currentStatus().name() : "UNKNOWN"),
                    TableFormatter.formatUptime(m.uptimePercent()),
                    m.intervalSeconds() + "s"
            });
        }
        System.out.println(TableFormatter.formatTable(headers, rows));
    }

    private void showStatus() {
        DashboardOverview overview = api.getOverview();
        System.out.println(TableFormatter.header("Dashboard Overview"));
        System.out.println("  Total Monitors:    " + overview.totalMonitors());
        System.out.println("  Monitors Up:       \u001B[32m" + overview.monitorsUp() + "\u001B[0m");
        System.out.println("  Monitors Down:     \u001B[31m" + overview.monitorsDown() + "\u001B[0m");
        System.out.println("  Monitors Paused:   " + overview.monitorsPaused());
        System.out.println("  Active Incidents:  \u001B[31m" + overview.activeIncidents() + "\u001B[0m");
        System.out.println("  Cluster Nodes:     " + overview.totalNodes() + " total, \u001B[32m" + overview.nodesAlive() + " alive\u001B[0m");
        System.out.println("  Overall Uptime:    " + TableFormatter.formatUptime(overview.overallUptimePercent()));
        System.out.println();

        if (!overview.monitors().isEmpty()) {
            System.out.println(TableFormatter.header("Monitor Summary"));
            String[] headers = {"ID", "Name", "Status", "Uptime"};
            List<String[]> rows = new ArrayList<>();
            for (MonitorResponse m : overview.monitors()) {
                rows.add(new String[]{
                        String.valueOf(m.id()),
                        m.name(),
                        TableFormatter.statusIcon(m.currentStatus() != null ? m.currentStatus().name() : "UNKNOWN"),
                        TableFormatter.formatUptime(m.uptimePercent())
                });
            }
            System.out.println(TableFormatter.formatTable(headers, rows));
        }
    }

    private void showDetail(String[] args) {
        if (args.length < 2) {
            System.out.println("  Usage: detail <monitorId>");
            return;
        }
        long id = Long.parseLong(args[1]);
        MonitorResponse m = api.getMonitor(id);
        System.out.println(TableFormatter.header("Monitor #" + m.id() + " — " + m.name()));
        System.out.println("  URL:              " + m.url());
        System.out.println("  Type:             " + m.type());
        System.out.println("  Status:           " + TableFormatter.statusIcon(m.currentStatus() != null ? m.currentStatus().name() : "UNKNOWN"));
        System.out.println("  Uptime (30d):     " + TableFormatter.formatUptime(m.uptimePercent()));
        System.out.println("  Avg Response:     " + (m.avgResponseTimeMs() != null ? m.avgResponseTimeMs() + "ms" : "—"));
        System.out.println("  Interval:         " + m.intervalSeconds() + "s");
        System.out.println("  Timeout:          " + m.timeoutMs() + "ms");
        System.out.println("  Expected Status:  " + m.expectedStatusCode());
        System.out.println("  Keyword:          " + (m.keyword() != null ? m.keyword() : "—"));
        System.out.println("  Project:          " + m.projectSlug());
        System.out.println("  Paused:           " + (m.paused() ? "\u001B[33mYes\u001B[0m" : "No"));
        System.out.println("  Last Checked:     " + (m.lastCheckedAt() != null ? TIME_FMT.format(m.lastCheckedAt()) : "Never"));
        System.out.println("  Created:          " + (m.createdAt() != null ? TIME_FMT.format(m.createdAt()) : "—"));
        System.out.println();
    }

    private void pauseMonitor(String[] args) {
        if (args.length < 2) {
            System.out.println("  Usage: pause <monitorId>");
            return;
        }
        long id = Long.parseLong(args[1]);
        MonitorResponse m = api.pauseMonitor(id);
        String state = m.paused() ? "\u001B[33mpaused\u001B[0m" : "\u001B[32mresumed\u001B[0m";
        System.out.println("  Monitor #" + id + " is now " + state + "\n");
    }

    private void deleteMonitor(String[] args) {
        if (args.length < 2) {
            System.out.println("  Usage: delete <monitorId>");
            return;
        }
        long id = Long.parseLong(args[1]);
        api.deleteMonitor(id);
        System.out.println("  Monitor #" + id + " deleted.\n");
    }

    private void showNodes() {
        List<NodeResponse> nodes = api.getNodes();
        if (nodes.isEmpty()) {
            System.out.println("  No monitor nodes registered.\n");
            return;
        }
        System.out.println(TableFormatter.header("Cluster Nodes (" + nodes.size() + ")"));
        String[] headers = {"Node ID", "Region", "Address", "Status", "Monitors", "Last Heartbeat"};
        List<String[]> rows = new ArrayList<>();
        for (NodeResponse n : nodes) {
            rows.add(new String[]{
                    n.nodeId(),
                    n.region(),
                    n.address(),
                    TableFormatter.nodeStatusIcon(n.status().name()),
                    String.valueOf(n.assignedMonitors()),
                    n.lastHeartbeat() != null ? TIME_FMT.format(n.lastHeartbeat()) : "—"
            });
        }
        System.out.println(TableFormatter.formatTable(headers, rows));
    }

    private void showIncidents() {
        List<IncidentResponse> active = api.getActiveIncidents();
        List<IncidentResponse> recent = api.getRecentIncidents();

        System.out.println(TableFormatter.header("Active Incidents (" + active.size() + ")"));
        if (active.isEmpty()) {
            System.out.println("  No active incidents. All systems operational.\n");
        } else {
            printIncidentTable(active);
        }

        System.out.println(TableFormatter.header("Recent Incidents"));
        if (recent.isEmpty()) {
            System.out.println("  No recent incidents.\n");
        } else {
            printIncidentTable(recent);
        }
    }

    private void printIncidentTable(List<IncidentResponse> incidents) {
        String[] headers = {"ID", "Monitor", "Status", "Cause", "Duration"};
        List<String[]> rows = new ArrayList<>();
        for (IncidentResponse inc : incidents) {
            String status = inc.status().name().equals("ONGOING")
                    ? "\u001B[31m● ONGOING\u001B[0m"
                    : "\u001B[32m✓ RESOLVED\u001B[0m";
            rows.add(new String[]{
                    String.valueOf(inc.id()),
                    inc.monitorName() != null ? inc.monitorName() : "Monitor #" + inc.monitorId(),
                    status,
                    truncate(inc.cause() != null ? inc.cause() : "—", 35),
                    TableFormatter.formatDuration(inc.durationSeconds())
            });
        }
        System.out.println(TableFormatter.formatTable(headers, rows));

        for (IncidentResponse inc : incidents) {
            if (inc.diagnosis() != null && !inc.diagnosis().isBlank()) {
                String sourceTag = "AI".equals(inc.diagnosisSource())
                        ? "\u001B[35m[AI]\u001B[0m"
                        : "\u001B[36m[Rule]\u001B[0m";
                String confidence = inc.diagnosisConfidence() != null ? inc.diagnosisConfidence().name() : "?";
                System.out.println("  " + sourceTag + " \u001B[1mDiagnosis for " + inc.monitorName() + ":\u001B[0m " + inc.diagnosis());
                System.out.println("    Confidence: " + confidence + " | Category: " + inc.diagnosisCategory());
                if (inc.diagnosisExplanation() != null) {
                    System.out.println("    " + truncate(inc.diagnosisExplanation(), 120));
                }
                if (inc.diagnosisSuggestion() != null) {
                    System.out.println("    \u001B[33mSuggestion:\u001B[0m " + truncate(inc.diagnosisSuggestion(), 120));
                }
                System.out.println();
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                  \u001B[1mUsage:\u001B[0m truesignal <command> [options]
                
                  \u001B[1mCommands:\u001B[0m
                    add       Create a new monitor
                              --name <name> --url <url> [--type HTTP|TCP|SSL|DNS]
                              [--interval <s>] [--timeout <ms>] [--expected-status <code>]
                              [--keyword <text>] [--project <slug>]
                    list      List all monitors
                    status    Show dashboard overview
                    detail    Show monitor details              detail <monitorId>
                    pause     Pause/resume a monitor            pause <monitorId>
                    delete    Delete a monitor                  delete <monitorId>
                    nodes     Show cluster nodes
                    incidents Show active and recent incidents
                    help      Show this help message
                
                  \u001B[1mOptions:\u001B[0m
                    --server <url>  Server URL (default: http://localhost:8080)
                                    Also reads PULSEGUARD_SERVER env var
                """);
    }

    private static String resolveServerUrl(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--server".equals(args[i])) return args[i + 1];
        }
        String envUrl = System.getenv("PULSEGUARD_SERVER");
        return envUrl != null ? envUrl : "http://localhost:8080";
    }

    private static String[] filterServerArg(String[] args) {
        List<String> filtered = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--server".equals(args[i]) && i + 1 < args.length) {
                i++;
            } else {
                filtered.add(args[i]);
            }
        }
        return filtered.toArray(new String[0]);
    }

    private static Map<String, String> parseFlags(String[] args, int startIndex) {
        Map<String, String> flags = new HashMap<>();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].startsWith("--") || args[i].startsWith("-")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    flags.put(args[i], args[i + 1]);
                    i++;
                } else {
                    flags.put(args[i], "true");
                }
            }
        }
        return flags;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "—";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}
