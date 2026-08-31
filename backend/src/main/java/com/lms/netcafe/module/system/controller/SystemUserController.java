package com.lms.netcafe.module.system.controller;

import com.lms.netcafe.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/system/users")
public class SystemUserController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public SystemUserController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT
                  u.id,
                  u.username,
                  u.real_name AS realName,
                  u.phone,
                  m.member_no AS memberNo,
                  u.status,
                  u.last_login_at AS lastLoginAt,
                  GROUP_CONCAT(r.id ORDER BY r.id) AS roleIds,
                  GROUP_CONCAT(r.role_code ORDER BY r.id SEPARATOR ',') AS roleCodes,
                  GROUP_CONCAT(r.role_name ORDER BY r.id SEPARATOR ', ') AS roleNames
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON ur.user_id = u.id
                LEFT JOIN sys_role r ON r.id = ur.role_id
                LEFT JOIN member_info m ON m.id = u.member_id
                WHERE u.deleted = 0
                GROUP BY u.id, u.username, u.real_name, u.phone, m.member_no, u.status, u.last_login_at
                ORDER BY u.id
                """));
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT id, role_code AS roleCode, role_name AS roleName
                FROM sys_role
                WHERE status = 'ENABLED'
                ORDER BY id
                """));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody SaveUserRequest request) {
        ensureUsernameAvailable(request.username(), null);
        ensureMemberAvailable(request.memberId(), null);
        validateRole(request.roleId());
        validateStatus(request.status());
        validateRoleBinding(request.roleId(), request.memberId());

        jdbcTemplate.update("""
                INSERT INTO sys_user (member_id, username, password_hash, real_name, phone, status, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, request.memberId(), request.username(), passwordEncoder.encode(request.password()),
                request.realName(), request.phone(), request.status());
        Long userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, request.roleId());
        return ApiResponse.success(Map.of("id", userId));
    }

    @PatchMapping("/{userId}")
    @Transactional
    public ApiResponse<Void> update(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
        ensureUserExists(userId);
        ensureMemberAvailable(request.memberId(), userId);
        validateRole(request.roleId());
        validateStatus(request.status());
        validateRoleBinding(request.roleId(), request.memberId());

        jdbcTemplate.update("""
                UPDATE sys_user
                SET member_id = ?, real_name = ?, phone = ?, status = ?
                WHERE id = ? AND deleted = 0
                """, request.memberId(), request.realName(), request.phone(), request.status(), userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, request.roleId());
        return ApiResponse.success(null);
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId, @Valid @RequestBody StatusRequest request) {
        ensureUserExists(userId);
        validateStatus(request.status());
        jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ? AND deleted = 0", request.status(), userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId, @Valid @RequestBody ResetPasswordRequest request) {
        ensureUserExists(userId);
        jdbcTemplate.update("UPDATE sys_user SET password_hash = ? WHERE id = ? AND deleted = 0",
                passwordEncoder.encode(request.password()), userId);
        return ApiResponse.success(null);
    }

    private void ensureUserExists(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0", Integer.class, userId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    private void ensureUsernameAvailable(String username, Long excludeUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user
                WHERE username = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                """, Integer.class, username, excludeUserId, excludeUserId);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        }
    }

    private void ensureMemberAvailable(Long memberId, Long excludeUserId) {
        if (memberId == null) {
            return;
        }
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_info WHERE id = ? AND deleted = 0", Integer.class, memberId);
        if (memberCount == null || memberCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "绑定会员不存在");
        }
        Integer userCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user
                WHERE member_id = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                """, Integer.class, memberId, excludeUserId, excludeUserId);
        if (userCount != null && userCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该会员已绑定登录账号");
        }
    }

    private void validateRole(Long roleId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE id = ? AND status = 'ENABLED'", Integer.class, roleId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不可用");
        }
    }

    private void validateRoleBinding(Long roleId, Long memberId) {
        if (Long.valueOf(3L).equals(roleId) && memberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "普通用户账号必须绑定会员");
        }
        if (!Long.valueOf(3L).equals(roleId) && memberId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理端账号不绑定会员");
        }
    }

    private void validateStatus(String status) {
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户状态不合法");
        }
    }

    public record SaveUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String realName,
            String phone,
            Long memberId,
            @NotBlank String status,
            @NotNull Long roleId) {
    }

    public record UpdateUserRequest(
            @NotBlank String realName,
            String phone,
            Long memberId,
            @NotBlank String status,
            @NotNull Long roleId) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record ResetPasswordRequest(@NotBlank String password) {
    }
}
