package com.kad.jet.program.dto;

import java.util.List;

public record ProgramPageResponse<T>(
        List<T> contents,
        int size,
        int page,
        long totalElement,
        int totalPages,
        boolean first,
        boolean last
) {
}
