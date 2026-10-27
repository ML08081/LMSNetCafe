package com.lms.netcafe.module.statistics.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.module.face.service.FaceServiceClient;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final FaceServiceClient faceClient;

    public HealthController(JdbcTemplate jdbcTemplate, FaceServiceClient faceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.faceClient = faceClient;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean database;
        try {
            database = Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM session_billing_snapshot", Integer.class);
        } catch (Exception ex) { database = false; }
        Map<String, Object> face = faceClient.health();
        return ApiResponse.success(Map.of(
                "service", "lms-netcafe-backend",
                "status", database && Boolean.TRUE.equals(face.get("ready")) ? "UP" : "DEGRADED",
                "databaseReady", database,
                "face", face,
                "timestamp", OffsetDateTime.now().toString()));
    }

    @GetMapping("/statistics/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> summary = jdbcTemplate.queryForMap("SELECT * FROM v_dashboard_summary");
        Integer lowBalanceMembers = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM member_account
                WHERE balance < 10
                """, Integer.class);

        return ApiResponse.success(Map.of(
                "onlineMembers", summary.get("running_sessions"),
                "idleDevices", summary.get("idle_devices"),
                "faultDevices", summary.get("fault_devices"),
                "todayRecharge", summary.get("today_recharge"),
                "todayRevenue", summary.get("today_consume"),
                "runningSessions", summary.get("running_sessions"),
                "lowBalanceMembers", lowBalanceMembers));
    }
}
