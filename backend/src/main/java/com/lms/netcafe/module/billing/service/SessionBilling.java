package com.lms.netcafe.module.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class SessionBilling {
    private SessionBilling() {}

    public static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new ResponseStatusException(HttpStatus.CONFLICT, "会话开始时间无效，请联系管理员");
    }

    public static long minutes(Object start, LocalDateTime now) {
        return minutesFromSeconds(Duration.between(dateTime(start), now).getSeconds());
    }

    public static long minutesFromSeconds(long seconds) {
        return Math.max(1, (Math.max(0, seconds) + 59) / 60);
    }

    public static BigDecimal amount(BigDecimal hourly, int minimum, int unit, long minutes) {
        if (hourly == null || hourly.signum() <= 0 || minimum < 0 || unit <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "计费规则无效，请联系管理员");
        }
        long units = (Math.max(Math.max(1, minutes), minimum) + unit - 1) / unit;
        return hourly.multiply(BigDecimal.valueOf(units * unit))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
