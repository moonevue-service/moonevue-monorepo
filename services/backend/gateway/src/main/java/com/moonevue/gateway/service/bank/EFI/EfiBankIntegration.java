package com.moonevue.gateway.service.bank.EFI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.core.enums.BankType;
import com.moonevue.core.enums.Environment;
import com.moonevue.gateway.auth.AccessToken;
import com.moonevue.gateway.auth.OAuthClientCredentials;
import com.moonevue.gateway.config.BankConfigKeys;
import com.moonevue.gateway.dto.ChargeRequestDTO;
import com.moonevue.gateway.dto.ChargeResponseDTO;
import com.moonevue.gateway.http.RequestSenderFactory;
import com.moonevue.gateway.service.OAuthTokenService;
import com.moonevue.gateway.service.bank.BankIntegration;
import com.moonevue.gateway.util.ExtraConfigUtils;
import org.apache.hc.core5.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class EfiBankIntegration implements BankIntegration {

    private static final Logger log = LoggerFactory.getLogger(EfiBankIntegration.class);

    private final RequestSenderFactory senderFactory;
    private final OAuthTokenService tokenService;
    private final ObjectMapper mapper;

    public EfiBankIntegration(RequestSenderFactory senderFactory,
                              OAuthTokenService tokenService,
                              ObjectMapper mapper) {
        this.senderFactory = senderFactory;
        this.tokenService = tokenService;
        this.mapper = mapper;
    }

    @Override
    public BankType getBankType() {
        return BankType.EFI;
    }

    @Override
    public String processPayment(String payload, BankConfiguration cfg) {
        try {
            ChargeRequestDTO req = mapper.readValue(payload, ChargeRequestDTO.class);
            ChargeRequestDTO.Instrument instrument = req.payment().instrument();

            switch (instrument) {
                case PIX_IMMEDIATE -> {
                    String resp = callPixImmediate(req, cfg);
                    ChargeResponseDTO out = mapPixToStandard(resp, "pix_cob");
                    return mapper.writeValueAsString(out);
                }
                case PIX_DUE -> {
                    String resp = callPixDue(req, cfg);
                    ChargeResponseDTO out = mapPixToStandard(resp, "pix_cobv");
                    return mapper.writeValueAsString(out);
                }
                case BOLETO -> {
                    String resp = callChargesBoleto(req, cfg);
                    ChargeResponseDTO out = mapBoletoToStandard(resp);
                    return mapper.writeValueAsString(out);
                }
                default -> throw new IllegalArgumentException("Instrumento não suportado: " + instrument);
            }
        } catch (Exception e) {
            log.error("[EFI] Falha ao processar pagamento. instrument={} configId={} erro={}",
                    tryGetInstrument(payload), cfg.getId(), e.getMessage(), e);
            throw new RuntimeException("Erro na integração EFI: " + e.getMessage(), e);
        }
    }

    private String tryGetInstrument(String payload) {
        try { return mapper.readTree(payload).path("payment").path("instrument").asText("?"); }
        catch (Exception ignored) { return "?"; }
    }

    // ===================== PIX =====================

    private String callPixImmediate(ChargeRequestDTO req, BankConfiguration cfg) throws Exception {
        EnvUrls urls = getPixUrls(cfg);

        log.info("[EFI] PIX Imediato: tokenUrl={} env={}", urls.tokenUrl, cfg.getEnvironment());
        AccessToken token = getNamespacedToken(cfg, BankConfigKeys.PIX_NS, urls.tokenUrl, true);

        // Monta body
        ObjectNode body = mapper.createObjectNode();
        ObjectNode calendario = body.putObject("calendario");
        Integer exp = req.payment().pixImmediate().expiracaoSeconds();
        calendario.put("expiracao", exp != null ? exp : 3600);

        maybePutDevedorImmediate(body, req.payment().pixImmediate());

        ObjectNode valor = body.putObject("valor");
        valor.put("original", formatAmount(req.payment().pixImmediate().amount()));

        // chave: do request ou do extraConfig
        String chave = req.payment().pixImmediate().chave();
        if (chave == null || chave.isBlank()) {
            chave = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.PIX_KEY, "pix.pixKey");
        }
        body.put("chave", chave);

        if (req.payment().pixImmediate().solicitacaoPagador() != null) {
            body.put("solicitacaoPagador", req.payment().pixImmediate().solicitacaoPagador());
        }

        String url = urls.apiBase + "/v2/cob";
        Map<String, String> headers = bearerHeaders(token);

        // Força mTLS para PIX
        return senderFactory.getMtls(BankType.EFI, cfg).send(Method.POST, url, mapper.writeValueAsString(body), headers, cfg);
    }

    private String callPixDue(ChargeRequestDTO req, BankConfiguration cfg) throws Exception {
        EnvUrls urls = getPixUrls(cfg);
        AccessToken token = getNamespacedToken(cfg, BankConfigKeys.PIX_NS, urls.tokenUrl, true);

        var p = req.payment().pixDue();
        if (p.txid() == null || p.txid().isBlank()) {
            throw new IllegalArgumentException("txid é obrigatório para PIX com vencimento");
        }

        ObjectNode body = mapper.createObjectNode();
        ObjectNode calendario = body.putObject("calendario");
        calendario.put("dataDeVencimento", p.dataDeVencimento().toString());
        if (p.validadeAposVencimento() != null) calendario.put("validadeAposVencimento", p.validadeAposVencimento());

        putRequiredDevedorDue(body, p);

        ObjectNode valor = body.putObject("valor");
        valor.put("original", formatAmount(p.amountOriginal()));

        if (p.multaPerc() != null) {
            ObjectNode multa = valor.putObject("multa");
            multa.put("modalidade", 2);
            multa.put("valorPerc", p.multaPerc());
        }
        if (p.jurosPerc() != null) {
            ObjectNode juros = valor.putObject("juros");
            juros.put("modalidade", 2);
            juros.put("valorPerc", p.jurosPerc());
        }
        if (p.descontoData() != null && p.descontoValorPerc() != null) {
            ObjectNode desconto = valor.putObject("desconto");
            desconto.put("modalidade", 1);
            ArrayNode arr = desconto.putArray("descontoDataFixa");
            ObjectNode d = arr.addObject();
            d.put("data", p.descontoData().toString());
            d.put("valorPerc", p.descontoValorPerc());
        }

        String chave = p.chave();
        if (chave == null || chave.isBlank()) {
            chave = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.PIX_KEY, "pix.pixKey");
        }
        body.put("chave", chave);

        if (p.solicitacaoPagador() != null) {
            body.put("solicitacaoPagador", p.solicitacaoPagador());
        }

        String url = urls.apiBase + "/v2/cobv/" + p.txid();
        Map<String, String> headers = bearerHeaders(token);

        // Força mTLS para PIX
        return senderFactory.getMtls(BankType.EFI, cfg).send(Method.PUT, url, mapper.writeValueAsString(body), headers, cfg);
    }

    private EnvUrls getPixUrls(BankConfiguration cfg) {
        boolean prod = cfg.getEnvironment() == Environment.PRODUCTION;
        String defaultRoot = prod ? "https://pix.api.efipay.com.br" : "https://pix-h.api.efipay.com.br";
        String defaultApiBase = defaultRoot;
        String tokenUrl = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.TOKEN_URL, defaultRoot + "/oauth/token");
        String apiBase = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.BASE_URL, defaultApiBase);
        return new EnvUrls(apiBase, tokenUrl);
    }

    // ===================== COBRANÇAS (BOLETO) =====================

    private String callChargesBoleto(ChargeRequestDTO req, BankConfiguration cfg) throws Exception {
        EnvUrls urls = getChargesUrls(cfg);
        // Cobranças (Boleto) pode reutilizar as credenciais do PIX quando o namespace
        // "charges" não estiver configurado (comportamento documentado na UI).
        AccessToken token = getNamespacedToken(cfg, BankConfigKeys.CHARGES_NS, BankConfigKeys.PIX_NS, urls.tokenUrl, false);

        ObjectNode body = mapper.createObjectNode();
        ArrayNode items = body.putArray("items");
        for (var it : req.payment().boleto().items()) {
            ObjectNode i = items.addObject();
            i.put("name", it.name());
            i.put("value", it.valueInCents());
            i.put("amount", it.amount());
        }

        ObjectNode payment = body.putObject("payment");
        ObjectNode billet = payment.putObject("banking_billet");

        var cust = req.payment().boleto().customer();
        if (cust == null) {
            throw new IllegalArgumentException("Pagador é obrigatório para boleto (nome + CPF ou CNPJ).");
        }
        String custName = trimToNull(cust.name());
        String custCpf = trimToNull(cust.cpf());
        String custCnpj = cust.juridicalPerson() != null ? trimToNull(cust.juridicalPerson().cnpj()) : null;
        if (custName == null) {
            throw new IllegalArgumentException("Nome do pagador é obrigatório para boleto.");
        }
        if (custCpf == null && custCnpj == null) {
            throw new IllegalArgumentException("CPF ou CNPJ do pagador é obrigatório para boleto.");
        }
        if (custCpf != null && custCnpj != null) {
            throw new IllegalArgumentException("Informe apenas CPF ou apenas CNPJ do pagador, não ambos.");
        }
        ObjectNode customer = billet.putObject("customer");
        customer.put("name", custName);
        if (custCpf != null) customer.put("cpf", custCpf);
        if (cust.email() != null) customer.put("email", cust.email());
        if (cust.phoneNumber() != null) customer.put("phone_number", cust.phoneNumber());

        if (cust.juridicalPerson() != null && cust.juridicalPerson().cnpj() != null) {
            ObjectNode jp = customer.putObject("juridical_person");
            jp.put("corporate_name", cust.juridicalPerson().corporateName());
            jp.put("cnpj", cust.juridicalPerson().cnpj());
        }

        if (cust.address() != null) {
            ObjectNode addr = customer.putObject("address");
            addr.put("street", cust.address().street());
            addr.put("number", cust.address().number());
            addr.put("neighborhood", cust.address().neighborhood());
            addr.put("zipcode", cust.address().zipcode());
            addr.put("city", cust.address().city());
            addr.put("complement", cust.address().complement());
            addr.put("state", cust.address().state());
        }

        billet.put("expire_at", req.payment().boleto().expireAt().toString());

        var conf = req.payment().boleto().configurations();
        if (conf != null) {
            ObjectNode configs = billet.putObject("configurations");
            if (conf.daysToWriteOff() != null) configs.put("days_to_write_off", conf.daysToWriteOff());
            if (conf.fineInCents() != null) configs.put("fine", conf.fineInCents());
            if (conf.interestObject() != null) {
                configs.set("interest", mapper.valueToTree(conf.interestObject()));
            } else if (conf.interestInCents() != null) {
                configs.put("interest", conf.interestInCents());
            }
        }

        if (req.payment().boleto().message() != null) {
            billet.put("message", req.payment().boleto().message());
        }

        String url = urls.apiBase + "/v1/charge/one-step";
        Map<String, String> headers = bearerHeaders(token);

        // Boleto (Cobranças) não requer mTLS → sender padrão/automático
        return senderFactory.get(BankType.EFI, cfg).send(Method.POST, url, mapper.writeValueAsString(body), headers, cfg);
    }

    private EnvUrls getChargesUrls(BankConfiguration cfg) {
        boolean prod = cfg.getEnvironment() == Environment.PRODUCTION;
        String defaultBase = prod ? "https://cobrancas.api.efipay.com.br" : "https://cobrancas-h.api.efipay.com.br";
        String tokenUrl = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.CHARGES_NS + "." + BankConfigKeys.TOKEN_URL, defaultBase + "/v1/authorize");
        String apiBase = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.CHARGES_NS + "." + BankConfigKeys.BASE_URL, defaultBase);
        return new EnvUrls(apiBase, tokenUrl);
    }

    // ===================== MAPEAMENTOS DE RESPOSTA =====================

    private ChargeResponseDTO mapPixToStandard(String raw, String kind) throws Exception {
        JsonNode j = mapper.readTree(raw);
        ChargeResponseDTO out = new ChargeResponseDTO();
        out.setKind(kind);
        out.setId(j.path("txid").asText(null));
        out.setStatus(j.path("status").asText(null));
        out.setAmount(j.path("valor").path("original").asText(null));
        out.setPixCopiaECola(j.path("pixCopiaECola").asText(null));
        out.setChave(j.path("chave").asText(null));

        // Calendário
        if ("pix_cob".equals(kind)) {
            if (j.path("calendario").has("expiracao")) {
                out.setExpiracao(j.path("calendario").path("expiracao").asInt());
            }
        } else {
            if (j.path("calendario").has("dataDeVencimento")) {
                out.setDueDate(j.path("calendario").path("dataDeVencimento").asText(null));
            }
        }

        // loc
        JsonNode loc = j.path("loc");
        if (!loc.isMissingNode()) {
            if (loc.has("id")) out.setLocId(loc.get("id").asInt());
            if (loc.has("location")) out.setLocation(loc.get("location").asText(null));
            if (loc.has("tipoCob")) out.setTipoCob(loc.get("tipoCob").asText(null));
        } else if (j.has("location")) {
            out.setLocation(j.get("location").asText(null));
        }
        return out;
    }

    private ChargeResponseDTO mapBoletoToStandard(String raw) throws Exception {
        JsonNode j = mapper.readTree(raw);
        JsonNode data = j.path("data");
        ChargeResponseDTO out = new ChargeResponseDTO();
        out.setKind("boleto");
        out.setId(asTextSafe(data, "charge_id"));
        out.setStatus(asTextSafe(data, "status"));
        // A EFI retorna "total" em centavos; padronizamos para reais (ex.: 1000 -> "10.00").
        out.setAmount(centsToReais(asTextSafe(data, "total")));
        out.setDueDate(asTextSafe(data, "expire_at"));

        out.setBarcode(asTextSafe(data, "barcode"));
        out.setLink(asTextSafe(data, "link"));
        out.setBilletLink(asTextSafe(data, "billet_link"));
        if (data.has("pdf") && data.get("pdf").has("charge")) {
            out.setPdfLink(data.get("pdf").get("charge").asText(null));
        }
        // Pix dentro do boleto (bolix) pode trazer qrcode/qrcode_image; se quiser mapear:
        if (data.has("pix") && data.get("pix").has("qrcode")) {
            out.setPixCopiaECola(data.get("pix").get("qrcode").asText(null));
        }
        return out;
    }

    private static String asTextSafe(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText(null) : null;
    }

    /** Converte um valor em centavos (string) para reais com 2 casas (ex.: "1000" -> "10.00"). */
    private static String centsToReais(String cents) {
        if (cents == null || cents.isBlank()) return cents;
        try {
            return new BigDecimal(cents.trim())
                    .movePointLeft(2)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException e) {
            return cents;
        }
    }

    private AccessToken getNamespacedToken(BankConfiguration cfg,
                                           String namespace,
                                           String tokenUrl,
                                           boolean useMtlsForToken) {
        return getNamespacedToken(cfg, namespace, null, tokenUrl, useMtlsForToken);
    }

    private AccessToken getNamespacedToken(BankConfiguration cfg,
                                           String namespace,
                                           String fallbackNamespace,
                                           String tokenUrl,
                                           boolean useMtlsForToken) {
        String clientId = resolveCredential(cfg, namespace, fallbackNamespace, BankConfigKeys.CLIENT_ID);
        String clientSecret = resolveCredential(cfg, namespace, fallbackNamespace, BankConfigKeys.CLIENT_SECRET);
        String scope = ExtraConfigUtils.getString(cfg.getExtraConfig(), namespace + "." + BankConfigKeys.SCOPE, null);
        OAuthClientCredentials creds = new OAuthClientCredentials(clientId, clientSecret, scope);
        return tokenService.getTokenFor(BankType.EFI, tokenUrl, creds, cfg, useMtlsForToken);
    }

    /**
     * Lê uma credencial do namespace informado, recorrendo ao {@code fallbackNamespace}
     * (quando fornecido) caso o valor primário esteja ausente. Lança erro descritivo
     * apenas quando nenhum dos namespaces possui o valor.
     */
    private String resolveCredential(BankConfiguration cfg,
                                     String namespace,
                                     String fallbackNamespace,
                                     String key) {
        String value = ExtraConfigUtils.getString(cfg.getExtraConfig(), namespace + "." + key, null);
        if ((value == null || value.isBlank()) && fallbackNamespace != null) {
            value = ExtraConfigUtils.getString(cfg.getExtraConfig(), fallbackNamespace + "." + key, null);
        }
        if (value == null || value.isBlank()) {
            String label = fallbackNamespace != null
                    ? namespace + "." + key + " (nem " + fallbackNamespace + "." + key + ")"
                    : namespace + "." + key;
            throw new IllegalArgumentException("Configuração obrigatória ausente: " + label);
        }
        return value;
    }

    private Map<String, String> bearerHeaders(AccessToken token) {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        h.put("Authorization", token.getTokenType() + " " + token.getToken());
        return h;
    }

    private String formatAmount(BigDecimal v) {
        if (v == null) return null;
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private void maybePutDevedorImmediate(ObjectNode body, ChargeRequestDTO.PixImmediate p) {
        if (p == null) return;

        String nome = trimToNull(p.nome());
        String cpf = trimToNull(p.cpf());
        String cnpj = trimToNull(p.cnpj());

        // A EFI valida 'devedor' com regras oneOf e exige nome quando o objeto existe.
        // Para deixar o devedor opcional, omitimos o bloco quando estiver incompleto.
        if (nome == null) {
            if (cpf != null || cnpj != null) {
                log.warn("[EFI] Ignorando devedor em PIX imediato por ausência de nome (cpf/cnpj recebido). config={}", body.path("chave").asText("?"));
            }
            return;
        }

        ObjectNode devedor = body.putObject("devedor");
        devedor.put("nome", nome);
        if (cpf != null) devedor.put("cpf", cpf);
        if (cnpj != null) devedor.put("cnpj", cnpj);
    }

    private void putRequiredDevedorDue(ObjectNode body, ChargeRequestDTO.PixDue p) {
        // PIX com vencimento (cobv) exige devedor obrigatório (nome + CPF/CNPJ).
        if (p == null) {
            throw new IllegalArgumentException("Devedor é obrigatório para PIX com vencimento.");
        }

        String nome = trimToNull(p.nome());
        String cpf = trimToNull(p.cpf());
        String cnpj = trimToNull(p.cnpj());

        if (nome == null) {
            throw new IllegalArgumentException("Nome do devedor é obrigatório para PIX com vencimento.");
        }
        if (cpf == null && cnpj == null) {
            throw new IllegalArgumentException("CPF ou CNPJ do devedor é obrigatório para PIX com vencimento.");
        }
        if (cpf != null && cnpj != null) {
            throw new IllegalArgumentException("Informe apenas CPF ou apenas CNPJ do devedor, não ambos.");
        }

        ObjectNode devedor = body.putObject("devedor");
        devedor.put("nome", nome);
        if (cpf != null) devedor.put("cpf", cpf);
        if (cnpj != null) devedor.put("cnpj", cnpj);

        String logradouro = trimToNull(p.logradouro());
        String cidade = trimToNull(p.cidade());
        String uf = trimToNull(p.uf());
        String cep = trimToNull(p.cep());
        if (logradouro != null) devedor.put("logradouro", logradouro);
        if (cidade != null) devedor.put("cidade", cidade);
        if (uf != null) devedor.put("uf", uf);
        if (cep != null) devedor.put("cep", cep);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record EnvUrls(String apiBase, String tokenUrl) {}
}
