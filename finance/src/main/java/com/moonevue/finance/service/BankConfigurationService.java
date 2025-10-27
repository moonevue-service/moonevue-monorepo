package com.moonevue.finance.service;

import com.moonevue.finance.dto.bankconfig.BankConfigurationRequest;
import com.moonevue.finance.dto.bankconfig.BankConfigurationResponse;
import com.moonevue.finance.dto.bankconfig.BankConfigurationUpdateRequest;
import com.moonevue.finance.dto.bankconfig.CertificateUploadResponse;
import com.moonevue.finance.exception.NotFoundException;
import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Contractor;
import com.moonevue.core.enums.Environment;
import com.moonevue.core.repository.BankConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class BankConfigurationService {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final ContractorService contractorService;
    private final BankAccountService bankAccountService;
    private final CertificateStorageService certificateStorageService;

    @Transactional
    public BankConfigurationResponse create(Long contractorId, Long bankAccountId, BankConfigurationRequest req) {
        Contractor contractor = contractorService.getEntity(contractorId);
        BankAccount bankAccount = bankAccountService.getEntity(bankAccountId);
        if (!bankAccount.getContractor().getId().equals(contractorId)) {
            throw new NotFoundException("Bank account does not belong to contractor");
        }

        Environment env = req.environment();
        bankConfigurationRepository.findByContractorIdAndBankAccountIdAndEnvironment(contractorId, bankAccountId, env)
                .ifPresent(existing -> { throw new IllegalStateException("Configuration already exists for this environment"); });

        BankConfiguration cfg = new BankConfiguration();
        cfg.setContractor(contractor);
        cfg.setBankAccount(bankAccount);
        cfg.setEnvironment(env);
        cfg.setIsActive(req.isActive() == null ? Boolean.TRUE : req.isActive());
        cfg.setWebhookUrl(req.webhookUrl());
        cfg.setExtraConfig(req.extraConfig() == null ? java.util.Map.of() : req.extraConfig());

        bankConfigurationRepository.save(cfg);
        return BankConfigurationResponse.from(cfg);
    }

    @Transactional(readOnly = true)
    public BankConfiguration getEntity(Long id) {
        return bankConfigurationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Bank configuration not found: " + id));
    }

    @Transactional
    public BankConfigurationResponse update(Long contractorId, Long bankAccountId, Long configId, BankConfigurationUpdateRequest req) {
        BankConfiguration cfg = getEntity(configId);
        validateOwnership(contractorId, bankAccountId, cfg);

        if (req.isActive() != null) cfg.setIsActive(req.isActive());
        if (req.webhookUrl() != null) cfg.setWebhookUrl(req.webhookUrl());
        if (req.extraConfig() != null) cfg.setExtraConfig(req.extraConfig());

        return BankConfigurationResponse.from(cfg);
    }

    @Transactional
    public CertificateUploadResponse uploadCertificate(Long contractorId, Long bankAccountId, Long configId,
                                                       MultipartFile file, String password) throws IOException {
        BankConfiguration cfg = getEntity(configId);
        validateOwnership(contractorId, bankAccountId, cfg);

        String path = certificateStorageService.storeCertificate(contractorId, configId, file);
        cfg.setCertificatePath(path);
        cfg.setCertificatePassword(password);

        String masked = path.replaceAll("(?s).+?([^/\\\\]+)$", "***$1");
        return new CertificateUploadResponse(cfg.getId(), masked);
    }

    private void validateOwnership(Long contractorId, Long bankAccountId, BankConfiguration cfg) {
        if (!cfg.getContractor().getId().equals(contractorId)) {
            throw new NotFoundException("Configuration does not belong to contractor");
        }
        if (!cfg.getBankAccount().getId().equals(bankAccountId)) {
            throw new NotFoundException("Configuration does not belong to bank account");
        }
    }
}
