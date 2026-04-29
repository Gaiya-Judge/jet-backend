package com.kad.jet.department.dto;

import java.util.List;

public record DepartmentPageResponse<T>(
        List<T> contents,
        int size,
        int page,
        long totalElement,
        int totalPages,
        boolean first,
        boolean last
) {
}
