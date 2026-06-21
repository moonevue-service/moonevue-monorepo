package com.moonevue.gateway.controller;

import com.moonevue.core.enums.BankType;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.service.policy.DebtorRequirementPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expõe ao frontend as regras (capabilities) de cada provedor de pagamento, para
 * que os formulários reflitam 1:1 a política do backend — em especial quando o
 * devedor/pagador é obrigatório por tipo de cobrança.
 */
@RestController
@RequestMapping("/capabilities")
public class CapabilitiesController {

    private final DebtorRequirementPolicy debtorRequirementPolicy;

    public CapabilitiesController(DebtorRequirementPolicy debtorRequirementPolicy) {
        this.debtorRequirementPolicy = debtorRequirementPolicy;
    }

    /** Requisitos de devedor por instrumento para todos os provedores conhecidos. */
    @GetMapping
    public ResponseEntity<?> all() {
        Map<String, Object> providers = new LinkedHashMap<>();
        for (BankType provider : BankType.values()) {
            providers.put(provider.name(), buildProvider(provider));
        }
        return ResponseEntity.ok(Map.of("providers", providers));
    }

    /** Requisitos de devedor por instrumento para um provedor específico. */
    @GetMapping("/{provider}")
    public ResponseEntity<?> forProvider(@PathVariable("provider") String provider) {
        BankType bankType;
        try {
            bankType = BankType.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Provedor não suportado: " + provider));
        }
        return ResponseEntity.ok(buildProvider(bankType));
    }

    private Map<String, Object> buildProvider(BankType provider) {
        Map<ChargeRequestDTO.Instrument, Boolean> requirements = debtorRequirementPolicy.requirementsFor(provider);
        Map<String, Boolean> debtorRequired = new LinkedHashMap<>();
        requirements.forEach((instrument, required) -> debtorRequired.put(instrument.name(), required));
        return Map.of(
                "provider", provider.name(),
                "debtorRequired", debtorRequired
        );
    }
}
