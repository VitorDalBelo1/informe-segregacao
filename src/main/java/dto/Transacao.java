package dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Transacao {
    private Integer index;
    private String e2eId;
    private BigDecimal vlPago;
    private BigDecimal vlCbsSegr;
    private BigDecimal vlIbsSegr;
    private String docFiscal;
    private String cnpjRaizPspRecInd;
    private String cnpjRaizPspPag;
    private String cnpjRec;
    private String cnpjCpfPagEfet;
    private OffsetDateTime dtHrPgto;
    private OffsetDateTime dtHrLiq;
    private OffsetDateTime dtHrRepasse;

    // Getters e Setters
    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getE2eId() {
        return e2eId;
    }

    public void setE2eId(String e2eId) {
        this.e2eId = e2eId;
    }

    public BigDecimal getVlPago() {
        return vlPago;
    }

    public void setVlPago(BigDecimal vlPago) {
        this.vlPago = vlPago;
    }

    public BigDecimal getVlCbsSegr() {
        return vlCbsSegr;
    }

    public void setVlCbsSegr(BigDecimal vlCbsSegr) {
        this.vlCbsSegr = vlCbsSegr;
    }

    public BigDecimal getVlIbsSegr() {
        return vlIbsSegr;
    }

    public void setVlIbsSegr(BigDecimal vlIbsSegr) {
        this.vlIbsSegr = vlIbsSegr;
    }

    public String getDocFiscal() {
        return docFiscal;
    }

    public void setDocFiscal(String docFiscal) {
        this.docFiscal = docFiscal;
    }

    public String getCnpjRaizPspRecInd() {
        return cnpjRaizPspRecInd;
    }

    public void setCnpjRaizPspRecInd(String cnpjRaizPspRecInd) {
        this.cnpjRaizPspRecInd = cnpjRaizPspRecInd;
    }

    public String getCnpjRaizPspPag() {
        return cnpjRaizPspPag;
    }

    public void setCnpjRaizPspPag(String cnpjRaizPspPag) {
        this.cnpjRaizPspPag = cnpjRaizPspPag;
    }

    public String getCnpjRec() {
        return cnpjRec;
    }

    public void setCnpjRec(String cnpjRec) {
        this.cnpjRec = cnpjRec;
    }

    public String getCnpjCpfPagEfet() {
        return cnpjCpfPagEfet;
    }

    public void setCnpjCpfPagEfet(String cnpjCpfPagEfet) {
        this.cnpjCpfPagEfet = cnpjCpfPagEfet;
    }

    public OffsetDateTime getDtHrPgto() {
        return dtHrPgto;
    }

    public void setDtHrPgto(OffsetDateTime dtHrPgto) {
        this.dtHrPgto = dtHrPgto;
    }

    public OffsetDateTime getDtHrLiq() {
        return dtHrLiq;
    }

    public void setDtHrLiq(OffsetDateTime dtHrLiq) {
        this.dtHrLiq = dtHrLiq;
    }

    public OffsetDateTime getDtHrRepasse() {
        return dtHrRepasse;
    }

    public void setDtHrRepasse(OffsetDateTime dtHrRepasse) {
        this.dtHrRepasse = dtHrRepasse;
    }
}