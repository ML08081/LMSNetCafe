package com.lms.netcafe.module.billing.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/sessions")
public class MachineSessionController {

    private final JdbcTemplate jdbcTemplate;

    public MachineSessionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(querySessions(""));
    }

    @GetMapping("/running")
    public ApiResponse<List<Map<String, Object>>> running() {
        return ApiResponse.success(querySessions("WHERE s.status = 'RUNNING'"));
    }

    @PostMapping("/start")
    @Transactional
    public ApiResponse<Map<String, Object>> start(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartSessionRequest request) {
        Map<String, Object> member = jdbcTemplate.queryForMap(
                "SELECT status FROM member_info WHERE id = ? AND deleted = 0 FOR UPDATE", request.memberId());
        if (!"ACTIVE".equals(member.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员账户当前不可上机");
        }
        Integer runningCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM machine_session WHERE member_id = ? AND status = 'RUNNING'",
                Integer.class, request.memberId());
        if (runningCount != null && runningCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该会员已有进行中的上机会话");
        }
        Map<String, Object> device = jdbcTemplate.queryForMap(
                "SELECT status FROM device_info WHERE id = ? AND deleted = 0 FOR UPDATE", request.deviceId());
        if (!"IDLE".equals(device.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选机位当前不可用");
        }
        String sessionNo = businessNo("S");
        jdbcTemplate.update("""
                INSERT INTO machine_session
                  (session_no, member_id, device_id, billing_rule_id, start_at, estimated_amount, status, operator_id)
                VALUES (?, ?, ?, ?, NOW(), 0, 'RUNNING', ?)
                """, sessionNo, request.memberId(), request.deviceId(), request.billingRuleId(), user.id());
        Long sessionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("UPDATE device_info SET status = 'IN_USE' WHERE id = ?", request.deviceId());
        return ApiResponse.success(Map.of("sessionId", sessionId, "sessionNo", sessionNo, "status", "RUNNING"));
    }

    @PostMapping("/{sessionId}/settle")
    @Transactional
    public ApiResponse<Map<String, Object>> settle(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, Object> session = jdbcTemplate.queryForMap("""
                SELECT s.member_id, s.device_id, s.start_at, s.status,
                       r.price_per_hour, r.min_minutes, r.billing_unit_minutes
                FROM machine_session s
                JOIN billing_rule r ON r.id = s.billing_rule_id
                WHERE s.id = ? FOR UPDATE
                """, sessionId);
        if (!"RUNNING".equals(session.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该会话已经结算");
        }
        Long memberId = ((Number) session.get("member_id")).longValue();
        Long deviceId = ((Number) session.get("device_id")).longValue();
        LocalDateTime startAt = ((java.sql.Timestamp) session.get("start_at")).toLocalDateTime();
        long actualMinutes = Math.max(1, java.time.Duration.between(startAt, LocalDateTime.now()).toMinutes());
        int minMinutes = ((Number) session.get("min_minutes")).intValue();
        int unitMinutes = ((Number) session.get("billing_unit_minutes")).intValue();
        long chargedMinutes = Math.max(actualMinutes, minMinutes);
        long units = (chargedMinutes + unitMinutes - 1) / unitMinutes;
        BigDecimal amount = ((BigDecimal) session.get("price_per_hour"))
                .multiply(BigDecimal.valueOf(units * unitMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT balance FROM member_account WHERE member_id = ? FOR UPDATE", memberId);
        BigDecimal balanceBefore = (BigDecimal) account.get("balance");
        if (balanceBefore.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员余额不足，请先充值");
        }
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        String consumeNo = businessNo("C");
        String flowNo = businessNo("F");

        jdbcTemplate.update("""
                UPDATE machine_session
                SET end_at = NOW(), duration_minutes = ?, estimated_amount = ?, final_amount = ?,
                    status = 'ENDED', settled_by = ?, version = version + 1
                WHERE id = ?
                """, actualMinutes, amount, amount, user.id(), sessionId);
        jdbcTemplate.update("UPDATE device_info SET status = 'IDLE' WHERE id = ?", deviceId);
        jdbcTemplate.update("""
                UPDATE member_account
                SET balance = ?, total_consume = total_consume + ?, version = version + 1
                WHERE member_id = ?
                """, balanceAfter, amount, memberId);
        jdbcTemplate.update("""
                INSERT INTO consume_record
                  (consume_no, member_id, session_id, consume_type, amount, balance_after, operator_id)
                VALUES (?, ?, ?, 'MACHINE', ?, ?, ?)
                """, consumeNo, memberId, sessionId, amount, balanceAfter, user.id());
        jdbcTemplate.update("""
                INSERT INTO member_account_flow
                  (flow_no, member_id, related_id, related_type, change_amount,
                   balance_before, balance_after, operator_id, remark)
                VALUES (?, ?, ?, 'CONSUME', ?, ?, ?, ?, '上机结算')
                """, flowNo, memberId, sessionId, amount.negate(), balanceBefore, balanceAfter, user.id());
        return ApiResponse.success(Map.of("amount", amount, "balance", balanceAfter, "status", "ENDED"));
    }

    private List<Map<String, Object>> querySessions(String condition) {
        return jdbcTemplate.queryForList("""
                SELECT
                  s.id,
                  s.session_no AS sessionNo,
                  s.member_id AS memberId,
                  m.member_no AS memberNo,
                  m.name AS memberName,
                  d.device_code AS deviceCode,
                  d.seat_no AS seatNo,
                  s.start_at AS startAt,
                  s.end_at AS endAt,
                  TIMESTAMPDIFF(MINUTE, s.start_at, COALESCE(s.end_at, NOW())) AS durationMinutes,
                  s.estimated_amount AS estimatedAmount,
                  s.final_amount AS finalAmount,
                  a.balance,
                  s.status
                FROM machine_session s
                JOIN member_info m ON m.id = s.member_id
                JOIN device_info d ON d.id = s.device_id
                LEFT JOIN member_account a ON a.member_id = m.id
                %s
                ORDER BY s.start_at DESC
                """.formatted(condition));
    }

    private String businessNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public record StartSessionRequest(
            @NotNull Long memberId,
            @NotNull Long deviceId,
            @NotNull Long billingRuleId) {
    }
}
