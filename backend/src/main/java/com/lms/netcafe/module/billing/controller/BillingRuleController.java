package com.lms.netcafe.module.billing.controller;

import com.lms.netcafe.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/rules")
public class BillingRuleController {

    private final JdbcTemplate jdbcTemplate;

    public BillingRuleController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT
                  id,
                  rule_name AS ruleName,
                  price_per_hour AS pricePerHour,
                  min_minutes AS minMinutes,
                  billing_unit_minutes AS billingUnitMinutes,
                  low_balance_threshold AS lowBalanceThreshold,
                  status
                FROM billing_rule
                ORDER BY id
                """));
    }

    @PatchMapping("/{ruleId}")
    public ApiResponse<Void> update(@PathVariable Long ruleId, @Valid @RequestBody UpdateRuleRequest request) {
        jdbcTemplate.update("""
                UPDATE billing_rule
                SET price_per_hour = ?, low_balance_threshold = ?, status = ?
                WHERE id = ?
                """, request.pricePerHour(), request.lowBalanceThreshold(), request.status(), ruleId);
        return ApiResponse.success(null);
    }

    public record UpdateRuleRequest(
            @NotNull @DecimalMin("0.01") BigDecimal pricePerHour,
            @NotNull @DecimalMin("0.00") BigDecimal lowBalanceThreshold,
            @NotNull String status) {
    }
}
