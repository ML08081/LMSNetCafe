package com.lms.netcafe.module.member.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/frontdesk/services")
public class FrontdeskServiceController {

    private static final Set<String> ORDER_STATUSES = Set.of(
            "PENDING", "PREPARING", "DELIVERING", "COMPLETED", "CANCELLED");
    private static final Set<String> CALL_STATUSES = Set.of("PENDING", "PROCESSING", "COMPLETED", "CANCELLED");
    private final JdbcTemplate jdbcTemplate;

    public FrontdeskServiceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders() {
        List<Map<String, Object>> orders = jdbcTemplate.queryForList("""
                SELECT o.id, o.order_no AS orderNo, m.member_no AS memberNo, m.name AS memberName,
                       d.device_code AS deviceCode, o.total_amount AS totalAmount, o.status,
                       o.remark, o.paid_at AS paidAt, o.created_at AS createdAt
                FROM shop_order o
                JOIN member_info m ON m.id = o.member_id
                LEFT JOIN device_info d ON d.id = o.device_id
                ORDER BY FIELD(o.status, 'PENDING', 'PREPARING', 'DELIVERING', 'COMPLETED', 'CANCELLED'),
                         o.created_at DESC
                LIMIT 100
                """);
        for (Map<String, Object> order : orders) {
            order.put("items", jdbcTemplate.queryForList("""
                    SELECT product_name AS productName, unit_price AS unitPrice, quantity, subtotal
                    FROM shop_order_item WHERE order_id = ? ORDER BY id
                    """, order.get("id")));
        }
        return ApiResponse.success(orders);
    }

    @PatchMapping("/orders/{id}/status")
    @Transactional
    public ApiResponse<Map<String, Object>> updateOrderStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StatusRequest request) {
        if (!ORDER_STATUSES.contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单状态无效");
        }
        List<Map<String, Object>> currentOrders = jdbcTemplate.queryForList("""
                SELECT member_id, total_amount, status FROM shop_order WHERE id = ? FOR UPDATE
                """, id);
        if (currentOrders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Map<String, Object> currentOrder = currentOrders.get(0);
        String currentStatus = (String) currentOrder.get("status");
        if (!canChangeOrderStatus(currentStatus, request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "订单不能从“" + currentStatus + "”变更为“" + request.status() + "”");
        }
        int updated = jdbcTemplate.update("""
                UPDATE shop_order SET status = ?, handled_by = ?,
                  completed_at = CASE WHEN ? = 'COMPLETED' THEN NOW() ELSE completed_at END
                WHERE id = ?
                """, request.status(), user.id(), request.status(), id);
        if ("CANCELLED".equals(request.status())) {
            refundOrder(id, currentOrder, user.id());
        }
        return ApiResponse.success(Map.of("id", id, "status", request.status()));
    }

    @GetMapping("/calls")
    public ApiResponse<List<Map<String, Object>>> calls() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT c.id, c.call_no AS callNo, c.call_type AS callType, c.message, c.status,
                       m.member_no AS memberNo, m.name AS memberName, d.device_code AS deviceCode,
                       c.handled_at AS handledAt, c.created_at AS createdAt
                FROM service_call c
                JOIN member_info m ON m.id = c.member_id
                LEFT JOIN device_info d ON d.id = c.device_id
                ORDER BY FIELD(c.status, 'PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED'),
                         c.created_at DESC
                LIMIT 100
                """));
    }

    @PatchMapping("/calls/{id}/status")
    @Transactional
    public ApiResponse<Map<String, Object>> updateCallStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StatusRequest request) {
        if (!CALL_STATUSES.contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务状态无效");
        }
        List<String> currentStatuses = jdbcTemplate.queryForList(
                "SELECT status FROM service_call WHERE id = ? FOR UPDATE", String.class, id);
        if (currentStatuses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "服务呼叫不存在");
        }
        if (!canChangeCallStatus(currentStatuses.get(0), request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已结束的服务呼叫不能再次变更");
        }
        int updated = jdbcTemplate.update("""
                UPDATE service_call SET status = ?, handled_by = ?,
                  handled_at = CASE WHEN ? IN ('COMPLETED', 'CANCELLED') THEN NOW() ELSE handled_at END
                WHERE id = ?
                """, request.status(), user.id(), request.status(), id);
        return ApiResponse.success(Map.of("id", id, "status", request.status()));
    }

    private boolean canChangeOrderStatus(String current, String target) {
        if (current.equals(target)) {
            return true;
        }
        return switch (current) {
            case "PENDING" -> Set.of("PREPARING", "CANCELLED").contains(target);
            case "PREPARING" -> Set.of("DELIVERING", "COMPLETED", "CANCELLED").contains(target);
            case "DELIVERING" -> "COMPLETED".equals(target);
            default -> false;
        };
    }

    private boolean canChangeCallStatus(String current, String target) {
        if (current.equals(target)) {
            return true;
        }
        return switch (current) {
            case "PENDING" -> Set.of("PROCESSING", "COMPLETED", "CANCELLED").contains(target);
            case "PROCESSING" -> Set.of("COMPLETED", "CANCELLED").contains(target);
            default -> false;
        };
    }

    private void refundOrder(Long orderId, Map<String, Object> order, Long operatorId) {
        Long memberId = ((Number) order.get("member_id")).longValue();
        BigDecimal amount = (BigDecimal) order.get("total_amount");
        BigDecimal balanceBefore = jdbcTemplate.queryForObject(
                "SELECT balance FROM member_account WHERE member_id = ? FOR UPDATE", BigDecimal.class, memberId);
        BigDecimal balanceAfter = balanceBefore.add(amount);
        jdbcTemplate.update("""
                UPDATE member_account
                SET balance = ?, total_consume = GREATEST(0, total_consume - ?), version = version + 1
                WHERE member_id = ?
                """, balanceAfter, amount, memberId);
        jdbcTemplate.update("""
                UPDATE shop_product p
                JOIN shop_order_item i ON i.product_id = p.id
                SET p.stock = p.stock + i.quantity
                WHERE i.order_id = ?
                """, orderId);
        jdbcTemplate.update("""
                INSERT INTO consume_record
                  (consume_no, member_id, session_id, consume_type, amount, balance_after, operator_id)
                VALUES (?, ?, NULL, 'REFUND', ?, ?, ?)
                """, businessNo("C"), memberId, amount.negate(), balanceAfter, operatorId);
        jdbcTemplate.update("""
                INSERT INTO member_account_flow
                  (flow_no, member_id, related_id, related_type, change_amount,
                   balance_before, balance_after, operator_id, remark)
                VALUES (?, ?, ?, 'REFUND', ?, ?, ?, ?, '食品订单取消退款')
                """, businessNo("F"), memberId, orderId, amount, balanceBefore, balanceAfter, operatorId);
    }

    private String businessNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public record StatusRequest(@NotBlank String status) {
    }
}
