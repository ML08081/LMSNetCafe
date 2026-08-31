package com.lms.netcafe.module.system.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.system.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password())
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(40100, "用户名或密码错误"));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.success(Map.of("loggedOut", true));
    }

    @GetMapping("/profile")
    public ApiResponse<AuthenticatedUser> profile(Authentication authentication) {
        return ApiResponse.success((AuthenticatedUser) authentication.getPrincipal());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
