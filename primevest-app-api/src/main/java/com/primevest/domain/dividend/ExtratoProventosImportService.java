package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Confere o extrato de proventos da Area do Investidor da B3 contra os proventos da carteira
 * de quem chama. <b>So le</b>: nao grava nada no catalogo nem na carteira.
 *
 * <p>Ate 29/09/2026 esta importacao criava eventos no catalogo com fonte {@code EXTRATO_B3} e
 * completava datas de pagamento (V24). Isso saiu (PLANO_CARGA_INICIAL.md, regra C e D13; V38):
 * evento do catalogo vem de integracao — FNET, B3 e os relatorios de proventos da CVM — e o que so
 * o extrato do usuario tem nao entra no catalogo. A conferencia fica como ferramenta para achar o
 * que a integracao ainda nao cobre; a importacao da carteira por arquivo e escopo proprio
 * (PENDENCIAS 16).</p>
 *
 * <p>Cada linha (um pagamento recebido) cai em um caso:</p>
 * <ol>
 *   <li>{@link Status#JA_EXISTE} — um provento da carteira com o mesmo ativo, tipo, pagamento,
 *   cotas e liquido;</li>
 *   <li>{@link Status#SEM_DATA} — o provento existe (mesmo ativo, tipo, cotas e liquido), mas o
 *   evento do catalogo esta sem data de pagamento: a integracao ainda nao a trouxe;</li>
 *   <li>{@link Status#SEM_EVENTO} — nada na carteira: o catalogo nao tem o evento;</li>
 *   <li>{@link Status#RECUSADO} — linha incompleta ou ativo fora do catalogo.</li>
 * </ol>
 */
@Slf4j
@Service
public class ExtratoProventosImportService {

    /** Uma linha do extrato: um pagamento recebido. */
    public record Line(String ref, String ticker, DividendType type, LocalDate paymentDate, BigDecimal shares,
                       BigDecimal netAmount, BigDecimal grossAmount, LocalDate dataCom) {
    }

    public record Request(List<Line> lines) {
    }

    public enum Status { JA_EXISTE, SEM_DATA, SEM_EVENTO, RECUSADO }

    public record ItemResult(String ref, String ticker, DividendType type, LocalDate dataCom, LocalDate paymentDate,
                             BigDecimal netAmount, Status status, String message) {
    }

    public record Result(Map<Status, Long> totals, List<ItemResult> items) {
    }

    private final DividendRepository dividendRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;

    public ExtratoProventosImportService(DividendRepository dividendRepository, AssetRepository assetRepository,
                                         PortfolioRepository portfolioRepository) {
        this.dividendRepository = dividendRepository;
        this.assetRepository = assetRepository;
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional(readOnly = true)
    public Result importFor(UUID userId, Request request) {
        List<Line> lines = request.lines() != null ? request.lines() : List.of();
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario sem carteira."));
        List<Dividend> mine = new ArrayList<>(dividendRepository.findByPortfolioIdWithAsset(portfolio.getId()));
        Set<Long> used = new HashSet<>();
        List<ItemResult> items = new ArrayList<>();
        lines.stream()
                .sorted(Comparator.comparing(Line::paymentDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(l -> items.add(line(mine, used, l)));
        Map<Status, Long> totals = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            totals.put(s, items.stream().filter(i -> i.status() == s).count());
        }
        log.info("Extrato de proventos (conferencia): {} linhas — {}", lines.size(), totals);
        return new Result(totals, items);
    }

    private ItemResult line(List<Dividend> mine, Set<Long> used, Line l) {
        if (l.ticker() == null || l.type() == null || l.paymentDate() == null || l.shares() == null
                || l.netAmount() == null) {
            return result(l, null, Status.RECUSADO, "Linha incompleta: ticker, tipo, pagamento, cotas e liquido sao obrigatorios.");
        }
        Asset asset = assetRepository.findByTicker(l.ticker()).orElse(null);
        if (asset == null) {
            return result(l, null, Status.RECUSADO, "Ativo fora do catalogo.");
        }
        Dividend same = mine.stream()
                .filter(d -> !used.contains(d.getId()))
                .filter(d -> sameAsset(d, asset) && d.getType() == l.type())
                .filter(d -> l.paymentDate().equals(d.getPaymentDate()))
                .filter(d -> sameShares(d, l.shares()) && sameMoney(d.netOrGross(), l.netAmount()))
                .findFirst().orElse(null);
        if (same != null) {
            used.add(same.getId());
            return result(l, same.getExDate(), Status.JA_EXISTE, null);
        }
        Dividend undated = mine.stream()
                .filter(d -> !used.contains(d.getId()))
                .filter(d -> d.getPaymentDate() == null)
                .filter(d -> sameAsset(d, asset) && d.getType() == l.type())
                .filter(d -> l.dataCom() == null || l.dataCom().equals(d.getExDate()))
                .filter(d -> sameShares(d, l.shares()) && sameMoney(d.netOrGross(), l.netAmount()))
                .min(Comparator.comparing(Dividend::getExDate).thenComparing(Dividend::getId))
                .orElse(null);
        if (undated != null) {
            used.add(undated.getId());
            return result(l, undated.getExDate(), Status.SEM_DATA,
                    "Provento na carteira, sem data de pagamento no catalogo (a integracao ainda nao trouxe).");
        }
        return result(l, null, Status.SEM_EVENTO, "O catalogo nao tem este provento.");
    }

    private static boolean sameAsset(Dividend d, Asset asset) {
        return d.getPortfolioAsset() != null && d.getPortfolioAsset().getAsset() != null
                && Objects.equals(d.getPortfolioAsset().getAsset().getId(), asset.getId());
    }

    private static boolean sameShares(Dividend d, BigDecimal shares) {
        return d.getSharesOnDate() != null && d.getSharesOnDate().compareTo(shares) == 0;
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static ItemResult result(Line l, LocalDate dataCom, Status status, String message) {
        return new ItemResult(l.ref(), l.ticker(), l.type(), dataCom != null ? dataCom : l.dataCom(), l.paymentDate(),
                l.netAmount(), status, message);
    }
}
