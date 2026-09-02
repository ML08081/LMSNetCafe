package com.lms.netcafe.module.member.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portal")
public class CustomerPortalController {

    private final JdbcTemplate jdbcTemplate;

    public CustomerPortalController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@AuthenticationPrincipal AuthenticatedUser user) {
        Long memberId = requireMemberId(user);
        Map<String, Object> profile = jdbcTemplate.queryForMap("""
                SELECT m.member_no AS memberNo, m.name, m.phone, m.level, m.status,
                       a.balance, a.total_recharge AS totalRecharge, a.total_consume AS totalConsume
                FROM member_info m
                JOIN member_account a ON a.member_id = m.id
                WHERE m.id = ? AND m.deleted = 0
                """, memberId);
        List<Map<String, Object>> running = jdbcTemplate.queryForList("""
                SELECT s.session_no AS sessionNo, d.device_code AS deviceCode, d.area, d.seat_no AS seatNo,
                       s.start_at AS startAt,
                       TIMESTAMPDIFF(MINUTE, s.start_at, NOW()) AS durationMinutes,
                       s.estimated_amount AS estimatedAmount, s.status
                FROM machine_session s
                JOIN device_info d ON d.id = s.device_id
                WHERE s.member_id = ? AND s.status = 'RUNNING'
                ORDER BY s.start_at DESC
                LIMIT 1
                """, memberId);
        Integer availableDevices = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_info WHERE status = 'IDLE' AND deleted = 0", Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("currentSession", running.isEmpty() ? null : running.get(0));
        result.put("availableDevices", availableDevices);
        result.put("operationProfile", queryOperationProfile(memberId));
        result.put("coupons", jdbcTemplate.queryForList("""
                SELECT coupon_no AS couponNo, coupon_type AS couponType, title,
                       discount_amount AS discountAmount, min_spend AS minSpend,
                       source_reason AS sourceReason, expires_at AS expiresAt
                FROM member_coupon
                WHERE member_id = ? AND status = 'UNUSED' AND expires_at > NOW()
                ORDER BY expires_at ASC, id DESC
                LIMIT 5
                """, memberId));
        return ApiResponse.success(result);
    }

    @GetMapping("/account-flows")
    public ApiResponse<List<Map<String, Object>>> accountFlows(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT flow_no AS flowNo, related_type AS relatedType, change_amount AS changeAmount,
                       balance_before AS balanceBefore, balance_after AS balanceAfter, remark, created_at AS createdAt
                FROM member_account_flow
                WHERE member_id = ?
                ORDER BY created_at DESC, id DESC
                """, requireMemberId(user)));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT s.session_no AS sessionNo, d.device_code AS deviceCode, d.area, d.seat_no AS seatNo,
                       s.start_at AS startAt, s.end_at AS endAt,
                       TIMESTAMPDIFF(MINUTE, s.start_at, COALESCE(s.end_at, NOW())) AS durationMinutes,
                       s.estimated_amount AS estimatedAmount, s.final_amount AS finalAmount, s.status
                FROM machine_session s
                JOIN device_info d ON d.id = s.device_id
                WHERE s.member_id = ?
                ORDER BY s.start_at DESC
                """, requireMemberId(user)));
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices(@AuthenticationPrincipal AuthenticatedUser user) {
        requireMemberId(user);
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT id, device_code AS deviceCode, area, seat_no AS seatNo,
                       area_type AS areaType, room_capacity AS roomCapacity,
                       hourly_rate_hint AS hourlyRateHint, config_desc AS configDesc,
                       status, updated_at AS updatedAt
                FROM device_info
                WHERE deleted = 0
                ORDER BY area, seat_no
                """));
    }

    @GetMapping("/billing-rules")
    public ApiResponse<List<Map<String, Object>>> billingRules(@AuthenticationPrincipal AuthenticatedUser user) {
        requireMemberId(user);
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT id, rule_name AS ruleName, price_per_hour AS pricePerHour,
                       min_minutes AS minMinutes, billing_unit_minutes AS billingUnitMinutes
                FROM billing_rule
                WHERE status = 'ENABLED'
                ORDER BY id
                """));
    }

    @GetMapping("/faults")
    public ApiResponse<List<Map<String, Object>>> faults(@AuthenticationPrincipal AuthenticatedUser user) {
        requireMemberId(user);
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT f.id, d.device_code AS deviceCode, f.fault_type AS faultType,
                       f.description, f.status, f.reported_at AS reportedAt,
                       r.result_desc AS resultDesc, r.repaired_at AS repairedAt
                FROM device_fault f
                JOIN device_info d ON d.id = f.device_id
                LEFT JOIN repair_record r ON r.fault_id = f.id
                WHERE f.reported_by = ?
                ORDER BY f.reported_at DESC
                """, user.id()));
    }

    @PostMapping("/faults")
    @Transactional
    public ApiResponse<Map<String, Object>> reportFault(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody FaultRequest request) {
        requireMemberId(user);
        Integer deviceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_info WHERE id = ? AND deleted = 0", Integer.class, request.deviceId());
        if (deviceCount == null || deviceCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在");
        }
        jdbcTemplate.update("""
                INSERT INTO device_fault (device_id, fault_type, description, status, reported_by)
                VALUES (?, 'USER_REPORT', ?, 'OPEN', ?)
                """, request.deviceId(), request.description(), user.id());
        Long faultId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("UPDATE device_info SET status = 'FAULT' WHERE id = ?", request.deviceId());
        return ApiResponse.success(Map.of("faultId", faultId, "status", "OPEN"));
    }

    private Long requireMemberId(AuthenticatedUser user) {
        if (user == null || user.memberId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定会员资料");
        }
        return user.memberId();
    }

    private Map<String, Object> queryOperationProfile(Long memberId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT favorite_games AS favoriteGames, preferred_time_slot AS preferredTimeSlot,
                       beverage_preference AS beveragePreference, spending_power AS spendingPower,
                       churn_risk AS churnRisk, segment, last_visit_at AS lastVisitAt,
                       recommendation
                FROM member_operation_profile
                WHERE member_id = ?
                """, memberId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public record FaultRequest(@NotNull Long deviceId, @NotBlank String description) {
    }
}
