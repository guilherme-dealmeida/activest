package com.activest.domain.indicator.dto;

import com.activest.domain.issuer.IssuerKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Card "Dados da empresa" (acao) / "Dados do fundo" (FII) da tela de Ativos (docs/planos/PLANO_TELA_ATIVOS.md, D2 a
 * D7). Valores em reais (quantidades em unidades); nulos com o motivo quando ha um ({@code marketValueNote}), ou
 * porque nao se aplicam (banco e seguradora nao tem ativo circulante, divida nem caixa no formato comercial; FII so
 * tem valor de mercado, PL, cotas, ativos e liquidez).
 *
 * @param statementLayout  {@code COMERCIAL}, {@code BANCO}, {@code SEGURADORA}; nulo no FII
 * @param basePeriodEnd    data do balanco (acao) ou ultimo dia do mes do informe (FII)
 * @param baseStale        balanco/informe com marca de dado antigo (limites da listagem, D5)
 * @param marketValue      acao: ON + PN sem tesouraria x preco de cada classe (D3); FII: cotas x preco
 * @param marketValueNote  classe avaliada pelo preco da outra, ou o motivo do vazio
 * @param enterpriseValue  valor de mercado + divida liquida (so acao com divida e caixa)
 * @param equity           acao: PL da controladora; FII: PL do informe
 * @param sharesTotal      acao: total de acoes (com tesouraria), ajustado pelos eventos posteriores; FII: cotas
 * @param averageDailyLiquidity media de quantidade x fechamento do ticker nos pregoes da janela (D4); nulo sem pregao
 * @param liquiditySessions pregoes com volume na janela
 * @param liquidityMonths  a janela, em meses ({@code activest.issuer-page.liquidity-months})
 */
public record CompanyDataDTO(String ticker, Long issuerId, IssuerKind kind, String statementLayout,
                             LocalDate basePeriodEnd, boolean baseStale, BigDecimal price, LocalDate priceDate,
                             boolean priceStale, BigDecimal marketValue, String marketValueNote,
                             BigDecimal enterpriseValue, BigDecimal equity, BigDecimal sharesTotal,
                             BigDecimal totalAssets, BigDecimal currentAssets, BigDecimal grossDebt, BigDecimal netDebt,
                             BigDecimal cash, BigDecimal averageDailyLiquidity, int liquiditySessions,
                             int liquidityMonths) {
}
