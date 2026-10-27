package com.activest.domain.dividend;

import com.activest.domain.asset.Asset;
import com.activest.domain.currency.CurrencyRepository;
import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.ForeignTax;
import com.activest.domain.currency.ForeignTaxCalculator;
import com.activest.domain.currency.ForeignTaxEvent;
import com.activest.domain.currency.ForeignTaxRule;
import com.activest.domain.currency.ForeignTaxRuleService;
import com.activest.domain.currency.RateSide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Provento de ativo em moeda estrangeira (docs/planos/PLANO_CADASTRO_MOEDAS.md, C6 e D24).
 *
 * <ol>
 *   <li>Valor por cota do evento na moeda do ativo × <b>PTAX compra</b> da data de pagamento (sem ela, da data-com;
 *   sem boletim no dia, o último carregado — D27) → valor por cota em reais (8 casas).</li>
 *   <li>Bruto em reais = por cota × cotas, truncado nos centavos como os nacionais.</li>
 *   <li>Retenção do país de origem (país do ativo ou da moeda) pela regra vigente na data.</li>
 *   <li>IR brasileiro estimado = alíquota vigente × bruto − retenção, nunca negativo (desde 2024 nos EUA: 0).</li>
 *   <li>Líquido = bruto − retenção − IR estimado.</li>
 * </ol>
 * Sem nenhum câmbio carregado até a data, o provento não é calculado ({@link Optional#empty()}).
 */
@Component
@RequiredArgsConstructor
public class ForeignDividendCalculator {

    private static final int PER_SHARE_SCALE = 8;

    private final ExchangeRateService exchangeRates;
    private final ForeignTaxRuleService taxRules;
    private final CurrencyRepository currencies;

    public boolean isForeign(Asset asset) {
        return asset != null && !exchangeRates.isBase(asset.getCurrencyCode());
    }

    public Optional<Dividend.ForeignDividend> calculate(Asset asset, AssetCashEvent event, BigDecimal shares,
                                                        List<ForeignTaxRule> rules) {
        String code = asset.getCurrencyCode();
        LocalDate date = event.getPaymentDate() != null ? event.getPaymentDate() : event.getDataCom();
        Optional<ExchangeRateService.Quote> quote = exchangeRates.rateOn(code, date, RateSide.BUY);
        if (quote.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal rate = quote.get().rate();
        BigDecimal original = event.getValuePerShare().setScale(PER_SHARE_SCALE, RoundingMode.HALF_UP);
        BigDecimal perShare = original.multiply(rate).setScale(PER_SHARE_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal gross = WithholdingTaxCalculator.gross(perShare, shares);
        BigDecimal originalTotal = WithholdingTaxCalculator.gross(original, shares);

        String country = asset.getCountryCode() != null ? asset.getCountryCode()
            : currencies.findById(code).map(c -> c.getCountryCode()).orElse(null);
        Long typeId = asset.getAssetType() != null ? asset.getAssetType().getId() : null;
        ForeignTaxRule withholding = ForeignTaxRuleService.select(rules, ForeignTax.WITHHOLDING_FOREIGN,
            ForeignTaxEvent.DIVIDEND, country, typeId, date).orElse(null);
        ForeignTaxRule income = ForeignTaxRuleService.select(rules, ForeignTax.IR_FOREIGN, ForeignTaxEvent.DIVIDEND,
            country, typeId, date).orElse(null);
        BigDecimal withheld = ForeignTaxCalculator.flat(gross, withholding);
        BigDecimal incomeTax = ForeignTaxCalculator.dividendIncomeTax(gross, withheld, income);
        BigDecimal net = gross.subtract(withheld).subtract(incomeTax);
        BigDecimal netPerShare = shares.signum() > 0
            ? net.divide(shares, PER_SHARE_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return Optional.of(new Dividend.ForeignDividend(code, rate, perShare, original, originalTotal, gross,
            withholding, withheld, incomeTax, netPerShare, net));
    }

    public List<ForeignTaxRule> rules() {
        return taxRules.all();
    }
}
