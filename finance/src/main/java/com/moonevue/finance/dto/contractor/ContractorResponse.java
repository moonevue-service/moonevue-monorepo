package com.moonevue.finance.dto.contractor;

import com.moonevue.core.entity.Contractor;
import com.moonevue.core.enums.PersonType;

import java.time.OffsetDateTime;

public record ContractorResponse(
        Long id,
        String name,
        String businessName,
        PersonType personType,
        String cpfCnpj,
        String contactEmail,
        String phone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ContractorResponse from(Contractor c) {
        return new ContractorResponse(
                c.getId(),
                c.getName(),
                c.getBusinessName(),
                c.getPersonType(),
                c.getCpfCnpj(),
                c.getContactEmail(),
                c.getPhone(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
