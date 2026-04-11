package com.solvit.internship_system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkOperationResponseDTO {

    private int updated;  // for bulk-deactivate
    private int deleted;  // for bulk-delete
    private int skipped;  // for bulk-delete (e.g. admins skipped)
    private String message;
}
