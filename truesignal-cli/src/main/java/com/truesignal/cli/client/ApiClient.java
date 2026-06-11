package com.truesignal.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.truesignal.common.dto.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class ApiClient {

    private final String serverUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public ApiClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public MonitorResponse createMonitor(CreateMonitorRequest req) {
        return post("/api/monitors", req, MonitorResponse.class);
    }

    public List<MonitorResponse> listMonitors() {
        return getList("/api/monitors", new TypeReference<>() {});
    }

    public MonitorResponse getMonitor(long id) {
        return get("/api/monitors/" + id, MonitorResponse.class);
    }

    public MonitorResponse pauseMonitor(long id) {
        return post("/api/monitors/" + id + "/pause", null, MonitorResponse.class);
    }

    public void deleteMonitor(long id) {
        delete("/api/monitors/" + id);
    }

    public DashboardOverview getOverview() {
        return get("/api/dashboard/overview", DashboardOverview.class);
    }

    public List<NodeResponse> getNodes() {
        return getList("/api/nodes", new TypeReference<>() {});
    }

    public List<IncidentResponse> getActiveIncidents() {
        return getList("/api/incidents", new TypeReference<>() {});
    }

    public List<IncidentResponse> getRecentIncidents() {
        return getList("/api/incidents/recent", new TypeReference<>() {});
    }

    private <T> T get(String path, Class<T> type) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readValue(response.body(), type);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> typeRef) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readValue(response.body(), typeRef);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json");
            if (body != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) return null;
            return mapper.readValue(response.body(), type);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }
    }

    private void delete(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }
    }
}
