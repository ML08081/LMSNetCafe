package com.lms.netcafe.module.device.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final JdbcTemplate jdbcTemplate;

    public DeviceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        String areaFilter = area == null || area.isBlank() || "全部".equals(area) ? null : area;
        String statusFilter = status == null || status.isBlank() ? null : status;
        String likeKeyword = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT
                  id,
                  device_code AS deviceCode,
                  area,
                  seat_no AS seatNo,
                  ip_address AS ipAddress,
                  config_desc AS configDesc,
                  status,
                  updated_at AS updatedAt
                FROM device_info
                WHERE deleted = 0
                  AND (? IS NULL OR area = ?)
                  AND (? IS NULL OR status = ?)
                  AND (
                    ? = '%%'
                    OR device_code LIKE ?
                    OR seat_no LIKE ?
                    OR ip_address LIKE ?
                  )
                ORDER BY area, seat_no
                """, areaFilter, areaFilter, statusFilter, statusFilter,
                likeKeyword, likeKeyword, likeKeyword, likeKeyword));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody SaveDeviceRequest request) {
        validateStatus(request.status());
        ensureDeviceCodeAvailable(request.deviceCode(), null);
        jdbcTemplate.update("""
                INSERT INTO device_info (device_code, area, seat_no, ip_address, config_desc, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, request.deviceCode(), request.area(), request.seatNo(), request.ipAddress(),
                request.configDesc(), request.status());
        Long deviceId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.success(Map.of("id", deviceId));
    }

    @PatchMapping("/{deviceId}")
    public ApiResponse<Void> update(@PathVariable Long deviceId, @Valid @RequestBody UpdateDeviceRequest request) {
        Map<String, Object> device = requireDevice(deviceId);
        validateStatus(request.status());
        ensureDeviceCodeAvailable(request.deviceCode(), deviceId);
        ensureMutableStatus((String) device.get("status"), request.status());
        jdbcTemplate.update("""
                UPDATE device_info
                SET device_code = ?, area = ?, seat_no = ?, ip_address = ?, config_desc = ?, status = ?
                WHERE id = ? AND deleted = 0
                """, request.deviceCode(), request.area(), request.seatNo(), request.ipAddress(),
                request.configDesc(), request.status(), deviceId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{deviceId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long deviceId, @Valid @RequestBody StatusRequest request) {
        Map<String, Object> device = requireDevice(deviceId);
        validateStatus(request.status());
        ensureMutableStatus((String) device.get("status"), request.status());
        jdbcTemplate.update("UPDATE device_info SET status = ? WHERE id = ? AND deleted = 0", request.status(), deviceId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{deviceId}/faults")
    public ApiResponse<Void> createFault(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody FaultRequest request) {
        Map<String, Object> device = requireDevice(deviceId);
        ensureMutableStatus((String) device.get("status"), "FAULT");
        jdbcTemplate.update("""
                INSERT INTO device_fault (device_id, fault_type, description, status, reported_by)
                VALUES (?, ?, ?, 'OPEN', ?)
                """, deviceId, request.faultType(), request.description(), user.id());
        jdbcTemplate.update("UPDATE device_info SET status = 'FAULT' WHERE id = ? AND deleted = 0", deviceId);
        return ApiResponse.success(null);
    }

    private Map<String, Object> requireDevice(Long deviceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status FROM device_info WHERE id = ? AND deleted = 0", deviceId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在");
        }
        return rows.get(0);
    }

    private void ensureDeviceCodeAvailable(String deviceCode, Long excludeDeviceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM device_info
                WHERE device_code = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                """, Integer.class, deviceCode, excludeDeviceId, excludeDeviceId);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "设备编号已存在");
        }
    }

    private void ensureMutableStatus(String currentStatus, String nextStatus) {
        if ("IN_USE".equals(currentStatus) && !"IN_USE".equals(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "设备正在使用中，请先完成下机结算");
        }
    }

    private void validateStatus(String status) {
        if (!List.of("IDLE", "IN_USE", "MAINTENANCE", "FAULT", "DISABLED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "设备状态不合法");
        }
    }

    public record SaveDeviceRequest(
            @NotBlank String deviceCode,
            @NotBlank String area,
            @NotBlank String seatNo,
            String ipAddress,
            String configDesc,
            @NotBlank String status) {
    }

    public record UpdateDeviceRequest(
            @NotBlank String deviceCode,
            @NotBlank String area,
            @NotBlank String seatNo,
            String ipAddress,
            String configDesc,
            @NotBlank String status) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record FaultRequest(
            @NotBlank String faultType,
            @NotBlank String description) {
    }
}
