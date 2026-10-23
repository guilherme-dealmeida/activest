package com.activest.domain.indicator;

import com.activest.domain.asset.AssetLogos;
import com.activest.domain.indicator.IndicatorService.CurrentSnapshot;
import com.activest.domain.indicator.dto.AssetIndicatorsDTO;
import com.activest.domain.indicator.dto.IssuerListingRowDTO;
import com.activest.domain.issuer.IssuerKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listagem de emissoras — uma aba (Acoes ou FIIs) de uma vez (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 3 e 4).
 * Os indicadores vem de {@link IndicatorService#currentAll}; aqui entram nome, setor, logo, as marcas de dado
 * antigo e a ordem padrao: <b>PL decrescente</b>, vazios no fim, desempate pelo ticker (D11).
 */
@Service
public class IssuerListingService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final IndicatorService indicators;
    private final JdbcTemplate jdbc;
    private final AssetLogos logos;
    private final int priceDays;
    private final int balanceMonths;
    private final int reportMonths;
    private final Clock clock;

    @Autowired
    public IssuerListingService(IndicatorService indicators, JdbcTemplate jdbc, AssetLogos logos,
                                @Value("${activest.issuer-page.stale-data.price-days:7}") int priceDays,
                                @Value("${activest.issuer-page.stale-data.statement-months:6}") int balanceMonths,
                                @Value("${activest.issuer-page.stale-data.report-months:3}") int reportMonths) {
        this(indicators, jdbc, logos, priceDays, balanceMonths, reportMonths, Clock.system(ZONE));
    }

    IssuerListingService(IndicatorService indicators, JdbcTemplate jdbc, AssetLogos logos, int priceDays,
                         int balanceMonths, int reportMonths, Clock clock) {
        this.indicators = indicators;
        this.jdbc = jdbc;
        this.logos = logos;
        this.priceDays = priceDays;
        this.balanceMonths = balanceMonths;
        this.reportMonths = reportMonths;
        this.clock = clock;
    }

    private record ListingMeta(String assetName, String tradeName, String legalName, String b3Sector, String b3Subsector,
                        String b3Segment, String fundSegment) {
    }

    public List<IssuerListingRowDTO> list(IssuerKind kind) {
        Map<Long, ListingMeta> metaById = new HashMap<>();
        jdbc.query("""
                SELECT a.id, a.name, i.trade_name, i.legal_name, i.b3_sector, i.b3_subsector, i.b3_segment, i.segment
                FROM assets a JOIN issuers i ON i.id = a.issuer_id
                WHERE a.active AND i.kind = ?""",
                rs -> {
                    metaById.put(rs.getLong("id"), new ListingMeta(rs.getString("name"), rs.getString("trade_name"),
                            rs.getString("legal_name"), rs.getString("b3_sector"), rs.getString("b3_subsector"),
                            rs.getString("b3_segment"), rs.getString("segment")));
                }, kind.name());

        LocalDate today = LocalDate.now(clock);
        List<IssuerListingRowDTO> out = new ArrayList<>();
        for (CurrentSnapshot s : indicators.currentAll(kind)) {
            AssetIndicatorsDTO dto = s.indicators();
            ListingMeta m = metaById.get(s.assetId());
            boolean company = kind == IssuerKind.COMPANY;
            String name = m == null ? dto.ticker()
                    : company && !blank(m.tradeName()) ? m.tradeName().trim()
                    : blank(m.assetName()) ? dto.ticker() : m.assetName().trim();
            out.add(new IssuerListingRowDTO(dto.ticker(), name, m == null ? null : m.legalName(), dto.issuerId(),
                    logos.hasLogo(dto.ticker()),
                    company && m != null ? clean(m.b3Sector()) : null,
                    company && m != null ? clean(m.b3Subsector()) : null,
                    m == null ? null : clean(company ? m.b3Segment() : m.fundSegment()),
                    dto.price(), s.priceDate(), priceStale(s.priceDate(), today),
                    dto.basePeriodEnd(), baseStale(kind, dto.basePeriodEnd(), today), s.equity(),
                    dto.indicators()));
        }
        out.sort(DEFAULT_ORDER);
        return out;
    }

    /** PL decrescente, vazios no fim, desempate pelo ticker. */
    static final Comparator<IssuerListingRowDTO> DEFAULT_ORDER = Comparator
            .comparing(IssuerListingRowDTO::equity, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(IssuerListingRowDTO::ticker);

    /** Preco sem pregao ha mais de N dias corridos. Sem preco nao ha o que marcar. */
    boolean priceStale(LocalDate priceDate, LocalDate today) {
        return priceDate != null && ChronoUnit.DAYS.between(priceDate, today) > priceDays;
    }

    /**
     * Acao: mais de N meses desde o fim do trimestre do balanco. FII: o mes do informe mais de N meses antes do mes
     * corrente (ago/2026 em outubro = 2 meses). Sem base nao ha o que marcar.
     */
    boolean baseStale(IssuerKind kind, LocalDate base, LocalDate today) {
        if (base == null) {
            return false;
        }
        if (kind == IssuerKind.COMPANY) {
            return base.plusMonths(balanceMonths).isBefore(today);
        }
        return ChronoUnit.MONTHS.between(YearMonth.from(base), YearMonth.from(today)) > reportMonths;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String s) {
        return blank(s) ? null : s.trim();
    }
}
