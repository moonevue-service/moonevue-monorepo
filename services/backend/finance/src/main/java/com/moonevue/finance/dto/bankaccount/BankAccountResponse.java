package com.moonevue.finance.dto.bankaccount;

import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.enums.AccountType;
import com.moonevue.core.enums.BankType;

import java.time.OffsetDateTime;

public record BankAccountResponse(
        Long id,
        Long tenantId,
        String name,
        String cdAgency,
        String cdAccount,
        String cdAccountDigit,
        BankType bank,
        AccountType accountType,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BankAccountResponse from(BankAccount b) {
        return new BankAccountResponse(
                b.getId(),
                b.getTenant().getId(),
                b.getName(),
                b.getCdAgency(),
                b.getCdAccount(),
                b.getCdAccountDigit(),
                b.getBank(),
                b.getAccountType(),
                b.getActive(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}