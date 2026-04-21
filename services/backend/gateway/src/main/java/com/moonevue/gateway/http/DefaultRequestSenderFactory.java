package com.moonevue.gateway.http;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;
import com.moonevue.gateway.mtls.MutualTlsHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fábrica concreta de RequestSender.
 * - Sem cfg (ou sem certificado): usa DefaultRequestSender (sem mTLS).
 * - Com cfg + certificatePath: usa MutualTlsHttpService (mTLS).
 */
@Component
public class DefaultRequestSenderFactory implements RequestSenderFactory {

    private static final Logger log = LoggerFactory.getLogger(DefaultRequestSenderFactory.class);

    private final DefaultRequestSender defaultSender;
    private final MutualTlsHttpService mtlsSender;

    public DefaultRequestSenderFactory(DefaultRequestSender defaultSender,
                                       MutualTlsHttpService mtlsSender) {
        this.defaultSender = defaultSender;
        this.mtlsSender = mtlsSender;
    }

    @Override
    public RequestSender get(BankType type) {
        if (log.isDebugEnabled()) {
            log.debug("RequestSenderFactory.get(type={}) -> defaultSender", type);
        }
        return defaultSender;
    }

    @Override
    public RequestSender get(BankType type, BankConfiguration cfg) {
        boolean hasCert = cfg != null && StringUtils.hasText(cfg.getCertificatePath());
        if (log.isDebugEnabled()) {
            log.debug("RequestSenderFactory.get(type={}, cfgId={}, certPathPresent={})",
                    type,
                    cfg != null ? cfg.getId() : null,
                    hasCert);
        }
        if (hasCert) {
            if (log.isDebugEnabled()) {
                log.debug("-> Using mTLS sender (MutualTlsHttpService) for cfgId={}", cfg.getId());
            }
            return mtlsSender;
        }
        if (log.isDebugEnabled()) {
            log.debug("-> Using default sender (no mTLS) for cfgId={}", cfg != null ? cfg.getId() : null);
        }
        return defaultSender;
    }

    @Override
    public RequestSender getMtls(BankType type, BankConfiguration cfg) {
        if (cfg == null || !StringUtils.hasText(cfg.getCertificatePath())) {
            throw new IllegalArgumentException("mTLS requerido para " + type + " mas BankConfiguration não possui certificatePath.");
        }
        if (log.isDebugEnabled()) {
            log.debug("RequestSenderFactory.getMtls(type={}, cfgId={}) -> mtlsSender", type, cfg.getId());
        }
        return mtlsSender;
    }
}
