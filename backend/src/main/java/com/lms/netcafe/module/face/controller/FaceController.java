package com.lms.netcafe.module.face.controller;

import com.lms.netcafe.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/faces")
public class FaceController {

    private final JdbcTemplate jdbcTemplate;

    public FaceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, Object>>> logs() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT
                  l.id,
                  m.name AS memberName,
                  d.device_code AS deviceCode,
                  l.similarity,
                  l.result,
                  l.fail_reason AS failReason,
                  l.created_at AS createdAt
                FROM face_verify_log l
                LEFT JOIN member_info m ON m.id = l.member_id
                LEFT JOIN device_info d ON d.id = l.device_id
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 50
                """));
    }
}
