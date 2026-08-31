package com.lms.netcafe.module.statistics.controller;

import com.lms.netcafe.common.api.ApiResponse;
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

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "lms-netcafe-backend",
                "status", "UP",
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
