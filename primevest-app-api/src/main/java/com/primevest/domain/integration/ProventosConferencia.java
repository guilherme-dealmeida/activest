package com.primevest.domain.integration;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.dividend.Dividend;
import com.primevest.domain.dividend.DividendRepository;
import com.primevest.domain.dividend.DividendType;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.portfolio.PortfolioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Proventos que a fonte informa pela API (B3, Area do Investidor: Movimentacao e Eventos Provisionados)
 * conferidos com os proventos da carteira (docs/planos/PLANO_INTEGRACAO_B3.md, 6.8 e C3). <b>So le</b>:
 * nada vai para {@code dividends} nem para o catalogo (P3) — provento da carteira vem do catalogo, e o
 * catalogo vem das fontes de catalogo (B3, FNET, CVM). O resultado vira o motivo do item nao importado —
 * {@code integration_run_items.reason}, sem coluna nova (P2).
 *
 * <p>Cada provento informado cai em um caso ({@link Situacao}): ja esta na carteira (mesmo ativo, tipo,
 * pagamento, cotas e liquido); esta na carteira, mas o evento do catalogo ainda nao tem a data de
 * pagamento; nao esta na carteira (o catalogo nao tem o evento); ou nao da para conferir.</p>
 *
 * <p>Roda na pre-visualizacao e de novo na confirmacao: na primeira importacao a carteira ainda nao
 * tem as posicoes, e os proventos dela so existem depois que as negociacoes entram.</p>
 */
@Slf4j
@Component
public class ProventosConferencia {

    public enum Situacao { JA_EXISTE, SEM_DATA, SEM_EVENTO, RECUSADO }

    /** Um provento informado pela fonte: um pagamento recebido (ou a receber). */
    record Pagamento(String ref, String ticker, DividendType tipo, LocalDate pagamento, BigDecimal cotas,
                     BigDecimal liquido) {
    }

    record Resultado(String ref, Situacao situacao, String mensagem) {
    }

    private final DividendRepository dividendRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;

    public ProventosConferencia(DividendRepository dividendRepository, AssetRepository assetRepository,
                                PortfolioRepository portfolioRepository) {
        this.dividendRepository = dividendRepository;
        this.assetRepository = assetRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /** externalId -> motivo com o resultado da conferencia, para os proventos (CASH_EVENT) entre os movimentos. */
    @Transactional(readOnly = true)
    public Map<String, String> conferir(UUID userId, List<ImportedMovement> movimentos) {
        Map<String, ImportedMovement> proventos = new HashMap<>();
        List<Pagamento> pagamentos = new ArrayList<>();
        for (ImportedMovement m : movimentos) {
            if (m.kind() != MovementKind.CASH_EVENT) {
                continue;
            }
            proventos.put(m.externalId(), m);
            pagamentos.add(new Pagamento(m.externalId(), m.asset(), tipo(m.market()), m.tradeDate(), m.grossQuantity(),
                m.quoteAmount()));
        }
        if (pagamentos.isEmpty()) {
            return Map.of();
        }
        List<Resultado> resultado;
        try {
            resultado = conferirPagamentos(userId, pagamentos);
        } catch (RuntimeException e) {
            log.warn("Conferencia de proventos da integracao nao rodou: {}", e.getMessage());
            return Map.of();
        }
        Map<String, String> motivos = new HashMap<>();
        for (Resultado r : resultado) {
            ImportedMovement m = proventos.get(r.ref());
            if (m != null) {
                motivos.put(r.ref(), motivo(m, r));
            }
        }
        return motivos;
    }

    /** A conferencia em si, pagamento a pagamento (em ordem de pagamento); um provento da carteira casa uma vez so. */
    List<Resultado> conferirPagamentos(UUID userId, List<Pagamento> pagamentos) {
        var portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return pagamentos.stream().map(p -> new Resultado(p.ref(), Situacao.RECUSADO, "usuario sem carteira.")).toList();
        }
        List<Dividend> mine = new ArrayList<>(dividendRepository.findByPortfolioIdWithAsset(portfolio.getId()));
        Set<Long> usados = new HashSet<>();
        return pagamentos.stream()
                .sorted(Comparator.comparing(Pagamento::pagamento, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> conferir(mine, usados, p))
                .toList();
    }

    private Resultado conferir(List<Dividend> mine, Set<Long> usados, Pagamento p) {
        if (p.ticker() == null || p.tipo() == null || p.pagamento() == null || p.cotas() == null || p.liquido() == null) {
            return new Resultado(p.ref(), Situacao.RECUSADO, "dados incompletos (ativo, tipo, pagamento, cotas e liquido).");
        }
        Asset asset = assetRepository.findByTicker(p.ticker()).orElse(null);
        if (asset == null) {
            return new Resultado(p.ref(), Situacao.RECUSADO, "ativo fora do catalogo.");
        }
        Dividend igual = mine.stream()
                .filter(d -> !usados.contains(d.getId()))
                .filter(d -> mesmoAtivo(d, asset) && d.getType() == p.tipo())
                .filter(d -> p.pagamento().equals(d.getPaymentDate()))
                .filter(d -> mesmasCotas(d, p.cotas()) && mesmoValor(d.netOrGross(), p.liquido()))
                .findFirst().orElse(null);
        if (igual != null) {
            usados.add(igual.getId());
            return new Resultado(p.ref(), Situacao.JA_EXISTE, null);
        }
        Dividend semData = mine.stream()
                .filter(d -> !usados.contains(d.getId()))
                .filter(d -> d.getPaymentDate() == null)
                .filter(d -> mesmoAtivo(d, asset) && d.getType() == p.tipo())
                .filter(d -> mesmasCotas(d, p.cotas()) && mesmoValor(d.netOrGross(), p.liquido()))
                .min(Comparator.comparing(Dividend::getExDate).thenComparing(Dividend::getId))
                .orElse(null);
        if (semData != null) {
            usados.add(semData.getId());
            return new Resultado(p.ref(), Situacao.SEM_DATA, null);
        }
        return new Resultado(p.ref(), Situacao.SEM_EVENTO, null);
    }

    private static boolean mesmoAtivo(Dividend d, Asset asset) {
        return d.getPortfolioAsset() != null && d.getPortfolioAsset().getAsset() != null
                && Objects.equals(d.getPortfolioAsset().getAsset().getId(), asset.getId());
    }

    private static boolean mesmasCotas(Dividend d, BigDecimal cotas) {
        return d.getSharesOnDate() != null && d.getSharesOnDate().compareTo(cotas) == 0;
    }

    private static boolean mesmoValor(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    static String motivo(ImportedMovement m, Resultado r) {
        String p = "Provento (" + (m.market() != null ? m.market() : "provento")
            + (m.asset() != null ? ", " + m.asset() : "") + ")";
        return switch (r.situacao()) {
            case JA_EXISTE -> p + " conferido: já está na carteira.";
            case SEM_DATA -> p + " está na carteira, mas o catálogo ainda não tem a data de pagamento.";
            case SEM_EVENTO -> p + " não está na carteira: o catálogo não tem este evento (pendência do catálogo).";
            case RECUSADO -> p + " não conferido: " + r.mensagem();
        };
    }

    /** Tipo da B3 ('Dividendo', 'Juros Sobre Capital Próprio', 'Rendimento', ... 'a receber') no tipo da carteira. */
    static DividendType tipo(String descricao) {
        if (descricao == null) {
            return null;
        }
        String t = Normalizer.normalize(descricao.toUpperCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (t.contains("JUROS")) {
            return DividendType.JCP;
        }
        if (t.contains("DIVIDENDO")) {
            return DividendType.DIVIDENDO;
        }
        if (t.contains("RENDIMENTO")) {
            return DividendType.RENDIMENTO;
        }
        if (t.contains("AMORTIZACAO")) {
            return DividendType.AMORTIZACAO;
        }
        if (t.contains("RESTITUICAO")) {
            return DividendType.RESTITUICAO_CAPITAL;
        }
        return null;
    }
}
