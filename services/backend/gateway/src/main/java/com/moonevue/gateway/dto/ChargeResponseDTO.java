package com.moonevue.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moonevue.core.enums.BankType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargeResponseDTO {
    // Identificação padronizada
    private BankType provider = BankType.EFI;
    private String kind;       // "pix_cob", "pix_cobv", "boleto"
    private String id;         // txid (pix) ou charge_id (boleto)
    private String status;
    private String currency = "BRL";

    // Montante e datas
    private String amount;     // string para cobrir ambos (ex.: "123.45" ou "59.90")
    private String dueDate;    // dataDeVencimento (pix cobv) ou expire_at (boleto)
    private Integer expiracao; // pix imediata (segundos)

    // PIX
    private Integer locId;
    private String location;
    private String tipoCob;          // "cob" | "cobv"
    private String pixCopiaECola;
    private String chave;

    // BOLETO
    private String barcode;
    private String link;
    private String billetLink;
    private String pdfLink;

    // Getters/Setters
    public BankType getProvider() { return provider; }
    public void setProvider(BankType provider) { this.provider = provider; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public Integer getExpiracao() { return expiracao; }
    public void setExpiracao(Integer expiracao) { this.expiracao = expiracao; }
    public Integer getLocId() { return locId; }
    public void setLocId(Integer locId) { this.locId = locId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getTipoCob() { return tipoCob; }
    public void setTipoCob(String tipoCob) { this.tipoCob = tipoCob; }
    public String getPixCopiaECola() { return pixCopiaECola; }
    public void setPixCopiaECola(String pixCopiaECola) { this.pixCopiaECola = pixCopiaECola; }
    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getBilletLink() { return billetLink; }
    public void setBilletLink(String billetLink) { this.billetLink = billetLink; }
    public String getPdfLink() { return pdfLink; }
    public void setPdfLink(String pdfLink) { this.pdfLink = pdfLink; }
}
