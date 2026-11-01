package com.activest.domain.portfolio;

import com.activest.domain.asset.Asset;
import com.activest.domain.currency.CurrencyRepository;
import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.RateSide;
import com.activest.domain.portfolio.dto.PortfolioAssetDetailDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Segunda linha da Carteira em outra moeda (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, R2 e R4): os valores da
 * posição na moeda do ativo do exterior (dólar) e, para as criptomoedas, em dólar pela PTAX. Os valores da carteira
 * continuam em reais; esta é só a leitura na outra moeda.
 *
 * <ul>
 *   <li><b>Câmbio do custo</b> ({@code costFx}): reais das compras ÷ moeda das compras. No exterior, a moeda é o
 *   original gravado na transação ({@code original_total_amount}); na cripto, cada compra em reais dividida pela PTAX
 *   de venda do dia dela. Preço médio e valor inicial na moeda = os de reais ÷ esse câmbio — no custo médio, a venda
 *   reduz reais e moeda na mesma proporção, e o câmbio do custo não muda.</li>
 *   <li><b>Câmbio de hoje</b> ({@code fxRate}): PTAX de venda do dia (a mesma que converte a cotação do exterior).
 *   Preço atual na moeda: no exterior, a cotação do ativo; na cripto, o preço em reais ÷ câmbio de hoje. Valor atual =
 *   cotas × preço na moeda; proventos em reais ÷ câmbio de hoje.</li>
 * </ul>
 */
@Service
public class ForeignValuesService {

    /** Moeda da segunda linha das criptomoedas (R4). */
    static final String CRYPTO_CURRENCY = "USD";

    private final NamedParameterJdbcTemplate jdbc;
    private final ExchangeRateService exchangeRates;
    @Autowired(required = false)
    CurrencyRepository currencies;

    public ForeignValuesService(JdbcTemplate jdbc, ExchangeRateService exchangeRates) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.exchangeRates = exchangeRates;
    }

    /** Moeda da segunda linha: a do ativo fora da base; dólar para cripto; nula para o resto. */
    public String currencyOf(Asset asset) {
        if (asset == null) {
            return null;
        }
        if (asset.getCurrencyCode() != null && !exchangeRates.isBase(asset.getCurrencyCode())) {
            return asset.getCurrencyCode();
        }
        return asset.getAssetType() != null && asset.getAssetType().isCrypto() ? CRYPTO_CURRENCY : null;
    }

    /** Câmbio do custo de cada posição (reais ÷ moeda das compras); posição sem compra fica de fora. */
    public Map<UUID, BigDecimal> costFx(Collection<PortfolioAsset> positions) {
        Map<UUID, String> currencyByPosition = new HashMap<>();
        for (PortfolioAsset pa : positions) {
            String c = currencyOf(pa.getAsset());
            if (c != null) {
                currencyByPosition.put(pa.getId(), c);
            }
        }
        Map<UUID, BigDecimal[]> sums = new HashMap<>();
        if (currencyByPosition.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT portfolio_asset_id, transaction_date, total_amount, original_total_amount
                FROM transactions
                WHERE type = 'BUY' AND portfolio_asset_id IN (:ids) AND total_amount > 0
                """, new MapSqlParameterSource("ids", currencyByPosition.keySet()));
        for (Map<String, Object> r : rows) {
            UUID id = (UUID) r.get("portfolio_asset_id");
            BigDecimal brl = (BigDecimal) r.get("total_amount");
            BigDecimal foreign = (BigDecimal) r.get("original_total_amount");
            if (foreign == null) {
                LocalDate date = ((java.sql.Date) r.get("transaction_date")).toLocalDate();
                BigDecimal rate = exchangeRates.rateOn(currencyByPosition.get(id), date, RateSide.SELL)
                        .map(ExchangeRateService.Quote::rate).orElse(null);
                if (rate == null || rate.signum() <= 0) {
                    continue;
                }
                foreign = brl.divide(rate, 10, RoundingMode.HALF_EVEN);
            }
            BigDecimal[] s = sums.computeIfAbsent(id, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(brl);
            s[1] = s[1].add(foreign);
        }
        Map<UUID, BigDecimal> out = new HashMap<>();
        sums.forEach((id, s) -> {
            if (s[1].signum() > 0) {
                out.put(id, s[0].divide(s[1], 10, RoundingMode.HALF_EVEN));
            }
        });
        return out;
    }

    /**
     * Os valores da posição na outra moeda; nulo quando a posição é em reais ou não há câmbio de hoje.
     *
     * @param currentPriceBrl preço atual em reais (o da linha de cima)
     */
    public PortfolioAssetDetailDTO.ForeignValues of(PortfolioAsset pa, BigDecimal costFx, LocalDate today,
                                                    BigDecimal shares, BigDecimal averagePriceBrl,
                                                    BigDecimal investedBrl, BigDecimal currentPriceBrl,
                                                    BigDecimal dividendsBrl) {
        String currency = currencyOf(pa.getAsset());
        if (currency == null) {
            return null;
        }
        var quote = exchangeRates.rateOn(currency, today, RateSide.SELL).orElse(null);
        if (quote == null || quote.rate() == null || quote.rate().signum() <= 0) {
            return null;
        }
        BigDecimal fx = quote.rate();
        boolean foreignAsset = currency.equals(pa.getAsset().getCurrencyCode());
        BigDecimal price = foreignAsset ? pa.getAsset().getCurrentPrice()
                : currentPriceBrl == null ? null : currentPriceBrl.divide(fx, 8, RoundingMode.HALF_EVEN);
        BigDecimal average = div(averagePriceBrl, costFx, 8);
        BigDecimal invested = div(investedBrl, costFx, 2);
        BigDecimal current = price == null || shares == null ? null
                : shares.multiply(price).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal valorization = current == null || invested == null ? null : current.subtract(invested);
        BigDecimal pct = valorization == null || invested.signum() <= 0 ? null
                : valorization.divide(invested, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
        BigDecimal dividends = div(dividendsBrl, fx, 2);
        String symbol = currencies == null ? currency
                : currencies.findById(currency).map(com.activest.domain.currency.Currency::getSymbol).orElse(currency);
        return new PortfolioAssetDetailDTO.ForeignValues(currency, symbol, fx, quote.rateDate(), costFx, price,
                average, invested, current, dividends, valorization, pct);
    }

    private static BigDecimal div(BigDecimal value, BigDecimal fx, int scale) {
        return value == null || fx == null || fx.signum() <= 0 ? null : value.divide(fx, scale, RoundingMode.HALF_EVEN);
    }
}
