package com.activest.adm.domain.fund;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um fundo (classe, subclasse ou fundo do formato antigo) como sai dos cadastros da CVM, antes de virar ativo
 * (PLANO_FUNDOS_INVESTIMENTO.md, F2). {@code key} = ticker do ativo.
 */
record FundRecord(String key, String cnpj, String subclassId, String parentKey, String legalName, String tradeName,
                  String fundKind, String cvmClassification, String anbimaClassification, String targetAudience,
                  Boolean exclusive, Boolean openEnded, Boolean longTermTax, String managerName, String managerCnpj,
                  String administratorName, String administratorCnpj, String status, LocalDate statusSince,
                  LocalDate startedOn, BigDecimal netWorth, LocalDate netWorthDate) {

    /** Situação de fundo vivo no cadastro: em funcionamento, pré-operacional ou em liquidação (C2). */
    boolean aliveByStatus() {
        String s = com.activest.domain.fund.FundKeys.normalize(status);
        return s.contains("FUNCIONAMENTO NORMAL") || s.contains("PRE-OPERACIONAL") || s.contains("PRE OPERACIONAL")
                || s.contains("LIQUIDACAO");
    }
}
