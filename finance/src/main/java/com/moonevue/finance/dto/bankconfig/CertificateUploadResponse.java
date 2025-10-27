package com.moonevue.finance.dto.bankconfig;

public record CertificateUploadResponse(
        Long configurationId,
        String storedPathMasked
) {}
