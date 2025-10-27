package com.moonevue.finance.dto.bankaccount;

import com.moonevue.core.enums.AccountType;
import com.moonevue.core.enums.BankType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String cdAgency,
        @NotBlank @Size(max = 100) String cdAccount,
        @Size(max = 10) String cdAccountDigit,
        @NotNull BankType bank,
        AccountType accountType,
        Boolean active
) {}
