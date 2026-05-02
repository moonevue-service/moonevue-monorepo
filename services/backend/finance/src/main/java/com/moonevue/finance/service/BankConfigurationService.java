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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Enumeration;

@Service
@RequiredArgsConstructor
public class BankConfigurationService {

    private final BankConfigurationRepository bankConfigs;
    private final BankAccountRepository bankAccounts;
    private final CertificateStorageService certificateStorageService;

    @Transactional(readOnly = true)
    public java.util.List<BankConfigurationResponse> list(Long tenantId, Long bankAccountId) {
        bankAccounts.findByIdAndTenantId(bankAccountId, tenantId)
                .orElseThrow(() -> new NotFoundException("Bank account not found for tenant"));
        return bankConfigs.findByTenantIdAndBankAccountId(tenantId, bankAccountId)
                .stream().map(BankConfigurationResponse::from).toList();
    }

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

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de certificado vazio");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "cert.p12";
        String lower = originalName.toLowerCase();
        if (!(lower.endsWith(".p12") || lower.endsWith(".pfx") || lower.endsWith(".pem"))) {
            throw new IllegalArgumentException("Certificado deve ser .p12 ou .pfx");
        }

        // Carrega em memória para validar antes de salvar
        byte[] bytes = file.getBytes();
        char[] pass = password != null ? password.toCharArray() : new char[0];
        validatePkcs12(bytes, pass);

        // Persiste o arquivo físico
        String path = certificateStorageService.storeCertificate(tenantId, configId, file);
        cfg.setCertificatePath(path);
        cfg.setCertificatePassword(password);

        // (Opcional) recalcula validade para retornar informação
        Instant earliestExpiry = extractEarliestExpiry(bytes, pass);

        String masked = path.replaceAll("(?s).+?([^/\\\\]+)$", "***$1");
        return new CertificateUploadResponse(cfg.getId(), masked, originalName, earliestExpiry);
    }

    private void validateOwnership(Long tenantId, Long bankAccountId, BankConfiguration cfg) {
        if (!cfg.getTenant().getId().equals(tenantId)) {
            throw new NotFoundException("Configuration does not belong to tenant");
        }
        if (!cfg.getBankAccount().getId().equals(bankAccountId)) {
            throw new NotFoundException("Configuration does not belong to bank account");
        }
    }

    private void validatePkcs12(byte[] bytes, char[] pass) {
        Exception last = null;
        for (char[] attempt : candidatePasswords(pass)) {
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (var in = new ByteArrayInputStream(bytes)) {
                    ks.load(in, attempt);
                }
                if (!ks.aliases().hasMoreElements()) {
                    throw new IllegalArgumentException("PKCS12 sem aliases");
                }
                return; // válido
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalArgumentException("Falha ao validar PKCS12: " + (last != null ? last.getMessage() : "erro desconhecido"), last);
    }

    /** Retorna as senhas a tentar na ordem: a informada, null, empty. Sem duplicatas. */
    private char[][] candidatePasswords(char[] pass) {
        if (pass == null || pass.length == 0) {
            return new char[][]{null, new char[0]};
        }
        return new char[][]{pass, null, new char[0]};
    }

    private Instant extractEarliestExpiry(byte[] bytes, char[] pass) {
        for (char[] attempt : candidatePasswords(pass)) {
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (var in = new ByteArrayInputStream(bytes)) {
                    ks.load(in, attempt);
                }
                Instant earliest = null;
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    var alias = aliases.nextElement();
                    var cert = ks.getCertificate(alias);
                    if (cert instanceof java.security.cert.X509Certificate x509) {
                        Instant exp = x509.getNotAfter().toInstant();
                        if (earliest == null || exp.isBefore(earliest)) earliest = exp;
                    }
                }
                return earliest;
            } catch (Exception ignored) {
                // tenta próxima senha
            }
        }
        return null;
    }
}