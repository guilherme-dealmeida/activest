package com.primevest.domain.indicator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma linha da listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 4.2): o ticker, o que identifica o
 * emissor e o "Atual" dos indicadores — os mesmos valores de {@code GET /assets/{ticker}/indicators}.
 *
 * @param name          acao: nome de pregao do emissor (sem ele, o nome do ativo); FII: nome do ativo (F1)
 * @param legalName     razao social do emissor (hover)
 * @param sector        setor B3 (so acao)
 * @param subsector     subsetor B3 (so acao)
 * @param segment       segmento B3 (acao) ou segmento do fundo (FII)
 * @param priceDate     data do preco atual
 * @param priceStale    preco sem pregao ha mais de {@code primevest.emissoras.dado-antigo.preco-dias} dias
 * @param basePeriodEnd fim do trimestre do balanco (acao) ou do mes do informe (FII) usado no "Atual"
 * @param baseStale     balanco ou informe mais antigo que o limite (F4)
 * @param equity        patrimonio liquido em R$: PL da controladora (acao) ou do informe (FII)
 */
public record IssuerListingRowDTO(String ticker, String name, String legalName, Long issuerId, boolean hasLogo,
                                  String sector, String subsector, String segment,
                                  BigDecimal price, LocalDate priceDate, boolean priceStale,
                                  LocalDate basePeriodEnd, boolean baseStale, BigDecimal equity,
                                  List<AssetIndicatorsDTO.Item> indicators) {
}
