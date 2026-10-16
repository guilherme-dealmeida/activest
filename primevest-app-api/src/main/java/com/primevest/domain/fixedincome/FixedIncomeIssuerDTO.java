package com.primevest.domain.fixedincome;

/**
 * Empresa de renda fixa para os seletores e as telas (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R10 e secao 6).
 *
 * @param label        rotulo do seletor: "Grupo — razao social"
 * @param displayName  nome das colunas: o do grupo; sem grupo, o comercial; sem ele, a razao social
 * @param dead         empresa morta (inativa, cancelada, liquidada): continua escolhivel (lancamento retroativo)
 * @param logoTicker   ticker cujo logo o grupo usa, quando o arquivo existe (L22)
 */
public record FixedIncomeIssuerDTO(
    Long id,
    String kind,
    String label,
    String displayName,
    String legalName,
    String groupName,
    String status,
    boolean dead,
    String guarantor,
    String saleRule,
    String cnpjRoot,
    String logoTicker
) {
}
