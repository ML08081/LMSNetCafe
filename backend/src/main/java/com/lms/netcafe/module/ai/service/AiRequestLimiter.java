package com.lms.netcafe.module.ai.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiRequestLimiter {
    private record Window(long minute, int count) {}
    private final Map<Long, Window> windows = new LinkedHashMap<>();
    public synchronized void check(Long userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
        Window previous = windows.get(userId);
        int count = previous == null ? 0 : previous.count();
        if (count >= 10 || (previous == null && windows.size() >= 2048)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求较频繁，请稍后再试");
        }
        windows.put(userId, new Window(minute, count + 1));
    }
}
