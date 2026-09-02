package com.lms.netcafe.module.member.controller;

import com.lms.netcafe.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Set<String> PRODUCT_TYPES = Set.of("MERCHANDISE", "PET_COMPANION", "EXPERT_COMPANION");
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");
    private final JdbcTemplate jdbcTemplate;

    public ProductController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        String keywordFilter = "%" + blankToEmpty(keyword) + "%";
        String typeFilter = blankToEmpty(productType);
        String categoryFilter = blankToEmpty(category);
        String statusFilter = blankToEmpty(status);
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT id, product_code AS productCode, product_name AS productName,
                       category, product_type AS productType,
                       pet_species AS petSpecies, pet_breed AS petBreed,
                       expert_role AS expertRole,
                       service_duration_minutes AS serviceDurationMinutes,
                       description, price, stock, status, sort_order AS sortOrder,
                       created_at AS createdAt, updated_at AS updatedAt
                FROM shop_product
                WHERE (? = '' OR product_type = ?)
                  AND (? = '' OR category = ?)
                  AND (? = '' OR status = ?)
                  AND (? = '%%' OR product_code LIKE ? OR product_name LIKE ?
                       OR category LIKE ? OR pet_breed LIKE ? OR expert_role LIKE ?)
                ORDER BY sort_order, id
                """, typeFilter, typeFilter, categoryFilter, categoryFilter, statusFilter, statusFilter,
                keywordFilter, keywordFilter, keywordFilter, keywordFilter, keywordFilter, keywordFilter));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody ProductRequest request) {
        validateProduct(request);
        int duplicated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shop_product WHERE product_code = ?", Integer.class, request.productCode());
        if (duplicated > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品编码已存在");
        }
        jdbcTemplate.update("""
                INSERT INTO shop_product
                  (product_code, product_name, category, product_type, pet_species, pet_breed,
                   expert_role, service_duration_minutes, description, price, stock, status, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.productCode().trim(), request.productName().trim(), request.category().trim(),
                request.productType().trim(), nullableText(request.petSpecies()), nullableText(request.petBreed()),
                nullableText(request.expertRole()), request.serviceDurationMinutes(), nullableText(request.description()),
                request.price(), request.stock(), request.status(), request.sortOrder());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.success(Map.of("id", id));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        validateProduct(request);
        List<Long> duplicated = jdbcTemplate.queryForList(
                "SELECT id FROM shop_product WHERE product_code = ? AND id <> ? LIMIT 1",
                Long.class, request.productCode(), id);
        if (!duplicated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品编码已存在");
        }
        int updated = jdbcTemplate.update("""
                UPDATE shop_product
                SET product_code = ?, product_name = ?, category = ?, product_type = ?,
                    pet_species = ?, pet_breed = ?, expert_role = ?,
                    service_duration_minutes = ?, description = ?, price = ?,
                    stock = ?, status = ?, sort_order = ?
                WHERE id = ?
                """, request.productCode().trim(), request.productName().trim(), request.category().trim(),
                request.productType().trim(), nullableText(request.petSpecies()), nullableText(request.petBreed()),
                nullableText(request.expertRole()), request.serviceDurationMinutes(), nullableText(request.description()),
                request.price(), request.stock(), request.status(), request.sortOrder(), id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return ApiResponse.success(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        int updated = jdbcTemplate.update("DELETE FROM shop_product WHERE id = ?", id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return ApiResponse.success(Map.of("id", id));
    }

    private void validateProduct(ProductRequest request) {
        if (!PRODUCT_TYPES.contains(request.productType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品类型无效");
        }
        if (!STATUSES.contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品状态无效");
        }
        if ("PET_COMPANION".equals(request.productType())) {
            List<String> missing = new ArrayList<>();
            if (nullableText(request.petSpecies()) == null) {
                missing.add("宠物类型");
            }
            if (nullableText(request.petBreed()) == null) {
                missing.add("宠物品种");
            }
            if (!missing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("、", missing) + "不能为空");
            }
        }
        if ("EXPERT_COMPANION".equals(request.productType()) && nullableText(request.expertRole()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "高手陪玩类型不能为空");
        }
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProductRequest(
            @NotBlank @Size(max = 32) String productCode,
            @NotBlank @Size(max = 64) String productName,
            @NotBlank @Size(max = 32) String category,
            @NotBlank @Size(max = 32) String productType,
            @Size(max = 32) String petSpecies,
            @Size(max = 64) String petBreed,
            @Size(max = 64) String expertRole,
            @Min(0) Integer serviceDurationMinutes,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            @NotNull @Min(0) Integer stock,
            @NotBlank String status,
            @NotNull @Min(0) Integer sortOrder) {
    }
}
