package com.lms.netcafe.module.face.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FaceServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FaceServiceClient(@Value("${lms.face-service-url}") String baseUrl, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    public FaceResult enroll(Long userId, Long memberId, byte[] image, String filename, String contentType) {
        return exchange("/face-service/internal/enroll", userId, memberId, image, contentType);
    }

    public FaceResult verify(Long userId, Long memberId, byte[] image, String filename, String contentType) {
        return exchange("/face-service/internal/verify", userId, memberId, image, contentType);
    }

    public FaceResult identify(byte[] image, String filename, String contentType) {
        return exchange("/face-service/internal/identify", null, null, image, contentType);
    }

    public void remove(Long userId) {
        try {
            restClient.delete()
                    .uri("/face-service/subjects/{subjectId}", userId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ignored) {
            // The database profile remains the source of truth during a temporary service outage.
        }
    }

    private FaceResult exchange(
            String path,
            Long userId,
            Long memberId,
            byte[] image,
            String contentType) {
        try {
            String responseBody = restClient.post()
                    .uri(builder -> {
                        builder.path(path);
                        if (userId != null) {
                            builder.queryParam("subject_id", userId);
                        }
                        if (memberId != null) {
                            builder.queryParam("member_id", memberId);
                        }
                        return builder.build();
                    })
                    .contentType(imageMediaType(contentType))
                    .body(image)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "人脸服务返回为空");
            }
            return objectMapper.readValue(responseBody, FaceResult.class);
        } catch (HttpClientErrorException.UnprocessableEntity ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, extractDetail(ex), ex);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "人脸服务响应格式错误", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "人脸识别服务暂不可用", ex);
        }
    }

    private MediaType imageMediaType(String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (IllegalArgumentException ignored) {
                // Browser captures default to JPEG when no usable content type is supplied.
            }
        }
        return MediaType.IMAGE_JPEG;
    }

    private String extractDetail(HttpClientErrorException ex) {
        try {
            JsonNode detail = objectMapper.readTree(ex.getResponseBodyAsString()).path("detail");
            if (detail.isTextual() && !detail.asText().isBlank()) {
                return detail.asText();
            }
        } catch (Exception ignored) {
            // Fall through to a stable user-facing message.
        }
        return "人脸图像未通过检测";
    }

    public record FaceResult(
            boolean matched,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("member_id") Long memberId,
            double similarity,
            @JsonProperty("quality_score") Double qualityScore,
            @JsonProperty("feature_ref") String featureRef,
            String message) {
    }
}
