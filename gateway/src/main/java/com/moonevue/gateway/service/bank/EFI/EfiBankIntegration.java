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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class EfiBankIntegration implements BankIntegration {

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
            throw new RuntimeException("Erro na integração EFI: " + e.getMessage(), e);
        }
    }

    // ===================== PIX =====================

    private String callPixImmediate(ChargeRequestDTO req, BankConfiguration cfg) throws Exception {
        EnvUrls urls = getPixUrls(cfg);

        // Credenciais namespaced (pix.*)
        String clientId = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.CLIENT_ID, "client_id");
        String clientSecret = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.CLIENT_SECRET, "client_secret");
        String scope = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.SCOPE, null);
        OAuthClientCredentials creds = new OAuthClientCredentials(clientId, clientSecret, scope);

        // Token PIX (mTLS obrigatório): OAuthTokenService já usa a fábrica; garantimos lá também (ver abaixo)
        AccessToken token = tokenService.getTokenFor(BankType.EFI, urls.tokenUrl, creds, cfg);

        // Monta body
        ObjectNode body = mapper.createObjectNode();
        ObjectNode calendario = body.putObject("calendario");
        Integer exp = req.payment().pixImmediate().expiracaoSeconds();
        calendario.put("expiracao", exp != null ? exp : 3600);

        ObjectNode devedor = body.putObject("devedor");
        if (req.payment().pixImmediate().cpf() != null) devedor.put("cpf", req.payment().pixImmediate().cpf());
        if (req.payment().pixImmediate().cnpj() != null) devedor.put("cnpj", req.payment().pixImmediate().cnpj());
        if (req.payment().pixImmediate().nome() != null) devedor.put("nome", req.payment().pixImmediate().nome());

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

        String clientId = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.CLIENT_ID, "pix.clientId");
        String clientSecret = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.CLIENT_SECRET, "pix.clientSecret");
        String scope = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.PIX_NS + "." + BankConfigKeys.SCOPE, null);
        OAuthClientCredentials creds = new OAuthClientCredentials(clientId, clientSecret, scope);
        AccessToken token = tokenService.getTokenFor(BankType.EFI, urls.tokenUrl, creds, cfg);

        var p = req.payment().pixDue();
        if (p.txid() == null || p.txid().isBlank()) {
            throw new IllegalArgumentException("txid é obrigatório para PIX com vencimento");
        }

        ObjectNode body = mapper.createObjectNode();
        ObjectNode calendario = body.putObject("calendario");
        calendario.put("dataDeVencimento", p.dataDeVencimento().toString());
        if (p.validadeAposVencimento() != null) calendario.put("validadeAposVencimento", p.validadeAposVencimento());

        ObjectNode devedor = body.putObject("devedor");
        if (p.cpf() != null) devedor.put("cpf", p.cpf());
        if (p.cnpj() != null) devedor.put("cnpj", p.cnpj());
        if (p.nome() != null) devedor.put("nome", p.nome());
        if (p.logradouro() != null) devedor.put("logradouro", p.logradouro());
        if (p.cidade() != null) devedor.put("cidade", p.cidade());
        if (p.uf() != null) devedor.put("uf", p.uf());
        if (p.cep() != null) devedor.put("cep", p.cep());

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

        String clientId = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.CHARGES_NS + "." + BankConfigKeys.CLIENT_ID, "charges.clientId");
        String clientSecret = ExtraConfigUtils.requireString(cfg.getExtraConfig(), BankConfigKeys.CHARGES_NS + "." + BankConfigKeys.CLIENT_SECRET, "charges.clientSecret");
        String scope = ExtraConfigUtils.getString(cfg.getExtraConfig(), BankConfigKeys.CHARGES_NS + "." + BankConfigKeys.SCOPE, null);
        OAuthClientCredentials creds = new OAuthClientCredentials(clientId, clientSecret, scope);
        AccessToken token = tokenService.getTokenFor(BankType.EFI, urls.tokenUrl, creds, cfg);

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
        ObjectNode customer = billet.putObject("customer");
        if (cust.name() != null) customer.put("name", cust.name());
        if (cust.cpf() != null) customer.put("cpf", cust.cpf());
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
        out.setAmount(asTextSafe(data, "total")); // em centavos, se preferir converta
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

    private record EnvUrls(String apiBase, String tokenUrl) {}
}
