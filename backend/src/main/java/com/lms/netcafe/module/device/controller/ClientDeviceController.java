package com.lms.netcafe.module.device.controller;

import com.lms.netcafe.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientDeviceController {

    private final JdbcTemplate jdbcTemplate;

    public ClientDeviceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/register")
    @Transactional
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        List<Map<String, Object>> devices = jdbcTemplate.queryForList("""
                SELECT id, device_code, seat_no, area
                FROM device_info
                WHERE device_code = ? AND deleted = 0
                LIMIT 1
                """, request.deviceCode());
        if (devices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备编号不存在，请先在管理端创建设备");
        }
        Map<String, Object> device = devices.get(0);
        Long deviceId = ((Number) device.get("id")).longValue();
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO client_device
                  (device_id, device_code, client_token, app_version, online_status, last_heartbeat_at)
                VALUES (?, ?, ?, ?, 'ONLINE', NOW())
                ON DUPLICATE KEY UPDATE
                  device_id = VALUES(device_id), client_token = VALUES(client_token),
                  app_version = VALUES(app_version), online_status = 'ONLINE', last_heartbeat_at = NOW()
                """, deviceId, request.deviceCode(), token, request.appVersion());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceCode", device.get("device_code"));
        result.put("seatNo", device.get("seat_no"));
        result.put("area", device.get("area"));
        result.put("clientToken", token);
        result.put("heartbeatSeconds", 20);
        return ApiResponse.success(result);
    }

    @PostMapping("/{deviceCode}/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(
            @PathVariable String deviceCode,
            @RequestHeader("X-Client-Token") String token) {
        requireClient(deviceCode, token);
        jdbcTemplate.update("""
                UPDATE client_device
                SET online_status = 'ONLINE', last_heartbeat_at = NOW()
                WHERE device_code = ?
                """, deviceCode);
        return ApiResponse.success(Map.of("online", true, "serverTime", LocalDateTime.now()));
    }

    @GetMapping("/{deviceCode}/session")
    public ApiResponse<Map<String, Object>> session(
            @PathVariable String deviceCode,
            @RequestHeader("X-Client-Token") String token) {
        Map<String, Object> client = requireClient(deviceCode, token);
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList("""
                SELECT s.id AS sessionId, s.session_no AS sessionNo, s.start_at AS startedAt,
                       m.name AS memberName, a.balance,
                       r.price_per_hour AS pricePerHour, r.min_minutes AS minMinutes,
                       r.billing_unit_minutes AS billingUnitMinutes,
                       r.low_balance_threshold AS lowBalanceThreshold,
                       COALESCE(p.enabled, 1) AS petEnabled,
                       COALESCE(p.always_on_top, 1) AS petAlwaysOnTop,
                       COALESCE(p.show_bubble, 1) AS petShowBubble
                FROM machine_session s
                JOIN member_info m ON m.id = s.member_id
                JOIN member_account a ON a.member_id = s.member_id
                JOIN billing_rule r ON r.id = s.billing_rule_id
                LEFT JOIN member_pet_setting p ON p.member_id = s.member_id
                WHERE s.device_id = ? AND s.status = 'RUNNING'
                ORDER BY s.start_at DESC
                LIMIT 1
                """, client.get("device_id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceCode", deviceCode);
        result.put("seatNo", client.get("seat_no"));
        result.put("area", client.get("area"));
        result.put("deviceStatus", client.get("device_status"));
        result.put("serverTime", LocalDateTime.now());
        if (sessions.isEmpty()) {
            result.put("active", false);
            return ApiResponse.success(result);
        }

        Map<String, Object> session = sessions.get(0);
        Object startedAtValue = session.get("startedAt");
        LocalDateTime startedAt = startedAtValue instanceof LocalDateTime localDateTime
                ? localDateTime
                : ((java.sql.Timestamp) startedAtValue).toLocalDateTime();
        long durationMinutes = Math.max(1, Duration.between(startedAt, LocalDateTime.now()).toMinutes());
        int minimumMinutes = ((Number) session.get("minMinutes")).intValue();
        int unitMinutes = ((Number) session.get("billingUnitMinutes")).intValue();
        long chargedMinutes = Math.max(durationMinutes, minimumMinutes);
        long units = (chargedMinutes + unitMinutes - 1) / unitMinutes;
        BigDecimal amount = ((BigDecimal) session.get("pricePerHour"))
                .multiply(BigDecimal.valueOf(units * unitMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal balance = (BigDecimal) session.get("balance");
        BigDecimal threshold = (BigDecimal) session.get("lowBalanceThreshold");

        result.put("active", true);
        result.put("sessionId", session.get("sessionId"));
        result.put("sessionNo", session.get("sessionNo"));
        result.put("memberName", session.get("memberName"));
        result.put("startedAt", startedAt);
        result.put("durationMinutes", durationMinutes);
        result.put("currentAmount", amount);
        result.put("balance", balance);
        result.put("lowBalanceThreshold", threshold);
        result.put("lowBalance", balance.subtract(amount).compareTo(threshold) <= 0);
        result.put("petEnabled", asBoolean(session.get("petEnabled")));
        result.put("petAlwaysOnTop", asBoolean(session.get("petAlwaysOnTop")));
        result.put("petShowBubble", asBoolean(session.get("petShowBubble")));
        return ApiResponse.success(result);
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
    }

    private Map<String, Object> requireClient(String deviceCode, String token) {
        List<Map<String, Object>> clients = jdbcTemplate.queryForList("""
                SELECT c.device_id, c.client_token, d.seat_no, d.area, d.status AS device_status
                FROM client_device c
                JOIN device_info d ON d.id = c.device_id AND d.deleted = 0
                WHERE c.device_code = ?
                LIMIT 1
                """, deviceCode);
        if (clients.isEmpty() || token == null || token.isBlank()
                || !token.equals(clients.get(0).get("client_token"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "客户机令牌无效，请重新绑定设备");
        }
        return clients.get(0);
    }

    public record RegisterRequest(@NotBlank String deviceCode, @NotBlank String appVersion) {
    }
}
