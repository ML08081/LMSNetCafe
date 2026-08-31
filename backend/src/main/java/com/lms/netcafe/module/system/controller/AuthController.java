package com.lms.netcafe.module.system.controller;

import com.lms.netcafe.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(Map.of(
                "token", "dev-token",
                "username", request.username(),
                "role", "admin"));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.success(Map.of("loggedOut", true));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        return ApiResponse.success(Map.of(
                "username", "admin",
                "realName", "System Administrator",
                "roles", new String[] {"admin"}));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
