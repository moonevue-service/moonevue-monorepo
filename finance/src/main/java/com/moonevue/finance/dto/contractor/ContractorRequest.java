package com.moonevue.finance.dto.contractor;

import com.moonevue.core.enums.PersonType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContractorRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String businessName,
        @NotNull PersonType personType,
        @NotBlank @Size(max = 14) String cpfCnpj,
        @NotBlank @Email @Size(max = 100) String contactEmail,
        @Size(max = 20) String phone
) {}
