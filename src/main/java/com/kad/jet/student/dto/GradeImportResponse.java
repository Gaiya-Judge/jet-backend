package com.kad.jet.student.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GradeImportResponse {


    private UUID importBatchId;
    // Reference to the grade_import_logs record
    // Use this to query what was imported

    private String fileName;
    private Integer totalRecords;
    private Integer successCount;
    private Integer errorCount;
    private List<GradeImportError> errors;
    // Detailed list of what failed and why
    private LocalDateTime importedAt;
}
