package com.aieyaan.splynt.clover.dto;

import java.time.OffsetDateTime;

public record CloverSyncResponse(
        String merchantId,
        int received,
        int created,
        int updated,
        int skipped,
        OffsetDateTime synchronizedAt
) {
}