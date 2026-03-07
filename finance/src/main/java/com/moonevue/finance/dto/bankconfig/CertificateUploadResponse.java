package com.moonevue.finance.dto.bankconfig;

import java.time.Instant;

public record CertificateUploadResponse(
        Long configurationId,
        String maskedPath,
        String originalFilename,
        Instant earliestExpiry
) {}
