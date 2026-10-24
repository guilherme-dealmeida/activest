package com.activest.domain.indicator;

import com.activest.domain.indicator.IndicatorService.MarketData;
import com.activest.domain.indicator.dto.CompanyDataDTO;
import com.activest.domain.issuer.IssuerKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Card "Dados da empresa" / "Dados do fundo" (docs/planos/PLANO_TELA_ATIVOS.md, D2 a D7). Nada novo e buscado fora:
 * o retrato de mercado vem do {@link IndicatorService#marketData} (as mesmas regras dos indicadores), as marcas de
 * dado antigo usam os limites da listagem ({@link IssuerListingService}) e a liquidez e lida aqui.
 *
 * <p><b>Liquidez media diaria (D4):</b> do ticker consultado, media de quantidade x fechamento dos pregoes dos ultimos
 * {@code activest.issuer-page.liquidity-months} meses. Usa o fechamento de cada pregao passado: e a excecao explicita
 * da regra do preco atual (REGRAS_CLAUDE 2.6, T10) — o COTAHIST guarda a quantidade, nao o volume financeiro.</p>
 */
@Service
public class CompanyDataService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final IndicatorService indicators;
    private final IssuerListingService listing;
    private final JdbcTemplate jdbc;
    private final int liquidityMonths;
    private final Clock clock;

    @Autowired
    public CompanyDataService(IndicatorService indicators, IssuerListingService listing, JdbcTemplate jdbc,
                              @Value("${activest.issuer-page.liquidity-months:2}") int liquidityMonths) {
        this(indicators, listing, jdbc, liquidityMonths, Clock.system(ZONE));
    }

    CompanyDataService(IndicatorService indicators, IssuerListingService listing, JdbcTemplate jdbc,
                       int liquidityMonths, Clock clock) {
        this.indicators = indicators;
        this.listing = listing;
        this.jdbc = jdbc;
        this.liquidityMonths = liquidityMonths;
        this.clock = clock;
    }

    public CompanyDataDTO of(String ticker) {
        MarketData m = indicators.marketData(ticker);
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> liq = jdbc.queryForMap("""
                SELECT AVG(volume * close_price) AS liquidity, COUNT(*) AS sessions FROM asset_price_history
                WHERE asset_id = ? AND reference_date > ? AND volume IS NOT NULL AND close_price IS NOT NULL""",
                m.assetId(), java.sql.Date.valueOf(today.minusMonths(liquidityMonths)));
        BigDecimal liquidity = (BigDecimal) liq.get("liquidity");
        int sessions = ((Number) liq.get("sessions")).intValue();

        boolean company = m.kind() == IssuerKind.COMPANY;
        BigDecimal netDebt = company && m.grossDebt() != null && m.cash() != null ? m.grossDebt().subtract(m.cash()) : null;
        BigDecimal enterprise = m.marketValue() != null && netDebt != null ? m.marketValue().add(netDebt) : null;
        return new CompanyDataDTO(m.ticker(), m.issuerId(), m.kind(), m.statementLayout(), m.basePeriodEnd(),
                listing.baseStale(m.kind(), m.basePeriodEnd(), today), m.price(), m.priceDate(),
                listing.priceStale(m.priceDate(), today), money(m.marketValue()), m.marketValueNote(),
                money(enterprise), money(m.equity()), m.sharesTotal(), money(m.totalAssets()),
                money(m.currentAssets()), money(m.grossDebt()), money(netDebt), money(m.cash()), money(liquidity),
                sessions, liquidityMonths);
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
