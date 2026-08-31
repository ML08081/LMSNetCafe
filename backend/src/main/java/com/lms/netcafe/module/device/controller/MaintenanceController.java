package com.lms.netcafe.module.device.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

    private final JdbcTemplate jdbcTemplate;

    public MaintenanceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/faults")
    public ApiResponse<List<Map<String, Object>>> faults() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT f.id, d.id AS deviceId, d.device_code AS deviceCode, d.area, d.seat_no AS seatNo,
                       f.fault_type AS faultType, f.description, f.status,
                       u.real_name AS reportedBy, f.reported_at AS reportedAt,
                       r.result_desc AS resultDesc, r.repaired_at AS repairedAt
                FROM device_fault f
                JOIN device_info d ON d.id = f.device_id
                LEFT JOIN sys_user u ON u.id = f.reported_by
                LEFT JOIN repair_record r ON r.fault_id = f.id
                ORDER BY CASE f.status WHEN 'OPEN' THEN 0 ELSE 1 END, f.reported_at DESC
                """));
    }

    @PatchMapping("/faults/{faultId}/resolve")
    @Transactional
    public ApiResponse<Void> resolve(
            @PathVariable Long faultId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ResolveRequest request) {
        jdbcTemplate.update("""
                INSERT INTO repair_record (fault_id, repair_user_id, result_desc)
                VALUES (?, ?, ?)
                """, faultId, user.id(), request.resultDesc());
        jdbcTemplate.update("UPDATE device_fault SET status = 'RESOLVED' WHERE id = ?", faultId);
        jdbcTemplate.update("""
                UPDATE device_info d
                JOIN device_fault f ON f.device_id = d.id
                SET d.status = 'IDLE'
                WHERE f.id = ?
                """, faultId);
        return ApiResponse.success(null);
    }

    public record ResolveRequest(@NotBlank String resultDesc) {
    }
}
