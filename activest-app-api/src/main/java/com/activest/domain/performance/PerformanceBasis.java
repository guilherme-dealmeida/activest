package com.activest.domain.performance;

/**
 * Base do valor (docs/planos/PLANO_RENTABILIDADE.md, L10): LIQUIDO (padrao) usa o liquido estimado da renda fixa e do
 * Tesouro (IR e IOF se resgatado no dia), o liquido das vendas e os proventos liquidos; BRUTO usa os valores brutos.
 * Na renda variavel o valor e o mesmo nas duas (IR de ganho de capital fica fora do escopo).
 */
public enum PerformanceBasis {
    NET, GROSS
}
