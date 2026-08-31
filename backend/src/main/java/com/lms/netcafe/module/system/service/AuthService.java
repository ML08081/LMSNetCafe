package com.lms.netcafe.module.system.service;

import com.lms.netcafe.common.security.AuthTokenService;
import com.lms.netcafe.common.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, AuthTokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public Optional<Map<String, Object>> login(String username, String password) {
        return loadUserWithPassword(username)
                .filter(user -> "ENABLED".equals(user.status()))
                .filter(user -> passwordEncoder.matches(password, user.passwordHash()))
                .map(user -> {
                    AuthenticatedUser profile = loadUser(username).orElseThrow();
                    return Map.of(
                            "token", tokenService.createToken(username),
                            "user", profile);
                });
    }

    public Optional<AuthenticatedUser> loadUser(String username) {
        return queryUser(username).map(row -> new AuthenticatedUser(
                ((Number) row.get("id")).longValue(),
                nullableLong(row.get("member_id")),
                (String) row.get("username"),
                (String) row.get("real_name"),
                (String) row.get("status"),
                queryRoles(((Number) row.get("id")).longValue()),
                queryPermissions(((Number) row.get("id")).longValue())));
    }

    private Optional<UserPasswordRow> loadUserWithPassword(String username) {
        return queryUser(username)
                .map(row -> new UserPasswordRow(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("username"),
                        (String) row.get("status"),
                        (String) row.get("password_hash")));
    }

    private Optional<Map<String, Object>> queryUser(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, member_id, username, password_hash, real_name, status
                FROM sys_user
                WHERE username = ? AND deleted = 0
                LIMIT 1
                """, username);
        return rows.stream().findFirst();
    }

    private List<String> queryRoles(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT r.role_code
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.status = 'ENABLED'
                ORDER BY r.id
                """, String.class, userId);
    }

    private List<String> queryPermissions(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT p.permission_code
                FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                JOIN sys_user_role ur ON ur.role_id = rp.role_id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.status = 'ENABLED'
                GROUP BY p.id, p.permission_code, p.sort_order
                ORDER BY p.sort_order, p.id
                """, String.class, userId);
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private record UserPasswordRow(Long id, String username, String status, String passwordHash) {
    }
}
