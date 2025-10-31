package com.moonevue.finance.service;

import com.moonevue.core.entity.BankAccount;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.enums.Environment;
import com.moonevue.core.repository.BankAccountRepository;
import com.moonevue.core.repository.BankConfigurationRepository;
import com.moonevue.finance.dto.bankconfig.*;
import com.moonevue.finance.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class BankConfigurationService {

    private final BankConfigurationRepository bankConfigs;
    private final BankAccountRepository bankAccounts;
    private final CertificateStorageService certificateStorageService;

    @Transactional
    public BankConfigurationResponse create(Long tenantId, Long bankAccountId, BankConfigurationRequest req) {
        BankAccount bankAccount = bankAccounts.findByIdAndTenantId(bankAccountId, tenantId)
                .orElseThrow(() -> new NotFoundException("Bank account not found for tenant"));

        Environment env = req.environment();
        bankConfigs.findByTenantIdAndBankAccountIdAndEnvironment(tenantId, bankAccountId, env)
                .ifPresent(existing -> { throw new IllegalStateException("Configuration already exists for this environment"); });

        BankConfiguration cfg = new BankConfiguration();
        var t = new Tenant(); t.setId(tenantId);
        cfg.setTenant(t);
        cfg.setBankAccount(bankAccount);
        cfg.setEnvironment(env);
        cfg.setIsActive(req.isActive() == null ? Boolean.TRUE : req.isActive());
        cfg.setWebhookUrl(req.webhookUrl());
        cfg.setExtraConfig(req.extraConfig() == null ? java.util.Map.of() : req.extraConfig());

        bankConfigs.save(cfg);
        return BankConfigurationResponse.from(cfg);
    }

    @Transactional(readOnly = true)
    public BankConfiguration getEntity(Long tenantId, Long configId) {
        return bankConfigs.findById(configId)
                .filter(cfg -> cfg.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Bank configuration not found: " + configId));
    }

    @Transactional
    public BankConfigurationResponse update(Long tenantId, Long bankAccountId, Long configId, BankConfigurationUpdateRequest req) {
        BankConfiguration cfg = getEntity(tenantId, configId);
        validateOwnership(tenantId, bankAccountId, cfg);

        if (req.isActive() != null) cfg.setIsActive(req.isActive());
        if (req.webhookUrl() != null) cfg.setWebhookUrl(req.webhookUrl());
        if (req.extraConfig() != null) cfg.setExtraConfig(req.extraConfig());

        return BankConfigurationResponse.from(cfg);
    }

    @Transactional
    public CertificateUploadResponse uploadCertificate(Long tenantId, Long bankAccountId, Long configId,
                                                       MultipartFile file, String password) throws IOException {
        BankConfiguration cfg = getEntity(tenantId, configId);
        validateOwnership(tenantId, bankAccountId, cfg);

        String path = certificateStorageService.storeCertificate(tenantId, configId, file);
        cfg.setCertificatePath(path);
        cfg.setCertificatePassword(password);

        String masked = path.replaceAll("(?s).+?([^/\\\\]+)$", "***$1");
        return new CertificateUploadResponse(cfg.getId(), masked);
    }

    private void validateOwnership(Long tenantId, Long bankAccountId, BankConfiguration cfg) {
        if (!cfg.getTenant().getId().equals(tenantId)) {
            throw new NotFoundException("Configuration does not belong to tenant");
        }
        if (!cfg.getBankAccount().getId().equals(bankAccountId)) {
            throw new NotFoundException("Configuration does not belong to bank account");
        }
    }
}