package com.lms.netcafe.module.face.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.module.face.service.FaceRecognitionService;
import com.lms.netcafe.module.face.service.FaceServiceClient.FaceResult;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/faces")
public class FaceController {

    private final JdbcTemplate jdbcTemplate;
    private final FaceRecognitionService faceRecognitionService;

    public FaceController(JdbcTemplate jdbcTemplate, FaceRecognitionService faceRecognitionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.faceRecognitionService = faceRecognitionService;
    }

    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates() {
        return ApiResponse.success(faceRecognitionService.candidates());
    }

    @PostMapping("/enroll")
    public ApiResponse<Map<String, Object>> enroll(
            @RequestParam Long userId,
            @RequestParam MultipartFile image) {
        return ApiResponse.success(faceRecognitionService.enroll(userId, image));
    }

    @PostMapping("/verify")
    public ApiResponse<FaceResult> verify(
            @RequestParam Long userId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam MultipartFile image) {
        return ApiResponse.success(faceRecognitionService.verify(userId, deviceId, image));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Map<String, Object>> remove(@PathVariable Long userId) {
        faceRecognitionService.remove(userId);
        return ApiResponse.success(Map.of("removed", true, "userId", userId));
    }

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, Object>>> logs() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT l.id, u.real_name AS realName, u.username,
                       m.name AS memberName, d.device_code AS deviceCode,
                       l.similarity, l.result, l.fail_reason AS failReason, l.created_at AS createdAt
                FROM face_verify_log l
                LEFT JOIN sys_user u ON u.id = l.sys_user_id
                LEFT JOIN member_info m ON m.id = l.member_id
                LEFT JOIN device_info d ON d.id = l.device_id
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 100
                """));
    }
}
