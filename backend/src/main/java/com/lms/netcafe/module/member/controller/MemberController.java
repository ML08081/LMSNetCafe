package com.lms.netcafe.module.member.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final JdbcTemplate jdbcTemplate;

    public MemberController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        String likeKeyword = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String statusFilter = status == null || status.isBlank() ? null : status;
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT
                  m.id,
                  m.member_no AS memberNo,
                  m.name,
                  m.phone,
                  m.id_card_no AS idCardNo,
                  m.level,
                  m.status,
                  a.balance,
                  p.segment,
                  p.churn_risk AS churnRisk,
                  p.spending_power AS spendingPower,
                  p.favorite_games AS favoriteGames,
                  p.preferred_time_slot AS preferredTimeSlot,
                  COALESCE(c.unused_coupons, 0) AS unusedCoupons,
                  CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS faceEnrolled,
                  m.registered_at AS registeredAt
                FROM member_info m
                LEFT JOIN member_account a ON a.member_id = m.id
                LEFT JOIN face_profile f ON f.member_id = m.id AND f.status = 'ACTIVE'
                LEFT JOIN member_operation_profile p ON p.member_id = m.id
                LEFT JOIN (
                  SELECT member_id, COUNT(*) AS unused_coupons
                  FROM member_coupon
                  WHERE status = 'UNUSED' AND expires_at > NOW()
                  GROUP BY member_id
                ) c ON c.member_id = m.id
                WHERE m.deleted = 0
                  AND (? IS NULL OR m.status = ?)
                  AND (
                    ? = '%%'
                    OR m.member_no LIKE ?
                    OR m.name LIKE ?
                    OR m.phone LIKE ?
                  )
                ORDER BY m.id DESC
                """, statusFilter, statusFilter, likeKeyword, likeKeyword, likeKeyword, likeKeyword));
    }

    @GetMapping("/operation/profiles")
    public ApiResponse<List<Map<String, Object>>> operationProfiles(
            @RequestParam(required = false) String churnRisk,
            @RequestParam(required = false) String segment) {
        String riskFilter = churnRisk == null || churnRisk.isBlank() || "全部".equals(churnRisk) ? null : churnRisk;
        String segmentFilter = segment == null || segment.isBlank() || "全部".equals(segment) ? null : segment;
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT m.id AS memberId, m.member_no AS memberNo, m.name, m.level, m.status,
                       a.balance, a.total_consume AS totalConsume,
                       p.favorite_games AS favoriteGames,
                       p.preferred_time_slot AS preferredTimeSlot,
                       p.beverage_preference AS beveragePreference,
                       p.spending_power AS spendingPower,
                       p.churn_risk AS churnRisk,
                       p.segment,
                       p.last_visit_at AS lastVisitAt,
                       DATEDIFF(CURRENT_DATE, DATE(p.last_visit_at)) AS daysSinceLastVisit,
                       p.recommendation,
                       COALESCE(c.unused_coupons, 0) AS unusedCoupons
                FROM member_info m
                JOIN member_account a ON a.member_id = m.id
                LEFT JOIN member_operation_profile p ON p.member_id = m.id
                LEFT JOIN (
                  SELECT member_id, COUNT(*) AS unused_coupons
                  FROM member_coupon
                  WHERE status = 'UNUSED' AND expires_at > NOW()
                  GROUP BY member_id
                ) c ON c.member_id = m.id
                WHERE m.deleted = 0
                  AND (? IS NULL OR p.churn_risk = ?)
                  AND (? IS NULL OR p.segment = ?)
                ORDER BY FIELD(p.churn_risk, 'HIGH', 'MEDIUM', 'LOW'),
                         p.last_visit_at ASC,
                         a.total_consume DESC
                """, riskFilter, riskFilter, segmentFilter, segmentFilter));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody SaveMemberRequest request) {
        validateStatus(request.status());
        validateLevel(request.level());
        ensurePhoneAvailable(request.phone(), null);
        String memberNo = request.memberNo() == null || request.memberNo().isBlank()
                ? businessNo("M")
                : request.memberNo().trim();
        ensureMemberNoAvailable(memberNo, null);

        jdbcTemplate.update("""
                INSERT INTO member_info (member_no, name, phone, id_card_no, level, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, memberNo, request.name(), request.phone(), request.idCardNo(), request.level(), request.status());
        Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO member_account (member_id, balance, total_recharge, total_consume) VALUES (?, 0, 0, 0)",
                memberId);
        return ApiResponse.success(Map.of("id", memberId, "memberNo", memberNo));
    }

    @PatchMapping("/{memberId}")
    public ApiResponse<Void> update(@PathVariable Long memberId, @Valid @RequestBody UpdateMemberRequest request) {
        ensureMemberExists(memberId);
        validateStatus(request.status());
        validateLevel(request.level());
        ensurePhoneAvailable(request.phone(), memberId);
        jdbcTemplate.update("""
                UPDATE member_info
                SET name = ?, phone = ?, id_card_no = ?, level = ?, status = ?
                WHERE id = ? AND deleted = 0
                """, request.name(), request.phone(), request.idCardNo(), request.level(), request.status(), memberId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{memberId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long memberId, @Valid @RequestBody StatusRequest request) {
        ensureMemberExists(memberId);
        validateStatus(request.status());
        jdbcTemplate.update("UPDATE member_info SET status = ? WHERE id = ? AND deleted = 0", request.status(), memberId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{memberId}/recharge")
    @Transactional
    public ApiResponse<Map<String, Object>> recharge(
            @PathVariable Long memberId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RechargeRequest request) {
        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT balance FROM member_account WHERE member_id = ? FOR UPDATE", memberId);
        BigDecimal balanceBefore = (BigDecimal) account.get("balance");
        BigDecimal balanceAfter = balanceBefore.add(request.amount());
        String rechargeNo = businessNo("R");
        String flowNo = businessNo("F");

        jdbcTemplate.update("""
                UPDATE member_account
                SET balance = ?, total_recharge = total_recharge + ?, version = version + 1
                WHERE member_id = ?
                """, balanceAfter, request.amount(), memberId);
        jdbcTemplate.update("""
                INSERT INTO recharge_record
                  (recharge_no, member_id, amount, gift_amount, pay_method, operator_id, remark)
                VALUES (?, ?, ?, 0, ?, ?, ?)
                """, rechargeNo, memberId, request.amount(), request.payMethod(), user.id(), request.remark());
        Long rechargeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO member_account_flow
                  (flow_no, member_id, related_id, related_type, change_amount,
                   balance_before, balance_after, operator_id, remark)
                VALUES (?, ?, ?, 'RECHARGE', ?, ?, ?, ?, ?)
                """, flowNo, memberId, rechargeId, request.amount(), balanceBefore, balanceAfter, user.id(), request.remark());
        return ApiResponse.success(Map.of("rechargeNo", rechargeNo, "balance", balanceAfter));
    }

    private String businessNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void ensureMemberExists(Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_info WHERE id = ? AND deleted = 0", Integer.class, memberId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会员不存在");
        }
    }

    private void ensureMemberNoAvailable(String memberNo, Long excludeMemberId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM member_info
                WHERE member_no = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                """, Integer.class, memberNo, excludeMemberId, excludeMemberId);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员编号已存在");
        }
    }

    private void ensurePhoneAvailable(String phone, Long excludeMemberId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM member_info
                WHERE phone = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                """, Integer.class, phone, excludeMemberId, excludeMemberId);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手机号已被会员使用");
        }
    }

    private void validateStatus(String status) {
        if (!"ACTIVE".equals(status) && !"FROZEN".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会员状态不合法");
        }
    }

    private void validateLevel(String level) {
        if (!"NORMAL".equals(level) && !"VIP".equals(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会员等级不合法");
        }
    }

    public record SaveMemberRequest(
            String memberNo,
            @NotBlank String name,
            @NotBlank String phone,
            String idCardNo,
            @NotBlank String level,
            @NotBlank String status) {
    }

    public record UpdateMemberRequest(
            @NotBlank String name,
            @NotBlank String phone,
            String idCardNo,
            @NotBlank String level,
            @NotBlank String status) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record RechargeRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull String payMethod,
            String remark) {
    }
}
