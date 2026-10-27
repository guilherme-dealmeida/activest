package com.activest.domain.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.activest.domain.dividend.DividendType;

public record DividendResponseDTO(
    Long id,
    String ticker,
    String assetName,
    BigDecimal amountPerShare,
    /** Total BRUTO, truncado nos centavos. */
    BigDecimal totalAmount,
    LocalDate exDate,
    LocalDate paymentDate,
    DividendType type,
    /** Cotas da posicao na data-com (nulo no provento MANUAL antigo). */
    BigDecimal sharesOnDate,
    /** Aliquota de IR aplicada (0,175 = 17,5%; 0 = isento; nulo = sem regra). */
    BigDecimal withholdingRate,
    /** IR retido, truncado nos centavos. Nulo = sem regra. */
    BigDecimal withholdingTax,
    /** Valor liquido por cota, 8 casas — so exibicao. Nulo = sem regra. */
    BigDecimal netAmountPerShare,
    /** TOTAL liquido = totalAmount - withholdingTax (conta da B3). Nulo = sem regra. */
    BigDecimal netAmount,
    /** Base legal da regra aplicada. Nulo = sem regra. */
    String withholdingLegalBasis,
    Long assetTypeId,
    /** AssetType.getDescription(), para exibicao. */
    String assetTypeDescription,
    /** Mesma regra da carteira: null = tipo sem logo; false = mostra as iniciais. */
    Boolean hasLogo,
    /** Ja recebido hoje (fuso da B3)? false = a receber. Dividend.isReceived. */
    boolean received,
    /** Fonte do evento do catalogo (B3, B3_HISTORICO, FNET). */
    String cashEventSource,
    /** Origem da data de pagamento quando nao e a da fonte do evento: CVM (V38). Nulo = da fonte. */
    String paymentDateSource,
    /** Emissor do catalogo (assets.issuer_id), para o link da tela de emissoras. Nulo sem emissor. */
    Long issuerId,
    /**
     * Provento de ativo em outra moeda (docs/planos/PLANO_CADASTRO_MOEDAS.md, C6): valor na moeda, câmbio (PTAX compra
     * do pagamento), retenção do país de origem e IR brasileiro estimado. Nulo em reais. Os totais acima estão em reais.
     */
    Foreign foreign
) {
    public record Foreign(String currencyCode, String symbol, BigDecimal fxRate, BigDecimal originalAmountPerShare,
                          BigDecimal originalTotalAmount, BigDecimal withholdingRate, BigDecimal withholdingTax,
                          String withholdingLegalBasis, BigDecimal incomeTax) {
    }
}
