package com.lms.netcafe.module.statistics.controller;

import com.lms.netcafe.common.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "lms-netcafe-backend",
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString()));
    }

    @GetMapping("/statistics/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(Map.of(
                "onlineMembers", 0,
                "idleDevices", 0,
                "todayRevenue", 0,
                "runningSessions", 0));
    }
}
