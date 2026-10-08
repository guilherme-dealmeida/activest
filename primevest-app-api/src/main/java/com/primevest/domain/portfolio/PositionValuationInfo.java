package com.primevest.domain.portfolio;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.fixedincome.FixedIncomeCurve;
import com.primevest.domain.fixedincome.FixedIncomeValuation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
     */
    public record Info(String basis, LocalDate date, String note, boolean matured) {
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
                out.put(pa.getId(), d == null
                        ? new Info("SEM_CURVA", null, "sem preço do Tesouro carregado", matured)
                        : new Info("MERCADO", d, null, matured));
                continue;
            }
            if (!FixedIncomeValuation.usesCurve(pa)) {
                out.put(pa.getId(), new Info("SEM_CURVA", null, "título sem dados de remuneração", false));
                continue;
            }
            if (series == null) {
                series = valuation.series();
            }
            FixedIncomeCurve.Result r = valuation.value(pa, positionCalculator.chronologicalTransactions(pa), series);
            if (r == null || r.status() == FixedIncomeCurve.Status.UNSUPPORTED) {
                out.put(pa.getId(), new Info("SEM_CURVA", null, r != null ? r.reason() : null, false));
            } else {
                out.put(pa.getId(), new Info("CURVA", r.referenceDate(),
                        r.estimated() ? "inclui a inflação do mês corrente estimada pelo último índice publicado" : null,
                        r.status() == FixedIncomeCurve.Status.MATURED));
            }
        }
        return out;
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
