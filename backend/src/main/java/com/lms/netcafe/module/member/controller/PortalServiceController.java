package com.lms.netcafe.module.member.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portal/services")
public class PortalServiceController {

    private static final Set<String> CALL_TYPES = Set.of("FRONT_DESK", "CLEANING", "SUPPLIES", "DEVICE_HELP");
    private final JdbcTemplate jdbcTemplate;

    public PortalServiceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(@AuthenticationPrincipal AuthenticatedUser user) {
        Long memberId = requireMemberId(user);
        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM member_account WHERE member_id = ?", BigDecimal.class, memberId);
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT id, product_code AS productCode, product_name AS productName,
                       category, product_type AS productType,
                       pet_species AS petSpecies, pet_breed AS petBreed,
                       expert_role AS expertRole,
                       service_duration_minutes AS serviceDurationMinutes,
                       description, price, stock, status
                FROM shop_product
                WHERE status = 'ENABLED'
                ORDER BY sort_order, id
                """);
        return ApiResponse.success(Map.of("balance", balance, "products", products));
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders(@AuthenticationPrincipal AuthenticatedUser user) {
        Long memberId = requireMemberId(user);
        List<Map<String, Object>> orders = jdbcTemplate.queryForList("""
                SELECT o.id, o.order_no AS orderNo, o.total_amount AS totalAmount, o.status,
                       o.remark, o.paid_at AS paidAt, o.created_at AS createdAt,
                       d.device_code AS deviceCode
                FROM shop_order o
                LEFT JOIN device_info d ON d.id = o.device_id
                WHERE o.member_id = ?
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT 50
                """, memberId);
        attachOrderItems(orders);
        return ApiResponse.success(orders);
    }

    @PostMapping("/orders")
    @Transactional
    public ApiResponse<Map<String, Object>> createOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateOrderRequest request) {
        Long memberId = requireMemberId(user);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        if (quantities.size() > 20 || quantities.values().stream().anyMatch(quantity -> quantity > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单笔订单商品数量超出限制");
        }

        List<Map<String, Object>> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                    SELECT id, product_name, price, stock, status
                    FROM shop_product
                    WHERE id = ?
                    FOR UPDATE
                    """, entry.getKey());
            if (products.isEmpty() || !"ENABLED".equals(products.get(0).get("status"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "所选商品已下架，请刷新后重试");
            }
            Map<String, Object> product = products.get(0);
            int stock = ((Number) product.get("stock")).intValue();
            if (stock < entry.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        product.get("product_name") + "库存不足，当前仅剩 " + stock + " 件");
            }
            BigDecimal unitPrice = (BigDecimal) product.get("price");
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));
            total = total.add(subtotal);
            Map<String, Object> orderItem = new LinkedHashMap<>();
            orderItem.put("productId", entry.getKey());
            orderItem.put("productName", product.get("product_name"));
            orderItem.put("unitPrice", unitPrice);
            orderItem.put("quantity", entry.getValue());
            orderItem.put("subtotal", subtotal);
            orderItems.add(orderItem);
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                "SELECT balance FROM member_account WHERE member_id = ? FOR UPDATE", memberId);
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会员资金账户不存在");
        }
        BigDecimal balanceBefore = (BigDecimal) accounts.get(0).get("balance");
        if (balanceBefore.compareTo(total) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "预存余额不足，当前余额 ¥" + balanceBefore.toPlainString());
        }
        BigDecimal balanceAfter = balanceBefore.subtract(total);
        Long deviceId = currentDeviceId(memberId);
        String orderNo = businessNo("O");
        jdbcTemplate.update("""
                INSERT INTO shop_order
                  (order_no, member_id, device_id, total_amount, status, remark, paid_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, NOW())
                """, orderNo, memberId, deviceId, total, request.remark());
        Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (Map<String, Object> item : orderItems) {
            jdbcTemplate.update("""
                    INSERT INTO shop_order_item
                      (order_id, product_id, product_name, unit_price, quantity, subtotal)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, orderId, item.get("productId"), item.get("productName"), item.get("unitPrice"),
                    item.get("quantity"), item.get("subtotal"));
            jdbcTemplate.update("UPDATE shop_product SET stock = stock - ? WHERE id = ?",
                    item.get("quantity"), item.get("productId"));
        }

        jdbcTemplate.update("""
                UPDATE member_account
                SET balance = ?, total_consume = total_consume + ?, version = version + 1
                WHERE member_id = ?
                """, balanceAfter, total, memberId);
        String consumeNo = businessNo("C");
        jdbcTemplate.update("""
                INSERT INTO consume_record
                  (consume_no, member_id, session_id, consume_type, amount, balance_after, operator_id)
                VALUES (?, ?, NULL, 'SHOP', ?, ?, ?)
                """, consumeNo, memberId, total, balanceAfter, user.id());
        jdbcTemplate.update("""
                INSERT INTO member_account_flow
                  (flow_no, member_id, related_id, related_type, change_amount,
                   balance_before, balance_after, operator_id, remark)
                VALUES (?, ?, ?, 'PURCHASE', ?, ?, ?, ?, ?)
                """, businessNo("F"), memberId, orderId, total.negate(), balanceBefore, balanceAfter,
                user.id(), "食品零售订单 " + orderNo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", orderNo);
        result.put("totalAmount", total);
        result.put("balance", balanceAfter);
        result.put("status", "PENDING");
        return ApiResponse.success(result);
    }

    @GetMapping("/calls")
    public ApiResponse<List<Map<String, Object>>> calls(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT c.id, c.call_no AS callNo, c.call_type AS callType, c.message, c.status,
                       c.handled_at AS handledAt, c.created_at AS createdAt,
                       d.device_code AS deviceCode
                FROM service_call c
                LEFT JOIN device_info d ON d.id = c.device_id
                WHERE c.member_id = ?
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT 50
                """, requireMemberId(user)));
    }

    @PostMapping("/calls")
    @Transactional
    public ApiResponse<Map<String, Object>> createCall(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateCallRequest request) {
        Long memberId = requireMemberId(user);
        if (!CALL_TYPES.contains(request.callType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的呼叫类型");
        }
        Integer pending = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM service_call
                WHERE member_id = ? AND call_type = ? AND status IN ('PENDING', 'PROCESSING')
                """, Integer.class, memberId, request.callType());
        if (pending != null && pending > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有同类型服务正在处理中，请勿重复呼叫");
        }
        String callNo = businessNo("S");
        jdbcTemplate.update("""
                INSERT INTO service_call (call_no, member_id, device_id, call_type, message)
                VALUES (?, ?, ?, ?, ?)
                """, callNo, memberId, currentDeviceId(memberId), request.callType(), request.message());
        Long callId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.success(Map.of("callId", callId, "callNo", callNo, "status", "PENDING"));
    }

    @GetMapping("/pet-settings")
    @Transactional
    public ApiResponse<Map<String, Object>> petSettings(@AuthenticationPrincipal AuthenticatedUser user) {
        Long memberId = requireMemberId(user);
        ensurePetSetting(memberId);
        return ApiResponse.success(jdbcTemplate.queryForMap("""
                SELECT enabled, always_on_top AS alwaysOnTop, show_bubble AS showBubble,
                       updated_at AS updatedAt
                FROM member_pet_setting WHERE member_id = ?
                """, memberId));
    }

    @PatchMapping("/pet-settings")
    @Transactional
    public ApiResponse<Map<String, Object>> updatePetSettings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PetSettingRequest request) {
        Long memberId = requireMemberId(user);
        jdbcTemplate.update("""
                INSERT INTO member_pet_setting (member_id, enabled, always_on_top, show_bubble)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled),
                  always_on_top = VALUES(always_on_top), show_bubble = VALUES(show_bubble)
                """, memberId, request.enabled(), request.alwaysOnTop(), request.showBubble());
        return petSettings(user);
    }

    private void attachOrderItems(List<Map<String, Object>> orders) {
        for (Map<String, Object> order : orders) {
            order.put("items", jdbcTemplate.queryForList("""
                    SELECT product_id AS productId, product_name AS productName,
                           unit_price AS unitPrice, quantity, subtotal
                    FROM shop_order_item WHERE order_id = ? ORDER BY id
                    """, order.get("id")));
        }
    }

    private void ensurePetSetting(Long memberId) {
        jdbcTemplate.update("INSERT IGNORE INTO member_pet_setting (member_id) VALUES (?)", memberId);
    }

    private Long currentDeviceId(Long memberId) {
        List<Long> deviceIds = jdbcTemplate.queryForList("""
                SELECT device_id FROM machine_session
                WHERE member_id = ? AND status = 'RUNNING'
                ORDER BY start_at DESC LIMIT 1
                """, Long.class, memberId);
        return deviceIds.isEmpty() ? null : deviceIds.get(0);
    }

    private Long requireMemberId(AuthenticatedUser user) {
        if (user == null || user.memberId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定会员资料");
        }
        return user.memberId();
    }

    private String businessNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public record OrderItemRequest(@NotNull Long productId, @Min(1) @Max(20) int quantity) {
    }

    public record CreateOrderRequest(
            @NotEmpty @Size(max = 20) List<@Valid OrderItemRequest> items,
            @Size(max = 255) String remark) {
    }

    public record CreateCallRequest(
            @NotBlank String callType,
            @Size(max = 255) String message) {
    }

    public record PetSettingRequest(
            @NotNull Boolean enabled,
            @NotNull Boolean alwaysOnTop,
            @NotNull Boolean showBubble) {
    }
}
