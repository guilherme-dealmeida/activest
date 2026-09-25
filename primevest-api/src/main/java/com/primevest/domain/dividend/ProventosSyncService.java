package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.corporateaction.AssetStockEvent;
import com.primevest.domain.corporateaction.AssetStockEventRepository;
import com.primevest.domain.corporateaction.StockEventType;
import com.primevest.domain.portfolio.AppreciationUpdater;
import com.primevest.domain.portfolio.OpenPositions;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.transaction.TransactionRepository;
import com.primevest.integration.b3.B3Client;
import com.primevest.integration.b3.B3Exception;
import com.primevest.integration.b3.B3CompanyDetail;
import com.primevest.integration.b3.B3HistoryPage;
import com.primevest.integration.b3.B3Supplement;
import com.primevest.integration.b3.B3Values;
import com.primevest.integration.b3.CvmFundRegistry;
import com.primevest.integration.b3.FnetClient;
import com.primevest.integration.b3.FnetProvento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proventos e eventos societarios da B3 (PLANO_BRAPI.md, F5 e F6).
 *
 * <p><b>Rotina diaria</b> (20:00): para cada EMISSOR com posicao — PETR3 e
 * PETR4 sao uma chamada so —, busca na B3 os proventos dos ultimos ~12 meses e
 * os desdobramentos, grupamentos e bonificacoes, e grava no catalogo
 * ({@link AssetCashEvent}, {@link AssetStockEvent}). Depois refaz os proventos
 * de cada posicao pelo {@link DividendRecalculator}.</p>
 *
 * <p><b>Historico longo</b> — uma vez por ativo, quando alguma posicao comeca
 * antes do evento mais antigo guardado: empresa pelo historico da B3 (desde os
 * anos 90, sem data de pagamento); FII pelo FNET (2017 em diante, com
 * pagamento). ETF nao tem fonte alem dos 12 meses: o que faltar e lancamento
 * manual.</p>
 *
 * <p><b>Falha</b> (B3 fora do ar, formato mudou): log ERROR e segue para o
 * proximo emissor. Nada ja gravado e apagado. A rotina diaria e a busca
 * depois de cada transacao ligam/desligam juntas em
 * {@code primevest.proventos.enabled}.</p>
 *
 * <p>Uma chamada por segundo, no maximo: o site da B3 e publico e nao
 * documentado, e nao ha motivo para pressiona-lo.</p>
 */
@Slf4j
@Service
public class ProventosSyncService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int VALUE_SCALE = 11;
    private static final int HISTORY_PAGE_SIZE = 100;
    /** Sincronizacao disparada por transacao nao repete o mesmo emissor antes disso. */
    private static final long ON_DEMAND_COOLDOWN_SECONDS = 6 * 3600;
    /** Especie do historico longo → final do ticker. */
    private static final Map<String, String> SPECIES_SUFFIX = Map.of(
            "ON", "3", "PN", "4", "PNA", "5", "PNB", "6", "PNC", "7", "PND", "8", "UNT", "11");

    private final AssetRepository assetRepository;
    private final AssetCashEventRepository cashEventRepository;
    private final AssetStockEventRepository stockEventRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final TransactionRepository transactionRepository;
    private final OpenPositions openPositions;
    private final DividendRecalculator dividendRecalculator;
    private final AppreciationUpdater appreciationUpdater;
    private final B3Client b3Client;
    private final CvmFundRegistry cvmFundRegistry;
    private final FnetClient fnetClient;
    private final TransactionTemplate tx;
    private final long pauseMillis;
    private final Map<String, Instant> lastIssuerSync = new ConcurrentHashMap<>();

    public ProventosSyncService(AssetRepository assetRepository, AssetCashEventRepository cashEventRepository,
                                AssetStockEventRepository stockEventRepository,
                                PortfolioAssetRepository portfolioAssetRepository,
                                TransactionRepository transactionRepository, OpenPositions openPositions,
                                DividendRecalculator dividendRecalculator, AppreciationUpdater appreciationUpdater,
                                B3Client b3Client, CvmFundRegistry cvmFundRegistry, FnetClient fnetClient,
                                PlatformTransactionManager transactionManager,
                                @Value("${primevest.proventos.pause-millis:1000}") long pauseMillis) {
        this.assetRepository = assetRepository;
        this.cashEventRepository = cashEventRepository;
        this.stockEventRepository = stockEventRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.transactionRepository = transactionRepository;
        this.openPositions = openPositions;
        this.dividendRecalculator = dividendRecalculator;
        this.appreciationUpdater = appreciationUpdater;
        this.b3Client = b3Client;
        this.cvmFundRegistry = cvmFundRegistry;
        this.fnetClient = fnetClient;
        this.tx = new TransactionTemplate(transactionManager);
        this.pauseMillis = pauseMillis;
    }

    /** Resultado de uma rodada. */
    public record SyncResult(int issuers, int failures, int newCashEvents, int newStockEvents, int newDividends) {
    }

    /** Onde a B3 publica o emissor: empresa (acao, unit, BDR) ou fundo (FII, ETF). */
    enum IssuerKind { COMPANY, FUND }

    /** Os ativos em carteira de um emissor. */
    record IssuerGroup(String issuer, IssuerKind kind, List<Asset> assets) {
    }

    // ------------------------------------------------------------------
    // Entradas
    // ------------------------------------------------------------------

    /** Rodada completa: todos os emissores com posicao (aberta ou ja encerrada). */
    public synchronized SyncResult syncAll(String reason) {
        List<IssuerGroup> groups = groups(openPositions.assetsEverHeld());
        log.info("Proventos ({}): {} emissores", reason, groups.size());
        Counters total = new Counters();
        int failures = 0;
        for (IssuerGroup group : groups) {
            try {
                total.add(syncGroup(group));
            } catch (RuntimeException e) {
                failures++;
                log.error("Proventos: emissor {} falhou — nada foi apagado: {}", group.issuer(), e.getMessage());
            }
        }
        log.info("Proventos ({}) concluido: {} emissores, {} falhas, {} eventos novos em dinheiro, {} eventos societarios novos, {} proventos novos nas posicoes",
                reason, groups.size(), failures, total.cash, total.stock, total.dividends);
        return new SyncResult(groups.size(), failures, total.cash, total.stock, total.dividends);
    }

    /**
     * Depois de uma transacao: sincroniza o emissor do ativo, se nao foi
     * sincronizado nas ultimas horas, e carrega o historico longo se a compra
     * for anterior ao que esta guardado.
     */
    public void syncAssetOnDemand(Long assetId) {
        Asset asset = tx.execute(s -> assetRepository.findById(assetId).map(a -> {
            a.getAssetType().getName();
            return a;
        }).orElse(null));
        if (asset == null || asset.getAssetType().getPriceSource() != PriceSource.BRAPI) {
            return;
        }
        List<IssuerGroup> groups = groups(List.of(asset));
        if (groups.isEmpty()) {
            return;
        }
        IssuerGroup single = groups.get(0);
        Instant last = lastIssuerSync.get(single.issuer());
        boolean recent = last != null && last.plusSeconds(ON_DEMAND_COOLDOWN_SECONDS).isAfter(Instant.now());
        synchronized (this) {
            try {
                // O grupo inteiro do emissor (PETR3 e PETR4 juntos), nao so o ativo.
                IssuerGroup full = groups(openPositions.assetsEverHeld()).stream()
                        .filter(g -> g.issuer().equals(single.issuer()))
                        .findFirst().orElse(single);
                if (recent) {
                    Counters c = new Counters();
                    loadLongHistoryIfNeeded(full, null, c);
                    recalculatePositions(full.assets(), c);
                } else {
                    syncGroup(full);
                }
            } catch (RuntimeException e) {
                log.error("Proventos sob demanda de {} falhou: {}", asset.getTicker(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Emissor
    // ------------------------------------------------------------------

    static List<IssuerGroup> groups(List<Asset> assets) {
        Map<String, IssuerGroup> groups = new LinkedHashMap<>();
        for (Asset a : assets) {
            if (a.getAssetType() == null || a.getAssetType().getPriceSource() != PriceSource.BRAPI
                    || a.getTicker() == null || a.getTicker().length() < 5) {
                continue;
            }
            String type = a.getAssetType().getName();
            IssuerKind kind = "FII".equals(type) || "ETF".equals(type) ? IssuerKind.FUND : IssuerKind.COMPANY;
            String issuer = a.getTicker().substring(0, 4).toUpperCase();
            groups.computeIfAbsent(kind + ":" + issuer, k -> new IssuerGroup(issuer, kind, new ArrayList<>()))
                    .assets().add(a);
        }
        return new ArrayList<>(groups.values());
    }

    private Counters syncGroup(IssuerGroup group) {
        Counters c = new Counters();
        pause();
        B3Supplement supplement = group.kind() == IssuerKind.COMPANY
                ? b3Client.companySupplement(group.issuer())
                : b3Client.fundSupplement(group.issuer());
        lastIssuerSync.put(group.issuer(), Instant.now());
        if (supplement == null) {
            log.warn("Proventos: a B3 nao encontrou o emissor {}", group.issuer());
            return c;
        }

        Map<String, List<Asset>> byIsin = isinMap(group, supplement);
        Installments installments = new Installments();
        List<B3Supplement.CashDividend> cash = new ArrayList<>(supplement.cash());
        cash.sort(java.util.Comparator.comparing((B3Supplement.CashDividend d) -> B3Values.date(d.paymentDate()),
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        for (B3Supplement.CashDividend d : cash) {
            List<Asset> targets = byIsin.getOrDefault(B3Values.trim(d.isinCode()), List.of());
            if (targets.isEmpty()) {
                continue; // recibo de subscricao, outra especie sem posicao...
            }
            BigDecimal value = B3Values.decimal(d.rate());
            LocalDate dataCom = B3Values.date(d.lastDatePrior());
            if (value == null || value.signum() <= 0 || dataCom == null) {
                continue;
            }
            for (Asset asset : targets) {
                DividendType type = DividendType.fromSourceLabel(d.label());
                CashEventData data = new CashEventData(type, d.label(), dataCom,
                        B3Values.date(d.paymentDate()), value, B3Values.date(d.approvedOn()),
                        B3Values.trim(d.relatedTo()), B3Values.trim(d.isinCode()), null, CashEventSource.B3, null,
                        installments.next(asset.getId(), type, d.label(), dataCom, value));
                if (saveCashEvent(asset, data)) {
                    c.cash++;
                }
            }
        }
        for (B3Supplement.StockDividend s : supplement.stock()) {
            List<Asset> targets = byIsin.getOrDefault(B3Values.trim(s.isinCode()), List.of());
            if (targets.isEmpty()) {
                continue;
            }
            StockEventType type = StockEventType.fromSourceLabel(s.label());
            BigDecimal factor = B3Values.decimal(s.factor());
            LocalDate dataCom = B3Values.date(s.lastDatePrior());
            if (type == null) {
                log.info("Proventos: evento societario '{}' de {} em {} nao mexe na quantidade — ignorado",
                        s.label(), targets.get(0).getTicker(), s.lastDatePrior());
                continue;
            }
            if (factor == null || factor.signum() <= 0 || dataCom == null) {
                continue;
            }
            for (Asset asset : targets) {
                if (saveStockEvent(asset, type, dataCom, factor, B3Values.date(s.approvedOn()), s.isinCode(), s.remarks())) {
                    c.stock++;
                }
            }
        }

        loadLongHistoryIfNeeded(group, supplement, c);
        recalculatePositions(group.assets(), c);
        return c;
    }

    /**
     * ISIN → ativos do grupo. O fracionario (PETR4F) tem o ISIN do papel
     * inteiro e recebe os mesmos proventos, por isso a lista.
     */
    private Map<String, List<Asset>> isinMap(IssuerGroup group, B3Supplement supplement) {
        boolean missing = group.assets().stream().anyMatch(a -> a.getIsin() == null);
        if (missing) {
            Map<String, String> isinByTicker = new HashMap<>();
            if (group.kind() == IssuerKind.COMPANY) {
                if (supplement.codeCVM() != null) {
                    pause();
                    B3CompanyDetail detail = b3Client.companyDetail(supplement.codeCVM().trim());
                    detail.codes().forEach(code -> isinByTicker.put(code.code().trim().toUpperCase(), code.isin().trim()));
                }
            } else {
                // Fundo: a cota e o ISIN BR{codigo}CTF...; recibo de subscricao
                // e outro ISIN e fica de fora.
                String prefix = "BR" + group.issuer() + "CTF";
                supplement.cash().stream().map(d -> B3Values.trim(d.isinCode()))
                        .filter(i -> i != null && i.startsWith(prefix)).findFirst()
                        .or(() -> supplement.stock().stream().map(s -> B3Values.trim(s.isinCode()))
                                .filter(i -> i != null && i.startsWith(prefix)).findFirst())
                        .ifPresent(isin -> group.assets().forEach(a -> isinByTicker.put(baseTicker(a.getTicker()), isin)));
            }
            for (Asset a : group.assets()) {
                if (a.getIsin() == null) {
                    String isin = isinByTicker.get(baseTicker(a.getTicker()));
                    if (isin != null) {
                        a.setIsin(isin);
                        tx.executeWithoutResult(s -> assetRepository.findById(a.getId()).ifPresent(db -> {
                            db.setIsin(isin);
                            assetRepository.save(db);
                        }));
                    } else if (supplement.cash().isEmpty() && supplement.stock().isEmpty()) {
                        log.info("Proventos: {} nao tem proventos publicados na B3", a.getTicker());
                    } else {
                        log.warn("Proventos: ISIN de {} nao encontrado na B3 — sem proventos automaticos", a.getTicker());
                    }
                }
            }
        }
        Map<String, List<Asset>> byIsin = new HashMap<>();
        group.assets().stream().filter(a -> a.getIsin() != null)
                .forEach(a -> byIsin.computeIfAbsent(a.getIsin(), k -> new ArrayList<>()).add(a));
        return byIsin;
    }

    /** PETR4F → PETR4: o fracionario e o mesmo papel. */
    static String baseTicker(String ticker) {
        String t = ticker.trim().toUpperCase();
        return t.endsWith("F") && t.length() > 5 && Character.isDigit(t.charAt(t.length() - 2))
                ? t.substring(0, t.length() - 1) : t;
    }

    // ------------------------------------------------------------------
    // Historico longo
    // ------------------------------------------------------------------

    /**
     * Falha no historico longo nao derruba o resto: os eventos recentes ja
     * gravados seguem para o recalculo das posicoes, e o historico fica para a
     * proxima rotina (so e marcado como carregado quando termina).
     */
    private void loadLongHistoryIfNeeded(IssuerGroup group, B3Supplement supplement, Counters c) {
        try {
            loadLongHistory(group, supplement, c);
        } catch (RuntimeException e) {
            log.error("Proventos: historico longo de {} nao carregado agora: {}", group.issuer(), e.getMessage());
        }
    }

    private void loadLongHistory(IssuerGroup group, B3Supplement supplement, Counters c) {
        List<Asset> needing = new ArrayList<>();
        for (Asset a : group.assets()) {
            Asset fresh = tx.execute(s -> assetRepository.findById(a.getId()).orElse(null));
            if (fresh == null || fresh.getCashHistoryLoadedAt() != null) {
                continue;
            }
            // Posicao que comeca antes da janela de ~12 meses da B3 precisa do
            // historico longo, ate ele ser carregado por inteiro.
            LocalDate firstTx = transactionRepository.findEarliestDateByAssetId(a.getId());
            LocalDate oldest = cashEventRepository.findOldestRecentDataCom(a.getId());
            if (firstTx != null && (oldest == null || firstTx.isBefore(oldest))) {
                needing.add(fresh);
            }
        }
        if (needing.isEmpty()) {
            return;
        }
        String type = group.assets().get(0).getAssetType().getName();
        if (group.kind() == IssuerKind.COMPANY) {
            B3Supplement sup = supplement;
            if (sup == null) {
                pause();
                sup = b3Client.companySupplement(group.issuer());
            }
            if (sup != null && sup.tradingName() != null) {
                c.cash += loadCompanyHistory(sup.tradingName().trim(), group, needing);
                needing.forEach(this::markHistoryLoaded);
            }
        } else if ("FII".equals(type)) {
            for (Asset a : needing) {
                if (loadFundHistory(a, c)) {
                    markHistoryLoaded(a);
                }
            }
        } else {
            needing.forEach(a -> {
                log.info("Proventos: {} ({}) nao tem fonte alem dos 12 meses da B3 — o que faltar e lancamento manual.",
                        a.getTicker(), type);
                markHistoryLoaded(a);
            });
        }
    }

    private int loadCompanyHistory(String tradingName, IssuerGroup group, List<Asset> needing) {
        boolean bdr = "BDR".equals(group.assets().get(0).getAssetType().getName());
        Map<String, List<Asset>> byTicker = new HashMap<>();
        needing.forEach(a -> byTicker.computeIfAbsent(baseTicker(a.getTicker()), k -> new ArrayList<>()).add(a));
        boolean singleBase = byTicker.size() == 1;
        Installments installments = new Installments();
        int saved = 0;
        for (int page = 1; page <= 100; page++) {
            pause();
            B3HistoryPage result = b3Client.cashDividendsHistory(tradingName, page, HISTORY_PAGE_SIZE);
            for (B3HistoryPage.Item item : result.items()) {
                String suffix = SPECIES_SUFFIX.get(item.typeStock() == null ? "" : item.typeStock().trim().toUpperCase());
                List<Asset> targets = suffix != null ? byTicker.get(group.issuer() + suffix) : null;
                if (targets == null && singleBase && bdr) {
                    // BDR: a especie nao segue ON/PN; o emissor tem um papel so.
                    targets = byTicker.values().iterator().next();
                }
                if (targets == null) {
                    continue;
                }
                BigDecimal cash = B3Values.decimal(item.valueCash());
                BigDecimal lot = B3Values.decimal(item.quotedPerShares());
                LocalDate dataCom = B3Values.date(item.lastDatePriorEx());
                if (cash == null || cash.signum() <= 0 || dataCom == null) {
                    continue;
                }
                BigDecimal perShare = lot != null && lot.compareTo(BigDecimal.ONE) > 0
                        ? cash.divide(lot, VALUE_SCALE, RoundingMode.HALF_UP) : cash;
                for (Asset asset : targets) {
                    DividendType type = DividendType.fromSourceLabel(item.corporateAction());
                    CashEventData data = new CashEventData(type,
                            item.corporateAction(), dataCom, null, perShare, B3Values.date(item.dateApproval()),
                            null, asset.getIsin(), null, CashEventSource.B3_HISTORICO, null,
                            installments.next(asset.getId(), type, item.corporateAction(), dataCom, perShare));
                    if (saveCashEvent(asset, data)) {
                        saved++;
                    }
                }
            }
            Integer totalPages = result.page() != null ? result.page().totalPages() : null;
            if (result.items().isEmpty() || totalPages == null || page >= totalPages) {
                break;
            }
        }
        log.info("Proventos: historico longo de {} carregado — {} eventos novos", tradingName, saved);
        return saved;
    }

    /**
     * Historico de um FII pelo FNET. Devolve se leu todos os avisos — so entao
     * o historico e marcado como carregado. Aviso que ja esta gravado (pelo id
     * do documento) nao e baixado de novo, entao a nova tentativa so busca o
     * que faltou.
     */
    private boolean loadFundHistory(Asset asset, Counters c) {
        String cnpj = asset.getCnpj();
        if (cnpj == null) {
            cnpj = cvmFundRegistry.cnpjForIsin(asset.getIsin());
            if (cnpj == null) {
                log.warn("Proventos: CNPJ de {} (ISIN {}) nao encontrado no cadastro da CVM — sem historico alem de 12 meses.",
                        asset.getTicker(), asset.getIsin());
                return true;
            }
            final String found = cnpj;
            tx.executeWithoutResult(s -> assetRepository.findById(asset.getId()).ifPresent(db -> {
                db.setCnpj(found);
                assetRepository.save(db);
            }));
        }
        pause();
        List<Long> ids = fnetClient.incomeNoticeIds(cnpj);
        Set<String> known = cashEventRepository.findFnetRefs(asset.getId());
        int saved = 0;
        int skipped = 0;
        int failuresInARow = 0;
        for (Long id : ids) {
            if (known.contains(String.valueOf(id))) {
                continue;
            }
            List<FnetProvento> proventos = null;
            for (int attempt = 1; attempt <= 2 && proventos == null; attempt++) {
                pause();
                try {
                    proventos = fnetClient.incomeNotice(id);
                } catch (RuntimeException e) {
                    if (attempt == 2) {
                        log.warn("Proventos: aviso {} do FNET ({}) nao lido — fica para a proxima rotina: {}",
                                id, asset.getTicker(), e.getMessage());
                    }
                }
            }
            if (proventos == null) {
                skipped++;
                // O FNET as vezes trava para todo mundo. Tres avisos seguidos
                // sem resposta: desiste agora; o que ja foi gravado fica.
                if (++failuresInARow >= 3) {
                    throw new B3Exception("FNET sem resposta ao carregar o historico de " + asset.getTicker()
                            + " (" + saved + " eventos gravados ate aqui; nova tentativa na proxima rotina)");
                }
                continue;
            }
            failuresInARow = 0;
            for (FnetProvento p : proventos) {
                boolean mine = (p.isin() != null && p.isin().equalsIgnoreCase(asset.getIsin()))
                        || (p.ticker() != null && p.ticker().equalsIgnoreCase(baseTicker(asset.getTicker())));
                if (!mine) {
                    continue;
                }
                DividendType type = p.amortization() ? DividendType.AMORTIZACAO : DividendType.RENDIMENTO;
                CashEventData data = new CashEventData(type, type.name(), p.dataBase(), p.paymentDate(), p.value(),
                        null, p.referencePeriod(), p.isin(), p.incomeTaxExempt(), CashEventSource.FNET, String.valueOf(id),
                        // Cada aviso e um documento; o mesmo aviso republicado
                        // (nova versao) e a mesma noticia: sempre parcela 1.
                        (short) 1);
                if (saveCashEvent(asset, data)) {
                    saved++;
                }
            }
        }
        c.cash += saved;
        log.info("Proventos: historico do FNET de {} — {} avisos, {} eventos novos{}", asset.getTicker(), ids.size(), saved,
                skipped > 0 ? ", " + skipped + " avisos nao lidos (nova tentativa na proxima rotina)" : "");
        return skipped == 0;
    }

    private void markHistoryLoaded(Asset asset) {
        tx.executeWithoutResult(s -> assetRepository.findById(asset.getId()).ifPresent(db -> {
            db.setCashHistoryLoadedAt(LocalDateTime.now());
            assetRepository.save(db);
        }));
    }

    // ------------------------------------------------------------------
    // Gravacao
    // ------------------------------------------------------------------

    record CashEventData(DividendType type, String label, LocalDate dataCom, LocalDate paymentDate, BigDecimal value,
                         LocalDate approvedOn, String relatedTo, String isin, Boolean incomeTaxExempt,
                         CashEventSource source, String sourceRef, short seq) {
    }

    /**
     * Numera as linhas iguais (tipo, data-com, valor) de uma mesma resposta: o
     * provento pago em parcelas vem repetido, so muda o pagamento (V22).
     */
    private static final class Installments {
        private final Map<String, Integer> seen = new HashMap<>();

        short next(Long assetId, DividendType type, String label, LocalDate dataCom, BigDecimal value) {
            String key = assetId + "|" + (type != null ? type : label) + "|" + dataCom + "|"
                    + value.stripTrailingZeros().toPlainString();
            return (short) (int) seen.merge(key, 1, Integer::sum);
        }
    }

    /**
     * Grava o evento, ou completa o que ja existe (a mesma noticia vinda de
     * outra fonte). Devolve se o evento e novo.
     */
    boolean saveCashEvent(Asset asset, CashEventData d) {
        BigDecimal value = d.value().setScale(VALUE_SCALE, RoundingMode.HALF_UP);
        String label = d.label() == null ? "?" : d.label().trim();
        Boolean created = tx.execute(s -> {
            AssetCashEvent existing = cashEventRepository.findSame(asset.getId(), d.type(), d.dataCom(), value, d.seq())
                    .orElse(null);
            if (existing != null) {
                boolean dirty = false;
                if (existing.getPaymentDate() == null && d.paymentDate() != null) {
                    existing.setPaymentDate(d.paymentDate());
                    existing.setPaymentDateSource(d.source() == CashEventSource.EXTRATO_B3
                            ? PaymentDateSource.EXTRATO_B3 : null);
                    dirty = true;
                } else if (existing.getPaymentDateSource() != null && d.paymentDate() != null
                        && d.source() != CashEventSource.EXTRATO_B3) {
                    // A data veio do extrato ou foi deduzida; a fonte oficial
                    // manda (V24).
                    existing.setPaymentDate(d.paymentDate());
                    existing.setPaymentDateSource(null);
                    dirty = true;
                }
                if (existing.getApprovedOn() == null && d.approvedOn() != null) {
                    existing.setApprovedOn(d.approvedOn());
                    dirty = true;
                }
                if (existing.getRelatedTo() == null && d.relatedTo() != null) {
                    existing.setRelatedTo(d.relatedTo());
                    dirty = true;
                }
                if (existing.getIncomeTaxExempt() == null && d.incomeTaxExempt() != null) {
                    existing.setIncomeTaxExempt(d.incomeTaxExempt());
                    dirty = true;
                }
                if (dirty) {
                    cashEventRepository.save(existing);
                }
                return false;
            }
            cashEventRepository.save(AssetCashEvent.builder()
                    .asset(assetRepository.getReferenceById(asset.getId()))
                    .type(d.type())
                    .label(label.length() > 60 ? label.substring(0, 60) : label)
                    .dataCom(d.dataCom())
                    .paymentDate(d.paymentDate())
                    .paymentDateSource(d.source() == CashEventSource.EXTRATO_B3 && d.paymentDate() != null
                            ? PaymentDateSource.EXTRATO_B3 : null)
                    .valuePerShare(value)
                    .seq(d.seq())
                    .approvedOn(d.approvedOn())
                    .relatedTo(d.relatedTo() != null && d.relatedTo().length() > 100 ? d.relatedTo().substring(0, 100) : d.relatedTo())
                    .isin(d.isin())
                    .incomeTaxExempt(d.incomeTaxExempt())
                    .source(d.source())
                    .sourceRef(d.sourceRef())
                    .build());
            return true;
        });
        if (Boolean.TRUE.equals(created) && d.type() == null) {
            log.warn("Proventos: rotulo desconhecido '{}' em {} (data-com {}) — gravado, mas nao gera provento ate ser mapeado em DividendType.",
                    label, asset.getTicker(), d.dataCom().format(BR));
        }
        return Boolean.TRUE.equals(created);
    }

    private boolean saveStockEvent(Asset asset, StockEventType type, LocalDate dataCom, BigDecimal factor,
                                   LocalDate approvedOn, String isin, String remarks) {
        BigDecimal f = factor.setScale(VALUE_SCALE, RoundingMode.HALF_UP);
        Boolean created = tx.execute(s -> {
            if (stockEventRepository.findByAssetIdAndTypeAndDataComAndFactor(asset.getId(), type, dataCom, f).isPresent()) {
                return false;
            }
            stockEventRepository.save(AssetStockEvent.builder()
                    .asset(assetRepository.getReferenceById(asset.getId()))
                    .type(type)
                    .dataCom(dataCom)
                    .factor(f)
                    .approvedOn(approvedOn)
                    .isin(B3Values.trim(isin))
                    .source("B3")
                    .build());
            return true;
        });
        if (Boolean.TRUE.equals(created)) {
            log.info("Proventos: {} de {} com data-com {} (fator {}) gravado", type, asset.getTicker(),
                    dataCom.format(BR), factor.stripTrailingZeros().toPlainString());
            LocalDate firstTx = transactionRepository.findEarliestDateByAssetId(asset.getId());
            // So avisa quando a bonificacao pega alguma posicao: a de 1994 da
            // PETR4 nao interessa a quem comprou em 2025.
            if (type == StockEventType.BONIFICACAO && firstTx != null && !firstTx.isAfter(dataCom)) {
                // B27: sem central de avisos ainda (PENDENCIAS 13), o aviso e o log.
                log.warn("{} — bonificacao de {}% em {} sem custo atribuido pela B3{}: as acoes novas entram com custo ZERO provisorio (a quantidade ja fica certa). Informe o custo por acao, que esta no fato relevante, quando a central de avisos existir (PENDENCIAS 13.2).",
                        asset.getTicker(), factor.stripTrailingZeros().toPlainString(), dataCom.format(BR),
                        remarks != null && !remarks.isBlank() ? " (observacao da B3: " + remarks.trim() + ")" : "");
            }
        }
        return Boolean.TRUE.equals(created);
    }

    /** Refaz os proventos AUTO e a valorizacao de cada posicao dos ativos. */
    private void recalculatePositions(List<Asset> assets, Counters c) {
        Set<Long> ids = new HashSet<>();
        assets.forEach(a -> ids.add(a.getId()));
        tx.executeWithoutResult(s -> {
            for (PortfolioAsset pa : portfolioAssetRepository.findAllByAssetIdIn(ids)) {
                c.dividends += dividendRecalculator.recalculate(pa, dividendRecalculator.chronologicalTransactions(pa));
                appreciationUpdater.refresh(pa);
            }
        });
    }

    private void pause() {
        if (pauseMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Counters {
        int cash;
        int stock;
        int dividends;

        void add(Counters other) {
            cash += other.cash;
            stock += other.stock;
            dividends += other.dividends;
        }
    }

}
