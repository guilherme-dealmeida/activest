package com.primevest.domain.portfolio;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.fixedincome.FixedIncomeCurve;
import com.primevest.domain.fixedincome.FixedIncomeLots;
import com.primevest.domain.fixedincome.FixedIncomeTaxCalculator;
import com.primevest.domain.fixedincome.FixedIncomeTaxTable;
import com.primevest.domain.fixedincome.FixedIncomeValuation;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * De onde vem o valor atual de cada titulo de renda fixa, para a tela (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md,
 * secao 5): a curva contratada (renda fixa privada) ou o preco oficial (Tesouro), com a data de referencia, o motivo
 * quando nao ha curva e se o titulo venceu. So leitura: a valorizacao gravada e a da rodada.
 */
@Component
public class PositionValuationInfo {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * @param basis   CURVA (renda fixa privada), MERCADO (Tesouro) ou SEM_CURVA
     * @param date    ate quando o valor vale (ultimo CDI ou dia do preco do Tesouro)
     * @param note    observacao: motivo de nao ter curva, inflacao do mes estimada
     * @param matured passou do vencimento (falta lancar o resgate na renda fixa privada)
     * @param estimatedNet liquido estimado se resgatasse na data do valor: bruto - IOF - IR (PLANO_VENDA_RENDA_FIXA,
     *                     R11); nulo sem valor
     * @param lots    o titulo aporte a aporte (renda fixa privada) ou o proprio titulo (Tesouro); vazio sem valor
     */
    public record Info(String basis, LocalDate date, String note, boolean matured, BigDecimal estimatedNet,
                       List<FixedIncomeLots.Lot> lots) {

        Info(String basis, LocalDate date, String note, boolean matured) {
            this(basis, date, note, matured, null, List.of());
        }
    }

    private final FixedIncomeValuation valuation;
    private final PositionCalculator positionCalculator;
    private final JdbcTemplate jdbc;

    public PositionValuationInfo(FixedIncomeValuation valuation, PositionCalculator positionCalculator,
                                 JdbcTemplate jdbc) {
        this.valuation = valuation;
        this.positionCalculator = positionCalculator;
        this.jdbc = jdbc;
    }

    /** Informacao das posicoes de renda fixa e Tesouro; as demais ficam de fora do mapa. */
    public Map<UUID, Info> of(List<PortfolioAsset> positions) {
        Map<UUID, Info> out = new HashMap<>();
        FixedIncomeValuation.Series series = null;
        List<PortfolioAsset> treasury = positions.stream()
                .filter(pa -> type(pa) != null && type(pa).isTreasury()).toList();
        Map<Long, LocalDate> priceDates = treasuryPriceDates(treasury);
        LocalDate today = LocalDate.now(ZONE);
        for (PortfolioAsset pa : positions) {
            AssetType type = type(pa);
            if (type == null || !type.isFixedIncome()) {
                continue;
            }
            LocalDate maturity = pa.getNote() != null ? pa.getNote().getMaturityDate() : null;
            if (type.isTreasury()) {
                LocalDate d = priceDates.get(pa.getAsset().getId());
                boolean matured = maturity != null && !maturity.isAfter(today);
                if (d == null) {
                    out.put(pa.getId(), new Info("SEM_CURVA", null, "sem preço do Tesouro carregado", matured));
                    continue;
                }
                if (series == null) {
                    series = valuation.series();
                }
                FixedIncomeLots.Lot lot = treasuryLot(pa, d, series.taxes());
                out.put(pa.getId(), new Info("MERCADO", d, null, matured, lot != null ? lot.net() : null,
                        lot != null ? List.of(lot) : List.of()));
                continue;
            }
            if (!FixedIncomeValuation.usesCurve(pa)) {
                out.put(pa.getId(), new Info("SEM_CURVA", null, "título sem dados de remuneração", false));
                continue;
            }
            if (series == null) {
                series = valuation.series();
            }
            FixedIncomeLots.Result r = valuation.lots(pa, positionCalculator.chronologicalTransactions(pa), series);
            if (r == null || r.status() == FixedIncomeCurve.Status.UNSUPPORTED) {
                out.put(pa.getId(), new Info("SEM_CURVA", null, r != null ? r.reason() : null, false));
            } else {
                out.put(pa.getId(), new Info("CURVA", r.referenceDate(),
                        r.estimated() ? "inclui a inflação do mês corrente estimada pelo último índice publicado" : null,
                        r.status() == FixedIncomeCurve.Status.MATURED, r.estimatedNet(), r.lots()));
            }
        }
        return out;
    }

    /**
     * O titulo do Tesouro como um lote: cotas x PU do dia (bruto), rendimento sobre cotas x PU de compra, IR e IOF
     * da data de compra ate o dia do preco. Nulo sem PU de compra ou sem preco.
     */
    private FixedIncomeLots.Lot treasuryLot(PortfolioAsset pa, LocalDate priceDate, FixedIncomeTaxTable table) {
        BigDecimal price = pa.getAsset().getCurrentPrice();
        if (price == null) {
            return null;
        }
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal buyPrice = null;
        LocalDate purchase = pa.getNote() != null ? pa.getNote().getPurchaseDate() : null;
        for (Transaction tx : positionCalculator.chronologicalTransactions(pa)) {
            BigDecimal s = tx.getShares() != null ? tx.getShares() : BigDecimal.ZERO;
            if (tx.getType() == TransactionType.BUY) {
                shares = shares.add(s);
                if (buyPrice == null) {
                    buyPrice = tx.getPricePerShare();
                }
                if (purchase == null) {
                    purchase = tx.getTransactionDate();
                }
            } else if (tx.getType() == TransactionType.SELL) {
                shares = shares.subtract(s);
            }
        }
        if (buyPrice == null || purchase == null || shares.signum() <= 0) {
            return null;
        }
        BigDecimal gross = shares.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal applied = shares.multiply(buyPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal income = gross.subtract(applied);
        FixedIncomeTaxTable.Rates rates = table.rates(pa.getAsset().getId(), purchase, priceDate);
        FixedIncomeTaxCalculator.Taxes t = FixedIncomeTaxCalculator.taxes(income, rates);
        return new FixedIncomeLots.Lot(purchase, rates.days(), applied, gross, income, rates.iof(), rates.ir(),
                t.iof(), t.ir(), gross.subtract(t.total()));
    }

    private static AssetType type(PortfolioAsset pa) {
        return pa.getAsset() != null ? pa.getAsset().getAssetType() : null;
    }

    private Map<Long, LocalDate> treasuryPriceDates(List<PortfolioAsset> treasury) {
        Map<Long, LocalDate> out = new HashMap<>();
        if (treasury.isEmpty()) {
            return out;
        }
        String ids = treasury.stream().map(pa -> String.valueOf(pa.getAsset().getId())).distinct()
                .collect(Collectors.joining(","));
        jdbc.query("SELECT asset_id, MAX(reference_date) AS d FROM asset_price_history WHERE source = 'TESOURO' "
                        + "AND asset_id IN (" + ids + ") GROUP BY asset_id",
                rs -> { out.put(rs.getLong("asset_id"), rs.getDate("d").toLocalDate()); });
        return out;
    }
}
