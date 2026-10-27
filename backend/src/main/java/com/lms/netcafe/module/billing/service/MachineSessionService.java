package com.lms.netcafe.module.billing.service;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.face.service.FaceRecognitionService;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Service
public class MachineSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final FaceRecognitionService faces;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public MachineSessionService(JdbcTemplate jdbcTemplate, FaceRecognitionService faces,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.faces = faces;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(querySessions(""));
    }

    public ApiResponse<List<Map<String, Object>>> running() {
        return ApiResponse.success(querySessions("WHERE s.status = 'RUNNING'"));
    }

    public ApiResponse<Map<String, Object>> diagnostics() {
        return ApiResponse.success(Map.of(
                "staleSessions", jdbcTemplate.queryForList("""
                    SELECT id, session_no AS sessionNo, start_at AS startAt FROM machine_session
                    WHERE status = 'RUNNING' AND TIMESTAMPDIFF(HOUR, start_at, NOW()) >= 24
                    """),
                "inconsistentDevices", jdbcTemplate.queryForList("""
                    SELECT d.id, d.device_code AS deviceCode, d.status FROM device_info d
                    WHERE d.deleted = 0 AND ((d.status = 'IDLE' AND EXISTS
                      (SELECT 1 FROM machine_session s WHERE s.device_id=d.id AND s.status='RUNNING'))
                    OR (d.status = 'IN_USE' AND NOT EXISTS
                      (SELECT 1 FROM machine_session s WHERE s.device_id=d.id AND s.status='RUNNING')))
                    """)));
    }

    public ApiResponse<Map<String, Object>> preview(Long sessionId) {
        List<Map<String, Object>> rows = querySessions("WHERE s.id = " + sessionId.longValue());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        Map<String, Object> session = rows.get(0);
        BigDecimal amount = (BigDecimal) ("RUNNING".equals(session.get("status"))
                ? session.get("estimatedAmount") : session.get("finalAmount"));
        return ApiResponse.success(Map.of("sessionId", sessionId, "amount", amount,
                "balance", session.get("balance"), "status", session.get("status")));
    }

    @Transactional
    public ApiResponse<Map<String, Object>> startForMember(AuthenticatedUser user, Long deviceId, Long ruleId) {
        if (user.memberId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号未绑定会员");
        return startInternal(user, new StartSessionRequest(user.memberId(), deviceId, ruleId, false), "ACCOUNT");
    }

    @Transactional
    public ApiResponse<Map<String, Object>> start(
            AuthenticatedUser user,
            StartSessionRequest request) {
        if (Boolean.TRUE.equals(request.faceRequired())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择拍照认证上机");
        }
        return startInternal(user, request, "MANUAL");
    }

    public ApiResponse<Map<String, Object>> startWithFace(
            AuthenticatedUser user,
            Long memberId, Long deviceId,
            Long billingRuleId, MultipartFile image) {
        // Verify before opening the transaction, so failed attempts remain in the audit log.
        faces.verifyMember(memberId, deviceId, image);
        return transactions.execute(status -> startInternal(user,
                new StartSessionRequest(memberId, deviceId, billingRuleId, false), "FACE"));
    }

    private ApiResponse<Map<String, Object>> startInternal(
            AuthenticatedUser user, StartSessionRequest request, String authMethod) {
        Map<String, Object> member = requireRow(
                "SELECT status FROM member_info WHERE id = ? AND deleted = 0 FOR UPDATE", request.memberId(), "会员不存在");
        if (!"ACTIVE".equals(member.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员账户当前不可上机");
        }
        Integer runningCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM machine_session WHERE member_id = ? AND status = 'RUNNING'",
                Integer.class, request.memberId());
        if (runningCount != null && runningCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该会员已有进行中的上机会话");
        }
        Map<String, Object> device = requireRow(
                "SELECT status, hourly_rate_hint FROM device_info WHERE id = ? AND deleted = 0 FOR UPDATE", request.deviceId(), "机位不存在");
        if (!"IDLE".equals(device.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选机位当前不可用");
        }
        Integer deviceSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM machine_session WHERE device_id = ? AND status = 'RUNNING'",
                Integer.class, request.deviceId());
        if (deviceSessions != null && deviceSessions > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该机位已有未结束的会话，请先结算");
        }
        Map<String, Object> rule = requireRow("SELECT * FROM billing_rule WHERE id = ? FOR UPDATE",
                request.billingRuleId(), "计费规则不存在");
        if (!"ENABLED".equals(rule.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计费规则已停用");
        }
        BigDecimal areaMinimum = (BigDecimal) device.get("hourly_rate_hint");
        if (areaMinimum != null && ((BigDecimal) rule.get("price_per_hour")).compareTo(areaMinimum) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选规则低于该区域最低小时价 ¥" + areaMinimum + "，请选择适用规则");
        }
        BigDecimal minimumCharge = SessionBilling.amount((BigDecimal) rule.get("price_per_hour"),
                ((Number) rule.get("min_minutes")).intValue(), ((Number) rule.get("billing_unit_minutes")).intValue(), 1);
        Map<String, Object> account = requireRow("SELECT balance FROM member_account WHERE member_id = ? FOR UPDATE",
                request.memberId(), "会员资金账户不存在");
        if (((BigDecimal) account.get("balance")).compareTo(minimumCharge) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "余额不足，最低上机费用 ¥" + minimumCharge + "，请先充值");
        }
        String sessionNo = businessNo("S");
        jdbcTemplate.update("""
                INSERT INTO machine_session
                  (session_no, member_id, device_id, billing_rule_id, start_at, estimated_amount, status, operator_id)
                VALUES (?, ?, ?, ?, NOW(), 0, 'RUNNING', ?)
                """, sessionNo, request.memberId(), request.deviceId(), request.billingRuleId(), user.id());
        Long sessionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO session_billing_snapshot
                  (session_id, price_per_hour, min_minutes, billing_unit_minutes, low_balance_threshold, auth_method)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sessionId, rule.get("price_per_hour"), rule.get("min_minutes"),
                rule.get("billing_unit_minutes"), rule.get("low_balance_threshold"), authMethod);
        jdbcTemplate.update("UPDATE device_info SET status = 'IN_USE' WHERE id = ?", request.deviceId());
        return ApiResponse.success(Map.of("sessionId", sessionId, "sessionNo", sessionNo, "status", "RUNNING"));
    }

    @Transactional
    public ApiResponse<Map<String, Object>> settle(
            Long sessionId,
            AuthenticatedUser user) {
        Map<String, Object> session = requireRow("""
                SELECT member_id, device_id, start_at, status, billing_rule_id
                FROM machine_session WHERE id = ? FOR UPDATE
                """, sessionId, "会话不存在");
        if (!"RUNNING".equals(session.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该会话已经结算");
        }
        // Lock the session alone: locking joined rules here reverses the start-flow lock order.
        Map<String, Object> rate = requireRow("""
                SELECT
                       COALESCE(b.price_per_hour, r.price_per_hour) AS price_per_hour,
                       COALESCE(b.min_minutes, r.min_minutes) AS min_minutes,
                       COALESCE(b.billing_unit_minutes, r.billing_unit_minutes) AS billing_unit_minutes
                FROM machine_session s
                JOIN billing_rule r ON r.id = s.billing_rule_id
                LEFT JOIN session_billing_snapshot b ON b.session_id = s.id
                WHERE s.id = ?
                """, sessionId, "会话不存在");
        session.putAll(rate);
        Long memberId = ((Number) session.get("member_id")).longValue();
        Long deviceId = ((Number) session.get("device_id")).longValue();
        long actualMinutes = SessionBilling.minutes(session.get("start_at"), LocalDateTime.now());
        int minMinutes = ((Number) session.get("min_minutes")).intValue();
        int unitMinutes = ((Number) session.get("billing_unit_minutes")).intValue();
        BigDecimal amount = SessionBilling.amount((BigDecimal) session.get("price_per_hour"), minMinutes, unitMinutes, actualMinutes);

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
        jdbcTemplate.update("""
                UPDATE device_info SET status = CASE
                  WHEN EXISTS (SELECT 1 FROM device_fault WHERE device_id = ? AND status <> 'RESOLVED') THEN 'FAULT'
                  WHEN status IN ('FAULT', 'MAINTENANCE', 'DISABLED') THEN status ELSE 'IDLE' END
                WHERE id = ?
                """, deviceId, deviceId);
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
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
                  COALESCE(b.price_per_hour, r.price_per_hour) AS pricePerHour,
                  COALESCE(b.min_minutes, r.min_minutes) AS minMinutes,
                  COALESCE(b.billing_unit_minutes, r.billing_unit_minutes) AS billingUnitMinutes,
                  COALESCE(b.low_balance_threshold, r.low_balance_threshold) AS lowBalanceThreshold,
                  COALESCE(b.auth_method, 'LEGACY') AS authMethod,
                  s.status
                FROM machine_session s
                JOIN member_info m ON m.id = s.member_id
                JOIN device_info d ON d.id = s.device_id
                LEFT JOIN member_account a ON a.member_id = m.id
                JOIN billing_rule r ON r.id = s.billing_rule_id
                LEFT JOIN session_billing_snapshot b ON b.session_id = s.id
                %s
                ORDER BY s.start_at DESC
                """.formatted(condition));
        for (Map<String, Object> row : rows) {
            if ("RUNNING".equals(row.get("status"))) {
                long minutes = SessionBilling.minutes(row.get("startAt"), LocalDateTime.now());
                BigDecimal amount = SessionBilling.amount((BigDecimal) row.get("pricePerHour"),
                        ((Number) row.get("minMinutes")).intValue(), ((Number) row.get("billingUnitMinutes")).intValue(), minutes);
                row.put("durationMinutes", minutes);
                row.put("estimatedAmount", amount);
                BigDecimal available = ((BigDecimal) row.get("balance")).subtract(amount);
                row.put("availableBalance", available);
                row.put("lowBalance", available.compareTo((BigDecimal) row.get("lowBalanceThreshold")) <= 0);
            }
        }
        return rows;
    }

    private Map<String, Object> requireRow(String sql, Long id, String message) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        return rows.get(0);
    }

    private String businessNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public record StartSessionRequest(
            @NotNull Long memberId,
            @NotNull Long deviceId,
            @NotNull Long billingRuleId,
            Boolean faceRequired) {
    }
}
