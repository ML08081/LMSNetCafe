package com.lms.netcafe.module.system.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.system.service.AuthService;
import com.lms.netcafe.module.face.service.FaceRecognitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final FaceRecognitionService faceRecognitionService;

    public AuthController(AuthService authService, FaceRecognitionService faceRecognitionService) {
        this.authService = authService;
        this.faceRecognitionService = faceRecognitionService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password())
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(40100, "用户名或密码错误"));
    }

    @PostMapping("/face-login")
    public ApiResponse<Map<String, Object>> faceLogin(@RequestParam MultipartFile image) {
        var result = faceRecognitionService.identify(image);
        if (!result.matched() || result.subjectId() == null) {
            return ApiResponse.fail(40101, result.message());
        }
        return authService.loginByUserId(result.subjectId())
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(40102, "识别到的用户不可登录"));
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
