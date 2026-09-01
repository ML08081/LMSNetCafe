package com.lms.netcafe.module.face.service;

import com.lms.netcafe.module.face.service.FaceServiceClient.FaceResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FaceRecognitionService {

    private final JdbcTemplate jdbcTemplate;
    private final FaceServiceClient faceServiceClient;

    public FaceRecognitionService(JdbcTemplate jdbcTemplate, FaceServiceClient faceServiceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.faceServiceClient = faceServiceClient;
    }

    public List<Map<String, Object>> candidates() {
        return jdbcTemplate.queryForList("""
                SELECT u.id AS userId, u.username, u.real_name AS realName, u.member_id AS memberId,
                       r.role_name AS roleName,
                       CASE WHEN fp.id IS NULL OR fp.status <> 'ACTIVE' THEN 0 ELSE 1 END AS enrolled,
                       fp.quality_score AS qualityScore, fp.enrolled_at AS enrolledAt
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                LEFT JOIN face_profile fp ON fp.sys_user_id = u.id
                WHERE u.deleted = 0 AND u.status = 'ENABLED'
                ORDER BY r.id, u.id
                """);
    }

    @Transactional
    public Map<String, Object> enroll(Long userId, MultipartFile image) {
        Map<String, Object> user = requireEnabledUser(userId);
        Long memberId = nullableLong(user.get("member_id"));
        FaceResult result = faceServiceClient.enroll(
                userId, memberId, bytes(image), image.getOriginalFilename(), image.getContentType());
        jdbcTemplate.update("""
                INSERT INTO face_profile
                  (sys_user_id, member_id, feature_ref, image_ref, quality_score, status, enrolled_at)
                VALUES (?, ?, ?, NULL, ?, 'ACTIVE', NOW())
                ON DUPLICATE KEY UPDATE
                  member_id = VALUES(member_id), feature_ref = VALUES(feature_ref), image_ref = NULL,
                  quality_score = VALUES(quality_score), status = 'ACTIVE', enrolled_at = NOW()
                """, userId, memberId, result.featureRef(), result.qualityScore());
        return Map.of(
                "userId", userId,
                "realName", user.get("real_name"),
                "qualityScore", result.qualityScore(),
                "message", result.message());
    }

    public FaceResult verify(Long userId, Long deviceId, MultipartFile image) {
        Map<String, Object> user = requireEnabledUser(userId);
        Long memberId = nullableLong(user.get("member_id"));
        FaceResult result = faceServiceClient.verify(
                userId, memberId, bytes(image), image.getOriginalFilename(), image.getContentType());
        writeLog(userId, memberId, deviceId, result);
        return result;
    }

    public FaceResult identify(MultipartFile image) {
        FaceResult result = faceServiceClient.identify(
                bytes(image), image.getOriginalFilename(), image.getContentType());
        if (!result.matched() || result.subjectId() == null) {
            writeLog(null, null, null, result);
            return result;
        }
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT u.id, u.member_id
                FROM sys_user u
                JOIN face_profile fp ON fp.sys_user_id = u.id AND fp.status = 'ACTIVE'
                WHERE u.id = ? AND u.status = 'ENABLED' AND u.deleted = 0
                LIMIT 1
                """, result.subjectId());
        if (users.isEmpty()) {
            FaceResult disabled = new FaceResult(false, null, null, result.similarity(),
                    result.qualityScore(), null, "识别到的用户不可登录");
            writeLog(result.subjectId(), result.memberId(), null, disabled);
            return disabled;
        }
        Long memberId = nullableLong(users.get(0).get("member_id"));
        writeLog(result.subjectId(), memberId, null, result);
        return result;
    }

    @Transactional
    public void remove(Long userId) {
        jdbcTemplate.update("UPDATE face_profile SET status = 'DISABLED' WHERE sys_user_id = ?", userId);
        faceServiceClient.remove(userId);
    }

    private Map<String, Object> requireEnabledUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, member_id, username, real_name
                FROM sys_user
                WHERE id = ? AND status = 'ENABLED' AND deleted = 0
                LIMIT 1
                """, userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在或已停用");
        }
        return rows.get(0);
    }

    private byte[] bytes(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传摄像头图片");
        }
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传图片", ex);
        }
    }

    private void writeLog(Long userId, Long memberId, Long deviceId, FaceResult result) {
        jdbcTemplate.update("""
                INSERT INTO face_verify_log
                  (sys_user_id, member_id, device_id, similarity, result, fail_reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, memberId, deviceId, result.similarity(),
                result.matched() ? "PASSED" : "FAILED", result.matched() ? null : result.message());
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
