package com.activest.domain.transaction;

import com.activest.domain.asset.Asset;
import com.activest.domain.currency.Currency;
import com.activest.domain.currency.CurrencyRepository;
import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.FxRateOrigin;
import com.activest.domain.currency.RateSide;
import com.activest.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Moeda da transação (docs/planos/PLANO_CADASTRO_MOEDAS.md, D3, D5, C2, C3, C8).
 *
 * <p>A moeda é a do ativo — o usuário não escolhe. Ativo na moeda base (reais): câmbio 1 e nenhum campo de câmbio
 * aceito (C3). Ativo de outra moeda: o usuário lança preço, total e taxa <b>na moeda do ativo</b>; o câmbio é o
 * informado ou, sem ele, a PTAX de venda da data (D5/D16). A transação guarda o lançado nos campos {@code original*} e
 * grava as colunas de sempre em reais (preço 8 casas, total e taxa 2 casas, HALF_EVEN — D26).</p>
 */
@Component
@RequiredArgsConstructor
public class TransactionCurrency {

    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 2;

    private final ExchangeRateService exchangeRates;
    private final CurrencyRepository currencies;

    /** Valores em reais depois de aplicar a moeda. */
    public record Converted(BigDecimal pricePerShare, BigDecimal totalAmount, BigDecimal brokerageFee) {
    }

    /** O que a tela mostra de uma transação fora da moeda base. Nulo na moeda base. */
    public record Info(String code, String symbol, int decimals, BigDecimal fxRate, FxRateOrigin fxRateOrigin,
                       BigDecimal originalPricePerShare, BigDecimal originalTotalAmount, BigDecimal originalFee) {
    }

    public boolean isBase(Asset asset) {
        return exchangeRates.isBase(asset.getCurrencyCode());
    }

    /**
     * Aplica a moeda do ativo à transação (C2/C3/C8) e devolve os valores em reais para gravar nas colunas de sempre.
     *
     * @param price  preço unitário lançado (na moeda do ativo); nulo nos lançados por valor total
     * @param total  total lançado (na moeda do ativo)
     * @param fee    taxa lançada (na moeda do ativo)
     * @param fxRate câmbio informado; nulo = PTAX venda da data
     */
    public Converted apply(Transaction tx, Asset asset, BigDecimal price, BigDecimal total, BigDecimal fee,
                           LocalDate date, BigDecimal fxRate) {
        String code = asset.getCurrencyCode();
        if (exchangeRates.isBase(code)) {
            if (fxRate != null && fxRate.compareTo(BigDecimal.ONE) != 0) {
                throw new BusinessException("Ativo cotado em reais não tem câmbio: " + asset.getTicker() + ".");
            }
            tx.setCurrencyCode(exchangeRates.baseCode());
            tx.setFxRate(BigDecimal.ONE);
            tx.setFxRateOrigin(FxRateOrigin.BASE);
            tx.setOriginalPricePerShare(null);
            tx.setOriginalTotalAmount(null);
            tx.setOriginalFee(null);
            return new Converted(price, total, fee);
        }
        BigDecimal rate;
        FxRateOrigin origin;
        BigDecimal ptax = exchangeRates.rateOn(code, date, RateSide.SELL).map(ExchangeRateService.Quote::rate)
            .orElse(null);
        if (fxRate != null) {
            if (fxRate.signum() <= 0) {
                throw new BusinessException("O câmbio precisa ser maior que zero.");
            }
            rate = fxRate;
            origin = ptax != null && ptax.compareTo(fxRate) == 0 ? FxRateOrigin.PTAX : FxRateOrigin.INFORMED;
        } else {
            rate = exchangeRates.requireRateOn(code, date, RateSide.SELL).rate();
            origin = FxRateOrigin.PTAX;
        }
        tx.setCurrencyCode(code);
        tx.setFxRate(rate.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN));
        tx.setFxRateOrigin(origin);
        tx.setOriginalPricePerShare(price == null ? null : price.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN));
        tx.setOriginalTotalAmount(total.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN));
        tx.setOriginalFee(fee == null ? null : fee.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN));
        return new Converted(
            price == null ? null : price.multiply(rate).setScale(PRICE_SCALE, RoundingMode.HALF_EVEN),
            total.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN),
            fee == null ? null : fee.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN));
    }

    public Info info(Transaction tx) {
        if (tx.getCurrencyCode() == null || exchangeRates.isBase(tx.getCurrencyCode())) {
            return null;
        }
        Currency c = currencies.findById(tx.getCurrencyCode()).orElse(null);
        return new Info(tx.getCurrencyCode(), c != null ? c.getSymbol() : tx.getCurrencyCode(),
            c != null ? c.getDecimals() : 2, tx.getFxRate(), tx.getFxRateOrigin(), tx.getOriginalPricePerShare(),
            tx.getOriginalTotalAmount(), tx.getOriginalFee());
    }
}
