package com.moonevue.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponseDTO {
    // Campos padronizados para PIX Cobrança com Vencimento (cobv)
    private String txid;
    private String status;
    private Integer locId;
    private String location;
    private String tipoCob;          // esperado "cobv"
    private String pixCopiaECola;    // BR Code copy-paste
    private String dueDate;          // dataDeVencimento (yyyy-MM-dd)
    private String amountOriginal;   // valor.original

    public PaymentResponseDTO() {}

    public PaymentResponseDTO(String txid, String status, Integer locId, String location, String tipoCob,
                              String pixCopiaECola, String dueDate, String amountOriginal) {
        this.txid = txid;
        this.status = status;
        this.locId = locId;
        this.location = location;
        this.tipoCob = tipoCob;
        this.pixCopiaECola = pixCopiaECola;
        this.dueDate = dueDate;
        this.amountOriginal = amountOriginal;
    }

    public String getTxid() { return txid; }
    public void setTxid(String txid) { this.txid = txid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getLocId() { return locId; }
    public void setLocId(Integer locId) { this.locId = locId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getTipoCob() { return tipoCob; }
    public void setTipoCob(String tipoCob) { this.tipoCob = tipoCob; }

    public String getPixCopiaECola() { return pixCopiaECola; }
    public void setPixCopiaECola(String pixCopiaECola) { this.pixCopiaECola = pixCopiaECola; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getAmountOriginal() { return amountOriginal; }
    public void setAmountOriginal(String amountOriginal) { this.amountOriginal = amountOriginal; }
}