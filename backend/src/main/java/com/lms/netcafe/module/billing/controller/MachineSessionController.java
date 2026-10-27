package com.lms.netcafe.module.billing.controller;

import com.lms.netcafe.common.api.ApiResponse;
import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.billing.service.MachineSessionService;
import com.lms.netcafe.module.billing.service.MachineSessionService.StartSessionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sessions")
public class MachineSessionController {
    private final MachineSessionService sessions;
    public MachineSessionController(MachineSessionService sessions) { this.sessions = sessions; }
    @GetMapping
    public ApiResponse<List<Map<String,Object>>> list() { return sessions.list(); }
    @GetMapping("/running")
    public ApiResponse<List<Map<String,Object>>> running() { return sessions.running(); }
    @GetMapping("/diagnostics")
    public ApiResponse<Map<String,Object>> diagnostics() { return sessions.diagnostics(); }
    @GetMapping("/{sessionId}/preview")
    public ApiResponse<Map<String,Object>> preview(@PathVariable Long sessionId) { return sessions.preview(sessionId); }
    @PostMapping("/start")
    public ApiResponse<Map<String,Object>> start(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartSessionRequest request) { return sessions.start(user,request); }
    @PostMapping("/start-with-face")
    public ApiResponse<Map<String,Object>> startWithFace(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long memberId, @RequestParam Long deviceId, @RequestParam Long billingRuleId,
            @RequestParam MultipartFile image) { return sessions.startWithFace(user,memberId,deviceId,billingRuleId,image); }
    @PostMapping("/{sessionId}/settle")
    public ApiResponse<Map<String,Object>> settle(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthenticatedUser user) { return sessions.settle(sessionId,user); }
}
