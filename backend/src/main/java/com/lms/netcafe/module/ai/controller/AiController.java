package com.lms.netcafe.module.ai.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.ai.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AiController {
    private final AssistantService assistant;
    private final DailyReportService reports;
    private final AiModelClient model;
    private final AiRequestLimiter limiter;
    public AiController(AssistantService assistant, DailyReportService reports, AiModelClient model, AiRequestLimiter limiter) {
        this.assistant=assistant; this.reports=reports; this.model=model; this.limiter=limiter;
    }
    @GetMapping("/ai/status")
    public ApiResponse<Map<String,Object>> status() { return ApiResponse.success(model.status()); }
    @PostMapping("/portal/assistant")
    public ApiResponse<Map<String,Object>> ask(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AssistantService.Question question) {
        limiter.check(user.id()); return ApiResponse.success(assistant.ask(user,question));
    }
    @GetMapping("/ai/reports")
    public ApiResponse<List<Map<String,Object>>> history() { return ApiResponse.success(reports.history()); }
    @GetMapping("/ai/reports/{date}")
    public ApiResponse<Map<String,Object>> report(@PathVariable LocalDate date) { return ApiResponse.success(reports.get(date)); }
    @PostMapping("/ai/reports/generate")
    public ApiResponse<Map<String,Object>> generate(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ReportRequest request) {
        limiter.check(user.id()); return ApiResponse.success(reports.generate(request.date(),user.id()));
    }
    public record ReportRequest(@NotNull LocalDate date) {}
}
