package com.lms.netcafe.common.security;

import java.util.List;

public record AuthenticatedUser(
        Long id,
        Long memberId,
        String username,
        String realName,
        String status,
        List<String> roles,
        List<String> permissions) {
}
