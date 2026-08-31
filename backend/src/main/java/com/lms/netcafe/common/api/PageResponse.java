package com.lms.netcafe.common.api;

import java.util.List;

public record PageResponse<T>(List<T> records, long page, long pageSize, long total) {
}
