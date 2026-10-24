package com.activest.adm.domain.dividend;

import com.activest.domain.task.TaskRun;

import com.activest.domain.dividend.AssetCashEventRepository;
import com.activest.domain.dividend.CashEventSource;
import com.activest.domain.dividend.CashEventWriter;
import com.activest.domain.dividend.CashEventWriter.CashEventData;
import com.activest.domain.dividend.DividendType;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.corporateaction.AssetStockEvent;
import com.activest.domain.corporateaction.AssetStockEventRepository;
import com.activest.domain.corporateaction.StockEventType;
import com.activest.domain.portfolio.OpenPositions;
import com.activest.domain.transaction.TransactionRepository;
import com.activest.adm.integration.b3.B3Client;
import com.activest.adm.integration.b3.B3Exception;
import com.activest.adm.integration.b3.B3CompanyDetail;
import com.activest.adm.integration.b3.B3HistoryPage;
import com.activest.adm.integration.b3.B3Supplement;
import com.activest.adm.integration.b3.B3SupplementStore;
import com.activest.adm.integration.b3.B3Values;
import com.activest.adm.integration.b3.FnetClient;
import com.activest.adm.integration.b3.FnetNotice;
import com.activest.adm.integration.b3.FnetDividend;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proventos e eventos societarios da B3 (docs/planos/PLANO_BRAPI.md, F5 e F6).
 *
 * <p><b>Rotina diaria</b> (20:00): para cada EMISSOR do catalogo — todo ativo ligado a
 * um emissor ({@code assets.issuer_id}) mais os que alguem ja teve em carteira (ETF,
 * BDR), desde a F8 do docs/planos/PLANO_EMPRESAS_BALANCOS.md; PETR3 e PETR4 sao uma chamada so —,
 * busca na B3 os proventos dos ultimos ~12 meses e os desdobramentos, grupamentos e
 * bonificacoes, e grava no catalogo ({@code asset_cash_events}, {@link AssetStockEvent}).
 * {@code activest.dividends.scope=HELD} volta ao escopo antigo (so o que ja esteve em
 * carteira) — e o dos testes de ponta a ponta.</p>
 *
 * <p><b>So catalogo</b> (docs/planos/PLANO_ADM_API.md, 10): era o {@code DividendCatalogService},
 * que tambem refazia os proventos de cada posicao. Agora a rotina termina
 * publicando "catalogo mudou" ({@link CatalogChange}, origem PROVENTOS) com os
 * ativos sincronizados, e quem refaz a carteira e a app-api.</p>
 *
 * <p><b>Historico longo</b> — sem coluna de estado no catalogo (F10): empresa pelo
 * historico da B3 (desde os anos 90, sem data de pagamento), buscado uma vez por emissor e
 * guardado em {@code carga/data/b3/dividends/{EMISSOR}.json}, aplicado ao ativo que ainda nao tem
 * nenhum evento dele; FII pelo FNET (2016 em
 * diante, com pagamento): os avisos do CNPJ que estao em {@code carga/data/fnet} (indice local do
 * {@link FnetClient}), sem perguntar ao FNET — a busca por fundo leva 3 a 10 s, trava com
 * frequencia, e o cache ja tem o historico.</p>
 *
 * <p><b>docs/planos/PLANO_CARGA_INICIAL.md (5.2):</b> a resposta de ~12 meses da B3 e somada a
 * {@code carga/data/b3/dividends-12m} ({@link B3SupplementStore}) e a rotina aplica o arquivo inteiro —
 * a data de pagamento nao se perde quando o evento sai da janela, e um banco novo a recebe do
 * arquivo. Para FII o <b>FNET e a fonte principal</b> (D15): os avisos em disco sao aplicados
 * antes da B3, e quando a B3 mostra um provento mais novo que o ultimo aviso gravado o FNET e
 * consultado pelo CNPJ (no maximo uma vez por dia por fundo) e o aviso novo e baixado; a B3 fica
 * de reserva para o fundo sem aviso. O aviso e ligado pelo CNPJ
 * do emissor, nao pelo ticker (renomeacoes); recibo de subscricao e outra classe (ISIN e ticker, R3 do
 * docs/planos/PLANO_CORRECAO_PROVENTOS.md), aviso inativo ou cancelado no FNET (R5) e
 * aviso com data-base depois do pagamento ficam de fora (1.5 do plano). ETF nao tem
 * fonte alem dos 12 meses: o que faltar e lancamento manual. No escopo HELD o
 * historico so e buscado quando uma posicao comeca antes do evento mais antigo
 * guardado.</p>
 *
 * <p><b>Concorrencia</b> (PENDENCIAS 14.1): a rodada completa nao roda duas vezes ao
 * mesmo tempo (a segunda desiste); o pedido sob demanda entra numa fila unica, com um
 * executor e sem repetir o mesmo ativo; o mesmo emissor nunca e sincronizado por dois
 * caminhos ao mesmo tempo. Nada de {@code synchronized} (prendia as threads virtuais).</p>
 *
 * <p><b>Falha</b> (B3 fora do ar, formato mudou): log ERROR e segue para o
 * proximo emissor. Nada ja gravado e apagado. A rotina diaria e a busca
 * depois de cada transacao ligam/desligam juntas em
 * {@code activest.dividends.enabled}.</p>
 *
 * <p>Uma chamada por segundo, no maximo: o site da B3 e publico e nao
 * documentado, e nao ha motivo para pressiona-lo.</p>
 */
@Slf4j
@Service
public class DividendCatalogService {

    /** Historico de preco de cripto no pedido "preciso do historico" (docs/planos/PLANO_RENTABILIDADE.md, 3.3). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.activest.adm.domain.pricehistory.CryptoHistoryService cryptoHistory;


    /** Pausa entre as buscas da situacao dos avisos no FNET (o FNET segura buscas seguidas). */
    @org.springframework.beans.factory.annotation.Value("${fnet.status-pause-millis:3000}")
    long fnetStatusPauseMillis = 3000;

    /** Mensagens de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.activest.adm.domain.notice.AdminNotices notices;

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int VALUE_SCALE = CashEventWriter.VALUE_SCALE;
    private static final int HISTORY_PAGE_SIZE = 100;
    /** Sincronizacao disparada por transacao nao repete o mesmo emissor antes disso. */
    private static final long ON_DEMAND_COOLDOWN_SECONDS = 6 * 3600;
    /**
     * ISIN de recibo de subscricao ou direito no FNET (BRTRXFR02E15, BRALZRR27M17, BRXPMLD02M12): o 7o caractere e
     * "R" ou "D" (a cota e "CTF"). O provento e do recibo, nao da cota (docs/planos/PLANO_CORRECAO_PROVENTOS.md, R3).
     */
    private static final Pattern RECEIPT_ISIN = Pattern.compile("BR[A-Z0-9]{4}[RD].*");
    /** Ticker de fundo: 4 letras, 2 digitos (11 = cota; 12 a 19, 20+ = recibos, direitos e outras classes). */
    private static final Pattern FUND_TICKER = Pattern.compile("([A-Z]{4})(\\d{2})[A-Z]?");
    /** FNET e B3 escrevem o mesmo valor com casas diferentes (1.03999999900318 x 1.04). */
    private static final BigDecimal SAME_VALUE_TOLERANCE = new BigDecimal("0.000001");
    /** Especie do historico longo → final do ticker. */
    private static final Map<String, String> SPECIES_SUFFIX = Map.of(
            "ON", "3", "PN", "4", "PNA", "5", "PNB", "6", "PNC", "7", "PND", "8", "UNT", "11");

    private final AssetRepository assetRepository;
    private final AssetCashEventRepository cashEventRepository;
    private final AssetStockEventRepository stockEventRepository;
    private final TransactionRepository transactionRepository;
    private final OpenPositions openPositions;
    private final CashEventWriter cashEventWriter;
    private final CatalogChangePublisher catalogPublisher;
    private final B3Client b3Client;
    private final B3SupplementStore supplementStore;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final Path historyDir;
    private final FnetClient fnetClient;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final long pauseMillis;
    private final boolean catalogScope;
    private final Map<String, Instant> lastIssuerSync = new ConcurrentHashMap<>();
    /** Busca no FNET por CNPJ: no maximo uma por dia por fundo (a busca leva 3 a 10 s). */
    private final Map<String, Instant> lastFnetSearch = new ConcurrentHashMap<>();
    private static final long FNET_SEARCH_COOLDOWN_SECONDS = 20 * 3600;
    private final ReentrantLock fullRun = new ReentrantLock();
    private final Map<String, ReentrantLock> groupLocks = new ConcurrentHashMap<>();
    /** Fila unica do sob demanda: um executor, sem repetir ativo ja na fila. */
    private final ExecutorService onDemand = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "proventos-sob-demanda");
        t.setDaemon(true);
        return t;
    });
    private final Set<Long> onDemandQueued = ConcurrentHashMap.newKeySet();

    public DividendCatalogService(AssetRepository assetRepository, AssetCashEventRepository cashEventRepository,
                                AssetStockEventRepository stockEventRepository,
                                TransactionRepository transactionRepository, OpenPositions openPositions,
                                CashEventWriter cashEventWriter, CatalogChangePublisher catalogPublisher,
                                B3Client b3Client, B3SupplementStore supplementStore, FnetClient fnetClient,
                                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                @Value("${activest.dividends.pause-millis:1000}") long pauseMillis,
                                @Value("${activest.dividends.scope:ALL}") String scope,
                                @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this.assetRepository = assetRepository;
        this.cashEventRepository = cashEventRepository;
        this.stockEventRepository = stockEventRepository;
        this.transactionRepository = transactionRepository;
        this.openPositions = openPositions;
        this.cashEventWriter = cashEventWriter;
        this.catalogPublisher = catalogPublisher;
        this.b3Client = b3Client;
        this.supplementStore = supplementStore;
        this.objectMapper = objectMapper;
        this.historyDir = Path.of(dataDir, "b3", "proventos").toAbsolutePath().normalize();
        this.fnetClient = fnetClient;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.pauseMillis = pauseMillis;
        this.catalogScope = !"HELD".equalsIgnoreCase(scope == null ? "" : scope.trim());
    }

    @PreDestroy
    void shutdown() {
        onDemand.shutdownNow();
    }

    /** Resultado de uma rodada. Os proventos das posicoes a app-api conta, ao recalcular. */
    public record SyncResult(int issuers, int failures, int newCashEvents, int newStockEvents) {
    }

    /** Onde a B3 publica o emissor: empresa (acao, unit, BDR) ou fundo (FII, ETF). */
    enum IssuerKind { COMPANY, FUND }

    /** Os ativos em carteira de um emissor. */
    record IssuerGroup(String issuer, IssuerKind kind, List<Asset> assets) {
    }

    // ------------------------------------------------------------------
    // Entradas
    // ------------------------------------------------------------------

    /**
     * Rodada completa: todos os emissores do escopo (catalogo ligado + ja em carteira, ou so
     * ja em carteira). Se outra rodada estiver rodando, desiste e devolve {@code null}.
     */
    public SyncResult syncAll(String reason) {
        return syncAll(reason, false);
    }

    /**
     * {@code allNotices}: le de novo <b>todos</b> os avisos do FNET em disco, inclusive os que ja geraram evento (gravar e
     * absorver sao idempotentes). Para depois de uma limpeza (V60): o aviso ativo que ja tinha uma linha gravada (a
     * amortizacao, por exemplo) nao era relido e a outra linha (o rendimento) que saiu junto com o evento indevido nao
     * voltava (CJCT11, HAAA11, MAXR11, PLRI11 em 21/10/2026).
     */
    public SyncResult syncAll(String reason, boolean allNotices) {
        if (!fullRun.tryLock()) {
            log.warn("Proventos ({}): ja existe uma rodada completa em andamento; nada feito", reason);
            return null;
        }
        rereadAllNotices = allNotices;
        try {
            return runAll(reason + (allNotices ? ", todos os avisos do FNET" : ""));
        } finally {
            rereadAllNotices = false;
            fullRun.unlock();
        }
    }

    /** Rodada com {@code syncAll(reason, true)}: o FNET de cada fundo e relido inteiro do disco. */
    private volatile boolean rereadAllNotices;

    private SyncResult runAll(String reason) {
        List<IssuerGroup> groups = groups(scopeAssets());
        log.info("Proventos ({}): {} emissores (escopo {})", reason, groups.size(),
                catalogScope ? "catalogo" : "carteira");
        Counters total = new Counters();
        int failures = 0;
        List<Asset> synced = new ArrayList<>();
        for (IssuerGroup group : groups) {
            TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
            try {
                total.add(locked(group, () -> syncGroup(group)));
                synced.addAll(group.assets());
                // por emissor, nao no fim: o historico longo do FNET pode levar muito tempo e a
                // carteira de quem tem os primeiros ja se acerta
                publishChange(group.assets());
            } catch (RuntimeException e) {
                failures++;
                log.error("Proventos: emissor {} falhou — nada foi apagado: {}", group.issuer(), e.getMessage());
            }
        }
        log.info("Proventos ({}) concluido: {} emissores, {} falhas, {} eventos novos em dinheiro, {} eventos societarios novos; {} ativos enviados para a carteira",
                reason, groups.size(), failures, total.cash, total.stock, synced.size());
        return new SyncResult(groups.size(), failures, total.cash, total.stock);
    }

    /**
     * Pedido da carteira ("preciso do historico", depois de uma transacao): entra na fila
     * unica e volta na hora. Ativo que ja esta na fila nao entra de novo.
     *
     * <p>Revisto na F8: com o catalogo inteiro sincronizado todo dia, o pedido quase
     * sempre termina sem chamada nenhuma (emissor sincronizado nas ultimas horas e
     * historico ja carregado). Continua para o que a rotina nao cobre — ETF e BDR sem
     * emissor na primeira compra, ativo novo no catalogo — e para a carteira nao esperar
     * ate as 20:00.</p>
     */
    public void requestOnDemand(Long assetId) {
        if (assetId == null || !onDemandQueued.add(assetId)) {
            return;
        }
        onDemand.execute(() -> {
            onDemandQueued.remove(assetId);
            try {
                syncAssetOnDemand(assetId);
            } catch (RuntimeException e) {
                log.error("Proventos sob demanda ({}): {}", assetId, e.getMessage(), e);
            }
        });
    }

    /**
     * Sincroniza o emissor do ativo, se nao foi sincronizado nas ultimas horas, e
     * carrega o historico longo se faltar. Chamado pela fila de {@link #requestOnDemand}.
     */
    public void syncAssetOnDemand(Long assetId) {
        Asset asset = tx.execute(s -> assetRepository.findById(assetId).map(a -> {
            a.getAssetType().getName();
            return a;
        }).orElse(null));
        if (asset != null && asset.getAssetType().getPriceSource() == PriceSource.COINGECKO && cryptoHistory != null) {
            // Cripto nao tem provento; o pedido serve para trazer o historico de preco (PLANO_RENTABILIDADE, 3.3).
            try {
                cryptoHistory.importAsset(asset.getId(), "sob demanda");
            } catch (RuntimeException e) {
                log.error("Historico de cripto sob demanda de {} falhou: {}", asset.getTicker(), e.getMessage());
            }
            return;
        }
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
        try {
            // O grupo inteiro do emissor (PETR3 e PETR4 juntos), nao so o ativo.
            List<Asset> scope = new ArrayList<>(scopeAssets());
            if (scope.stream().noneMatch(a -> a.getId().equals(asset.getId()))) {
                scope.add(asset);
            }
            IssuerGroup full = groups(scope).stream()
                    .filter(g -> g.kind() == single.kind() && g.issuer().equals(single.issuer()))
                    .findFirst().orElse(single);
            locked(full, () -> {
                if (recent) {
                    Counters c = new Counters();
                    loadLongHistoryIfNeeded(full, null, c);
                    return c;
                }
                return syncGroup(full);
            });
            publishChange(full.assets());
        } catch (RuntimeException e) {
            log.error("Proventos sob demanda de {} falhou: {}", asset.getTicker(), e.getMessage());
        }
    }

    /** O mesmo emissor nunca e sincronizado por dois caminhos ao mesmo tempo. */
    private Counters locked(IssuerGroup group, java.util.function.Supplier<Counters> work) {
        ReentrantLock lock = groupLocks.computeIfAbsent(group.kind() + ":" + group.issuer(), k -> new ReentrantLock());
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ativos do escopo, com o tipo carregado: todo ativo ativo ligado a um emissor mais os
     * que alguem ja teve em carteira; no escopo HELD, so os ultimos.
     */
    private List<Asset> scopeAssets() {
        Map<Long, Asset> assets = new LinkedHashMap<>();
        openPositions.assetsEverHeld().forEach(a -> assets.putIfAbsent(a.getId(), a));
        if (catalogScope) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM assets WHERE issuer_id IS NOT NULL AND active ORDER BY ticker", Long.class);
            List<Asset> linked = tx.execute(s -> {
                List<Asset> found = assetRepository.findAllById(ids);
                found.forEach(a -> {
                    if (a.getAssetType() != null) {
                        a.getAssetType().getName();
                    }
                });
                return found;
            });
            if (linked != null) {
                linked.stream().sorted(java.util.Comparator.comparing(Asset::getTicker))
                        .forEach(a -> assets.putIfAbsent(a.getId(), a));
            }
        }
        return new ArrayList<>(assets.values());
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
        boolean fund = group.kind() == IssuerKind.FUND;
        B3Supplement fresh;
        B3Exception b3Down = null;
        try {
            fresh = fund ? b3Client.fundSupplement(group.issuer()) : b3Client.companySupplement(group.issuer());
            if (fresh == null) {
                log.warn("Proventos: a B3 nao encontrou o emissor {}", group.issuer());
            }
        } catch (B3Exception e) {
            // a B3 caiu: segue com o que ja esta em carga/data/b3/proventos-12m
            log.warn("Proventos: B3 sem resposta para {} ({}) — usando o arquivo em disco", group.issuer(), e.getMessage());
            notices.sourceUnavailable("Proventos", "B3", "sem resposta para " + group.issuer() + " (" + e.getMessage()
                    + ") — a rodada seguiu com o arquivo em disco");
            fresh = null;
            b3Down = e;
        }
        lastIssuerSync.put(group.issuer(), Instant.now());
        // resposta somada ao arquivo: o que saiu da janela de 12 meses continua valendo (P1)
        B3Supplement supplement = supplementStore.merge(fund, group.issuer(), fresh);
        boolean fii = "FII".equals(group.assets().get(0).getAssetType().getName());
        if (supplement == null) {
            if (fund) {
                // o FNET nao depende da B3: o historico do FII segue pelo CNPJ
                loadLongHistoryIfNeeded(group, null, c);
            }
            if (b3Down != null) {
                throw b3Down;
            }
            return c;
        }
        if (fii) {
            // D15: FNET primeiro (fonte principal, sempre com pagamento); a B3 so completa ou
            // cobre o que o FNET nao tem
            loadLongHistoryIfNeeded(group, supplement, c);
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
                LocalDate payment = B3Values.date(d.paymentDate());
                // R1 (docs/planos/PLANO_CORRECAO_PROVENTOS.md): a mesma amortizacao ja gravada pelo FNET com outras
                // casas completa, nao duplica. Dentro da propria B3 valores quase iguais sao parcelas diferentes (a
                // ultima leva o arredondamento: KLBN 0,04559717225 x 3 e 0,04559717224) e ficam como vieram.
                BigDecimal v = type == null ? value : sameValue(asset, type, dataCom, value, CashEventSource.B3);
                if (fii && type != null && fnetHasSamePayment(asset, type, payment, v)) {
                    // R6: o FNET ja tem este provento (mesmo pagamento e valor) com outra data-base: a B3 era reserva
                    continue;
                }
                CashEventData data = new CashEventData(type, d.label(), dataCom,
                        payment, v, B3Values.date(d.approvedOn()),
                        B3Values.trim(d.relatedTo()), B3Values.trim(d.isinCode()), null, CashEventSource.B3, null,
                        // R2: a parcela e numerada pelo valor que vai ser gravado (a chave do evento)
                        installments.next(asset.getId(), type, d.label(), dataCom, v));
                if (cashEventWriter.save(asset, data)) {
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

        if (!fii) {
            loadLongHistoryIfNeeded(group, supplement, c);
        }
        if (b3Down != null) {
            // o que estava em disco foi aplicado; a rodada ainda conta o emissor como falha
            throw b3Down;
        }
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
     * proxima rotina (o que falta e reconhecido pelo que ja esta gravado e em disco).
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
            if (catalogScope) {
                // catalogo inteiro: todo ativo tem o historico longo
                needing.add(a);
                continue;
            }
            // Posicao que comeca antes da janela de ~12 meses da B3 precisa do
            // historico longo, ate ele estar gravado.
            LocalDate firstTx = transactionRepository.findEarliestDateByAssetId(a.getId());
            LocalDate oldest = cashEventRepository.findOldestRecentDataCom(a.getId());
            if (firstTx != null && (oldest == null || firstTx.isBefore(oldest))) {
                needing.add(a);
            }
        }
        if (needing.isEmpty()) {
            return;
        }
        String type = group.assets().get(0).getAssetType().getName();
        if (group.kind() == IssuerKind.COMPANY) {
            loadCompanyHistory(group, supplement, needing, c);
        } else if ("FII".equals(type)) {
            // barato: indice local do FNET x avisos ja gravados; nada a ler quando esta tudo gravado.
            // A B3 so e usada para saber se ha aviso novo a buscar no FNET.
            loadFundHistory(needing, latestB3DataCom(supplement), c);
        } else {
            log.debug("Proventos: {} ({}) nao tem fonte alem dos 12 meses da B3 — o que faltar e lancamento manual.",
                    group.issuer(), type);
        }
    }

    /**
     * Historico da B3 para os ativos do grupo que ainda nao tem nenhum evento dele
     * ({@code B3_HISTORICO}). A resposta inteira fica em
     * {@code carga/data/b3/dividends/{EMISSOR}.json} (F10: o arquivo e o estado, sem coluna no
     * catalogo): com o arquivo em disco nada e pedido a B3 de novo — a acao que nunca pagou
     * provento le o arquivo e segue sem eventos. Sem arquivo, as paginas sao buscadas (1
     * chamada/s) e o arquivo so e gravado quando todas vieram.
     */
    private void loadCompanyHistory(IssuerGroup group, B3Supplement supplement, List<Asset> needing, Counters c) {
        List<Asset> missing = needing.stream()
                .filter(a -> !cashEventRepository.existsByAssetIdAndSource(a.getId(), CashEventSource.B3_HISTORICO))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        Path file = companyHistoryFile(group.issuer());
        List<B3HistoryPage.Item> items = readCompanyHistory(file);
        if (items != null && items.isEmpty() && supplement != null && !supplement.cash().isEmpty()) {
            // R8 (docs/planos/PLANO_CORRECAO_PROVENTOS.md): historico vazio de quem tem provento nos 12 meses nao e
            // "nunca pagou" — era o nome de pregao com barra (KLABIN S/A, PENDENCIAS 19.1): busca de novo
            log.info("Proventos: historico longo de {} vazio em disco, mas a B3 mostra proventos recentes — buscando de novo",
                    group.issuer());
            items = null;
        }
        if (items == null) {
            B3Supplement sup = supplement;
            if (sup == null) {
                pause();
                sup = b3Client.companySupplement(group.issuer());
            }
            if (sup == null || sup.tradingName() == null) {
                return;
            }
            items = fetchCompanyHistory(sup.tradingName().trim());
            writeCompanyHistory(file, items);
        }
        c.cash += applyCompanyHistory(group, missing, items);
    }

    private List<B3HistoryPage.Item> fetchCompanyHistory(String tradingName) {
        List<B3HistoryPage.Item> items = new ArrayList<>();
        String query = historyQueryName(tradingName);
        for (int page = 1; page <= 100; page++) {
            pause();
            B3HistoryPage result = b3Client.cashDividendsHistory(query, page, HISTORY_PAGE_SIZE);
            items.addAll(result.items());
            Integer totalPages = result.page() != null ? result.page().totalPages() : null;
            if (result.items().isEmpty() || totalPages == null || page >= totalPages) {
                break;
            }
        }
        log.info("Proventos: historico longo de {} baixado da B3 — {} linhas", tradingName, items.size());
        return items;
    }

    private int applyCompanyHistory(IssuerGroup group, List<Asset> needing, List<B3HistoryPage.Item> items) {
        boolean bdr = "BDR".equals(group.assets().get(0).getAssetType().getName());
        Map<String, List<Asset>> byTicker = new HashMap<>();
        needing.forEach(a -> byTicker.computeIfAbsent(baseTicker(a.getTicker()), k -> new ArrayList<>()).add(a));
        boolean singleBase = byTicker.size() == 1;
        Installments installments = new Installments();
        int saved = 0;
        for (B3HistoryPage.Item item : items) {
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
                if (cashEventWriter.save(asset, data)) {
                    saved++;
                }
            }
        }
        log.info("Proventos: historico longo de {} gravado — {} eventos novos", group.issuer(), saved);
        return saved;
    }

    /**
     * Nome de pregao como o historico da B3 o encontra: sem a barra. "KLABIN S/A", "AMBEV S/A" e "CURY S/A" devolvem
     * lista vazia; "KLABIN SA", 219 lancamentos (conferido em 21/10/2026 — a causa das 17 acoes sem historico da
     * PENDENCIAS 19.1; docs/planos/PLANO_CORRECAO_PROVENTOS.md, G).
     */
    static String historyQueryName(String tradingName) {
        return tradingName.replace("/", "").replaceAll("\\s+", " ").trim();
    }

    /** {@code carga/data/b3/dividends/{EMISSOR}.json}: todas as paginas do historico da B3. */
    private Path companyHistoryFile(String issuer) {
        return historyDir.resolve(issuer.toUpperCase().replaceAll("[^A-Z0-9]", "_") + ".json");
    }

    /** Nulo = sem arquivo (ou ilegivel: busca de novo). */
    private List<B3HistoryPage.Item> readCompanyHistory(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return List.of(objectMapper.readValue(file.toFile(), B3HistoryPage.Item[].class));
        } catch (IOException e) {
            log.warn("Proventos: {} ilegivel ({}) — o historico sera buscado de novo", file, e.getMessage());
            return null;
        }
    }

    private void writeCompanyHistory(Path file, List<B3HistoryPage.Item> items) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(temp.toFile(), items);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // sem o arquivo, a proxima rodada busca de novo: nada se perde
            log.warn("Proventos: historico de {} nao gravado em disco: {}", file.getFileName(), e.getMessage());
        }
    }

    /**
     * Historico de FII pelo FNET, para os ativos de um grupo (o mesmo fundo pode ter duas
     * classes negociadas, TRPL11 e TRPL15). Busca pelo CNPJ do emissor ({@code issuers.cnpj}), le os avisos
     * desse CNPJ que estao em {@code carga/data/fnet} e ainda nao foram gravados. Devolve se nada ficou
     * para tras (aviso que nao foi lido e tentado de novo na proxima rodada).
     */
    private boolean loadFundHistory(List<Asset> assets, LocalDate latestB3, Counters c) {
        Map<String, List<Asset>> byCnpj = new TreeMap<>();
        for (Asset asset : assets) {
            String cnpj = fundCnpj(asset);
            if (cnpj == null) {
                log.warn("Proventos: CNPJ de {} (ISIN {}) nao encontrado — sem historico alem de 12 meses.",
                        asset.getTicker(), asset.getIsin());
                continue;
            }
            byCnpj.computeIfAbsent(cnpj, k -> new ArrayList<>()).add(asset);
        }
        boolean complete = true;
        for (Map.Entry<String, List<Asset>> e : byCnpj.entrySet()) {
            complete &= loadFund(e.getKey(), e.getValue(), c);
            complete &= searchNewNotices(e.getKey(), e.getValue(), latestB3, c);
        }
        return complete;
    }

    private boolean loadFund(String cnpj, List<Asset> assets, Counters c) {
        // so o que esta em disco (carga/data/fnet): nenhuma chamada ao FNET
        List<Long> ids = fnetClient.cachedNoticeIds(cnpj);
        Set<String> known = new HashSet<>();
        assets.forEach(a -> known.addAll(cashEventRepository.findFnetRefs(a.getId())));
        List<Long> missing = ids.stream().filter(id -> rereadAllNotices || !known.contains(String.valueOf(id)))
                .sorted().toList();
        if (missing.isEmpty()) {
            return true;
        }
        // D5/R5: aviso inativo ou cancelado no FNET (o fundo publicou outro no lugar, ou retirou) nao vira evento
        // R11: a situacao vale pelo id do aviso, em qualquer arquivo (aviso listado no FNET sob o CNPJ de outro fundo)
        Set<Long> inactive = fnetClient.inactiveNoticeIds();
        boolean statusKnown = fnetClient.hasStatus(cnpj);
        FnetClient.Batch batch = fnetClient.incomeNotices(missing.stream().filter(i -> !inactive.contains(i)).toList());
        // R9: dias de pagamento em que o fundo ja tem a linha da propria cota (ISIN do ativo ou ticker do fundo), neste
        // lote ou ja gravada — no mesmo dia, linha com ISIN e ticker de outro papel e de outra classe ou de outro fundo
        Set<LocalDate> ownPayments = ownPaymentDates(assets, batch, false);
        // R12: dias com a linha do ISIN da propria cota — ai, linha com outro ISIN e de outro papel mesmo com o ticker
        // do fundo (EURO11, 19/04/2017: recibo BREURO01M15 de R$ 1,56 com o ticker EURO11, ao lado da cota a R$ 1,23)
        Set<LocalDate> ownIsinPayments = ownPaymentDates(assets, batch, true);
        // R13: dias com a linha do ticker do fundo no lote — ai, linha com ticker de outra classe do fundo (mesma raiz,
        // outro final) e da outra classe mesmo com o ISIN da cota (URPR11, aviso 1335853: URPR11 0,30 e URPR13 0,078,
        // as duas linhas com BRURPRCTF002)
        Set<LocalDate> ownTickerPayments = new HashSet<>();
        batch.notices().values().forEach(n -> n.dividends().stream()
                .filter(p -> p.paymentDate() != null && tickerOfGroup(assets, p))
                .forEach(p -> ownTickerPayments.add(p.paymentDate())));
        int saved = 0;
        int otherFund = 0;
        int invalid = 0;
        for (Map.Entry<Long, FnetNotice> n : batch.notices().entrySet()) {
            long id = n.getKey();
            FnetNotice notice = n.getValue();
            if (notice.fundCnpj() != null && !notice.fundCnpj().equals(cnpj)) {
                otherFund++;
                continue;
            }
            for (FnetDividend p : notice.dividends()) {
                if (otherClass(assets, p)) {
                    // R3: recibo de subscricao, direito ou outra classe do fundo — o provento nao e da cota
                    continue;
                }
                if (p.paymentDate() != null && ownPayments.contains(p.paymentDate()) && !ownLine(assets, p)) {
                    // R9: XLPR11 (30/01/2026): o aviso de ANGC11 (R$ 770, outra classe do mesmo CNPJ) ao lado do de
                    // XLPR11; LPLP11 e SPG211: linhas de outra classe no mesmo aviso
                    continue;
                }
                if (p.paymentDate() != null && p.isin() != null && ownIsinPayments.contains(p.paymentDate())
                        && !ownIsin(assets, p)) {
                    continue;
                }
                if (p.paymentDate() != null && ownTickerPayments.contains(p.paymentDate()) && otherClassTicker(assets, p)) {
                    continue;
                }
                if (p.paymentDate() != null && p.dataBase().isAfter(p.paymentDate())) {
                    invalid++;
                    // relido do disco a cada rodada (F10, sem estado no catalogo): o resumo abaixo conta
                    log.debug("Proventos: aviso {} do FNET ({}) com data-base {} depois do pagamento {} — fora; revisar na fonte",
                            id, assets.get(0).getTicker(), p.dataBase(), p.paymentDate());
                    continue;
                }
                Asset target = target(assets, p);
                if (target == null) {
                    continue;
                }
                DividendType type = p.amortization() ? DividendType.AMORTIZACAO : DividendType.RENDIMENTO;
                if (fromSameNotice(target, type, p, id)) {
                    // a linha ja gravada por este aviso (talvez com o valor de outra fonte, quando o FNET completou a
                    // B3): reler o aviso nao a duplica
                    continue;
                }
                BigDecimal v = sameValue(target, type, p.dataBase(), p.value(), CashEventSource.FNET);
                if (absorb(target, type, p, v, id, inactive, statusKnown)) {
                    saved++;
                    continue;
                }
                CashEventData data = new CashEventData(type, type.name(), p.dataBase(), p.paymentDate(),
                        v, null, p.referencePeriod(),
                        p.isin() != null ? p.isin() : target.getIsin(), p.incomeTaxExempt(), CashEventSource.FNET,
                        String.valueOf(id),
                        // Cada aviso e um documento; o mesmo aviso republicado
                        // (nova versao) e a mesma noticia: sempre parcela 1.
                        (short) 1);
                if (cashEventWriter.save(target, data)) {
                    saved++;
                }
            }
        }
        c.cash += saved;
        // sem estado no catalogo (F10), avisos que nao viram evento (recibo, data-base invalida, provento
        // ja gravado pela B3) sao relidos do disco a cada rodada: so loga quando algo mudou
        boolean news = saved > 0 || !batch.failed().isEmpty() || batch.downloaded() > 0;
        String fmt = "Proventos: FNET de {} ({}) — {} avisos, {} lidos agora ({} baixados), {} eventos novos{}{}{}";
        Object[] args = {
                assets.get(0).getTicker(), cnpj, ids.size(), missing.size(), batch.downloaded(), saved,
                batch.failed().isEmpty() ? "" : ", " + batch.failed().size() + " nao obtidos (nova tentativa na proxima rotina)",
                otherFund == 0 ? "" : ", " + otherFund + " de outro CNPJ",
                invalid == 0 ? "" : ", " + invalid + " com data-base invalida"};
        if (news) {
            log.info(fmt, args);
        } else {
            log.debug(fmt, args);
        }
        return batch.failed().isEmpty();
    }

    /**
     * Aviso novo no FNET (PENDENCIAS 18.10 d): quando a B3 ja mostra um provento com data-com
     * depois do ultimo aviso gravado do fundo, busca os avisos do CNPJ no FNET, baixa os que nao
     * estao em {@code carga/data/fnet} e aplica. No maximo uma busca por dia por fundo; FNET fora do ar
     * nao derruba nada — a B3 grava o provento como reserva e a busca volta no dia seguinte.
     */
    private boolean searchNewNotices(String cnpj, List<Asset> assets, LocalDate latestB3, Counters c) {
        if (latestB3 == null) {
            return true;
        }
        LocalDate latestFnet = assets.stream()
                .map(a -> cashEventRepository.findLatestDataComBySource(a.getId(), CashEventSource.FNET))
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        if (latestFnet != null && !latestFnet.isBefore(latestB3)) {
            return true;
        }
        Instant last = lastFnetSearch.get(cnpj);
        if (last != null && last.plusSeconds(FNET_SEARCH_COOLDOWN_SECONDS).isAfter(Instant.now())) {
            return true;
        }
        lastFnetSearch.put(cnpj, Instant.now());
        List<Long> found;
        try {
            found = fnetClient.incomeNoticeIds(cnpj);
        } catch (RuntimeException e) {
            log.warn("Proventos: busca no FNET de {} ({}) falhou — B3 fica de reserva: {}",
                    assets.get(0).getTicker(), cnpj, e.getMessage());
            notices.sourceUnavailable("Proventos", "FNET", "busca de " + assets.get(0).getTicker() + " falhou ("
                    + e.getMessage() + ") — a B3 ficou de reserva");
            return false;
        }
        Set<Long> cached = new HashSet<>(fnetClient.cachedNoticeIds(cnpj));
        long news = found.stream().filter(id -> !cached.contains(id)).count();
        if (news == 0) {
            log.debug("Proventos: FNET de {} ({}) sem aviso novo; B3 em {} fica de reserva",
                    assets.get(0).getTicker(), cnpj, latestB3);
            return true;
        }
        log.info("Proventos: FNET de {} ({}) — {} aviso(s) novo(s) a baixar", assets.get(0).getTicker(), cnpj, news);
        // o download entra no indice local; loadFund aplica o que ainda nao foi gravado
        fnetClient.incomeNotices(found.stream().filter(id -> !cached.contains(id)).toList());
        return loadFund(cnpj, assets, c);
    }

    /**
     * Situacao no FNET (ativo, inativo, cancelado) dos avisos de todos os fundos com aviso em {@code carga/data/fnet}
     * (D5 do docs/planos/PLANO_CORRECAO_PROVENTOS.md). Grava {@code carga/data/fnet/status/{CNPJ}.json}; a rotina
     * diaria atualiza o arquivo de cada fundo sempre que busca aviso novo. {@code onlyMissing}: so os fundos ainda sem
     * arquivo (retomar depois de falhas).
     *
     * <p>O FNET segura as buscas seguidas do mesmo endereco (a resposta nao vem ate o tempo limite): uma busca por vez,
     * com pausa entre os fundos ({@code fnet.status-pause-millis}) e uma pausa longa depois de cada falha.</p>
     */
    public int refreshFnetStatus(String reason, boolean onlyMissing) {
        java.util.List<String> cnpjs = fnetClient.cachedCnpjs().stream()
                .filter(c -> !onlyMissing || !fnetClient.hasStatus(c)).toList();
        log.info("Proventos: situacao dos avisos no FNET ({}) — {} fundos{}", reason, cnpjs.size(),
                onlyMissing ? " sem arquivo de situacao" : "");
        int ok = 0;
        int fail = 0;
        for (String cnpj : cnpjs) {
            TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
            try {
                fnetClient.incomeNoticeIds(cnpj);
                ok++;
                sleepQuietly(fnetStatusPauseMillis);
            } catch (RuntimeException e) {
                fail++;
                log.warn("Proventos: situacao dos avisos de {} nao obtida: {}", cnpj, e.getMessage());
                sleepQuietly(fnetStatusPauseMillis * 10);
            }
            if ((ok + fail) % 25 == 0) {
                log.info("Proventos: situacao no FNET — {} de {} fundos ({} falhas)", ok + fail, cnpjs.size(), fail);
            }
        }
        log.info("Proventos: situacao dos avisos no FNET concluida — {} fundos, {} falhas (missing=true retoma as falhas)",
                ok, fail);
        return fail;
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Data-com mais recente que a B3 (resposta + arquivo) mostra para o grupo. */
    private static LocalDate latestB3DataCom(B3Supplement supplement) {
        if (supplement == null) {
            return null;
        }
        return supplement.cash().stream().map(d -> B3Values.date(d.lastDatePrior()))
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    /**
     * O ativo do provento: fundo com uma cota negociada leva tudo (o ticker do aviso pode
     * ser o de antes da renomeacao); com mais de uma, pelo ISIN ou pelo ticker.
     */
    private Asset target(List<Asset> assets, FnetDividend p) {
        if (p.isin() != null && !tickerOfGroup(assets, p) && isinOfOtherIssuer(assets, p.isin())) {
            // R7: ISIN da cota de outro ativo do catalogo, de outro emissor, sem o ticker deste fundo (incorporacao:
            // o aviso paga a cota do fundo incorporado). Com o ticker do fundo, vale o ticker: BRIP11 publicou o
            // rendimento de 07/02/2025 (R$ 36,85) com o ISIN de VCRI11, do mesmo administrador
            log.debug("Proventos: aviso do FNET de {} com o ISIN {} de outro fundo do catalogo — fora",
                    assets.get(0).getTicker(), p.isin());
            return null;
        }
        List<Asset> quotas = assets.stream().filter(a -> !a.getTicker().toUpperCase().endsWith("F")).toList();
        if (quotas.size() == 1) {
            return quotas.get(0);
        }
        for (Asset a : assets) {
            if ((p.isin() != null && p.isin().equalsIgnoreCase(a.getIsin()))
                    || (p.ticker() != null && p.ticker().equalsIgnoreCase(baseTicker(a.getTicker())))) {
                return a;
            }
        }
        return null;
    }

    /**
     * CNPJ do fundo: o do emissor ({@code issuers.cnpj}). FII sem emissor fica sem historico
     * alem dos 12 meses da B3 ate ser ligado (F3: {@code POST /admin/issuers/link}) — o
     * cadastro da CVM em memoria e o CNPJ no ativo sairam na F10.
     */
    private String fundCnpj(Asset asset) {
        List<String> issuer = jdbc.queryForList("""
                SELECT i.cnpj FROM assets a JOIN issuers i ON i.id = a.issuer_id WHERE a.id = ?""",
                String.class, asset.getId());
        return issuer.isEmpty() ? null : issuer.get(0);
    }

    /**
     * O mesmo provento ja gravado por <b>outra fonte</b> com outras casas (FNET 1.03999999900318, B3 1.04): usa
     * o valor gravado, para o {@link CashEventWriter} completar em vez de duplicar (R1 do
     * docs/planos/PLANO_CORRECAO_PROVENTOS.md). O valor exato, se ja existe, vence. Dentro da mesma familia de
     * fontes (B3 e o historico longo da B3; ou o FNET) valores quase iguais sao parcelas diferentes e nao se
     * confundem — foi o que duplicou as parcelas de KLBN, RECV3 e TRIS3 em 02/10/2026.
     */
    private BigDecimal sameValue(Asset asset, DividendType type, LocalDate dataCom, BigDecimal value,
                                 CashEventSource source) {
        BigDecimal scaled = value.setScale(VALUE_SCALE, RoundingMode.HALF_UP);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT value_per_share, source FROM asset_cash_events
                WHERE asset_id = ? AND type = ? AND data_com = ? ORDER BY value_per_share, id""",
                asset.getId(), type.name(), java.sql.Date.valueOf(dataCom));
        for (Map<String, Object> row : existing) {
            if (((BigDecimal) row.get("value_per_share")).compareTo(scaled) == 0) {
                return value;
            }
        }
        boolean fromFnet = source == CashEventSource.FNET;
        for (Map<String, Object> row : existing) {
            boolean rowFnet = CashEventSource.FNET.name().equals(row.get("source"));
            BigDecimal v = (BigDecimal) row.get("value_per_share");
            if (rowFnet != fromFnet && v.subtract(value).abs().compareTo(SAME_VALUE_TOLERANCE) <= 0) {
                return v;
            }
        }
        return value;
    }

    /** O evento desta linha ja foi gravado por este mesmo aviso (mesmo tipo e data-base, valor dentro da tolerancia). */
    private boolean fromSameNotice(Asset asset, DividendType type, FnetDividend p, long noticeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM asset_cash_events WHERE asset_id = ? AND type = ? AND data_com = ?
                  AND source = 'FNET' AND source_ref = ? AND abs(value_per_share - ?) <= ?)""", Boolean.class,
                asset.getId(), type.name(), java.sql.Date.valueOf(p.dataBase()), String.valueOf(noticeId), p.value(),
                SAME_VALUE_TOLERANCE));
    }

    /** R6: o FNET ja gravou o provento (mesmo tipo, pagamento e valor), ainda que com outra data-base. */
    private boolean fnetHasSamePayment(Asset asset, DividendType type, LocalDate payment, BigDecimal value) {
        if (payment == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM asset_cash_events WHERE asset_id = ? AND type = ? AND source = 'FNET'
                  AND payment_date = ? AND abs(value_per_share - ?) <= ?)""", Boolean.class,
                asset.getId(), type.name(), java.sql.Date.valueOf(payment), value, SAME_VALUE_TOLERANCE));
    }

    /**
     * R5 e R6 (docs/planos/PLANO_CORRECAO_PROVENTOS.md): o aviso traz um provento (mesmo tipo, pagamento e valor)
     * que ja esta gravado — pela B3, como reserva, com outra data-com; ou por um aviso que o FNET marcou depois como
     * inativo ou cancelado (o fundo corrigiu a data-base, ou so republicou o aviso). O evento e o mesmo: e atualizado
     * no lugar, passando a apontar para o aviso ativo, com o id preservado (os proventos da carteira acompanham no
     * recalculo), em vez de nascer um segundo.
     *
     * <p>R10: a retificacao que so muda o pagamento (MXRF11: aviso 1025775 pagava em 07/11/2025, o 1026624 o trocou
     * por 14/11/2025, mesma data-base e valor) chega com a mesma chave do evento ja gravado pelo aviso inativo: e o
     * mesmo evento, com o pagamento novo — so quando o FNET marcou o aviso antigo como inativo ou cancelado.</p>
     *
     * @return se o provento foi absorvido por um evento existente
     */
    private boolean absorb(Asset asset, DividendType type, FnetDividend p, BigDecimal value, long noticeId,
                           Set<Long> inactive, boolean statusKnown) {
        if (p.paymentDate() == null) {
            return false;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, source, source_ref, data_com, payment_date FROM asset_cash_events
                WHERE asset_id = ? AND type = ? AND abs(value_per_share - ?) <= ?
                  AND (payment_date = ? OR (source = 'FNET' AND data_com = ?
                       AND payment_date IS DISTINCT FROM ?)) ORDER BY id""",
                asset.getId(), type.name(), value, SAME_VALUE_TOLERANCE, java.sql.Date.valueOf(p.paymentDate()),
                java.sql.Date.valueOf(p.dataBase()), java.sql.Date.valueOf(p.paymentDate()));
        for (Map<String, Object> row : rows) {
            String source = (String) row.get("source");
            String ref = (String) row.get("source_ref");
            boolean samePayment = row.get("payment_date") != null
                    && ((java.sql.Date) row.get("payment_date")).toLocalDate().equals(p.paymentDate());
            if (!samePayment) {
                // R10: so o aviso que o FNET marcou como inativo ou cancelado cede o pagamento
                boolean replacedRef = CashEventSource.FNET.name().equals(source) && ref != null
                        && ref.matches("\\d+") && !ref.equals(String.valueOf(noticeId))
                        && inactive.contains(Long.valueOf(ref));
                if (!replacedRef) {
                    continue;
                }
            }
            boolean sameDate = ((java.sql.Date) row.get("data_com")).toLocalDate().equals(p.dataBase());
            // a reserva da B3 com a mesma data-com o CashEventWriter ja passa para o FNET (D15)
            boolean reserve = CashEventSource.B3.name().equals(source) && !sameDate;
            boolean fnetRef = CashEventSource.FNET.name().equals(source) && ref != null && ref.matches("\\d+")
                    && !ref.equals(String.valueOf(noticeId));
            // sem o arquivo de situacao do fundo: o aviso mais novo com a data-base ate 7 dias da anterior e a
            // retificacao (conferido onde ha situacao: o antigo inativo ou cancelado em 7 de 8 pares)
            long gap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                    ((java.sql.Date) row.get("data_com")).toLocalDate(), p.dataBase()));
            // R11: a situacao conhecida (em qualquer arquivo) vale; o arquivo do fundo, quando existe, e completo
            boolean replaced = fnetRef && (inactive.contains(Long.valueOf(ref))
                    || (!statusKnown && Long.parseLong(ref) < noticeId && gap <= 7));
            if (!reserve && !replaced) {
                continue;
            }
            try {
                jdbc.update("""
                        UPDATE asset_cash_events SET data_com = ?, payment_date = ?, value_per_share = ?, seq = 1,
                          source = 'FNET', source_ref = ?, isin = COALESCE(?, isin),
                          related_to = COALESCE(?, related_to), income_tax_exempt = COALESCE(?, income_tax_exempt),
                          payment_date_source = NULL, fetched_at = now()
                        WHERE id = ?""",
                        java.sql.Date.valueOf(p.dataBase()), java.sql.Date.valueOf(p.paymentDate()),
                        value.setScale(VALUE_SCALE, RoundingMode.HALF_UP), String.valueOf(noticeId), p.isin(),
                        p.referencePeriod(), p.incomeTaxExempt(), row.get("id"));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                return false;
            }
            log.info("Proventos: {} {} pago em {} — aviso {} do FNET {} (data-com {}{})", asset.getTicker(), type,
                    p.paymentDate().format(BR), noticeId, reserve ? "substitui a reserva da B3" : "substitui o aviso "
                            + ref + ", inativo no FNET", p.dataBase().format(BR),
                    samePayment ? "" : "; pagamento era " + row.get("payment_date"));
            return true;
        }
        return false;
    }

    /**
     * R3: o provento do aviso e de outra classe do fundo — recibo de subscricao ou direito (ISIN com "R" no 7o
     * caractere, mesmo com o ticker da cota; ou ticker com final diferente de 11 que nao e de nenhum ativo do grupo,
     * como TRXF26).
     */
    static boolean otherClass(List<Asset> assets, FnetDividend p) {
        String isin = p.isin() == null ? null : p.isin().trim().toUpperCase();
        String ticker = p.ticker() == null ? null : p.ticker().trim().toUpperCase();
        for (Asset a : assets) {
            if (isin != null && isin.equalsIgnoreCase(a.getIsin())) {
                return false;
            }
        }
        if (isin != null && RECEIPT_ISIN.matcher(isin).matches()) {
            // o ISIN de recibo vence o ticker: o administrador repete o ticker da cota nas linhas dos recibos
            // (ARRI11, aviso 709290: cota 0,10 e recibos R28, R29 e R30 0,03, todos "ARRI11")
            return true;
        }
        if (tickerOfGroup(assets, p)) {
            return false;
        }
        if (ticker != null) {
            Matcher m = FUND_TICKER.matcher(ticker);
            return m.matches() && !"11".equals(m.group(2));
        }
        return false;
    }

    /** A linha do aviso e da propria cota: ISIN de um ativo do grupo ou ticker do fundo. */
    private static boolean ownLine(List<Asset> assets, FnetDividend p) {
        return ownIsin(assets, p) || tickerOfGroup(assets, p);
    }

    /**
     * R13: ticker de outra classe do proprio fundo — mesma raiz de um ativo do grupo e outro final (URPR13 de URPR11).
     * Sozinho no dia, vale a linha (EXES11, 20/10/2022: o rendimento da cota publicado com o ticker EXES13).
     */
    private static boolean otherClassTicker(List<Asset> assets, FnetDividend p) {
        if (p.ticker() == null || tickerOfGroup(assets, p)) {
            return false;
        }
        Matcher m = FUND_TICKER.matcher(p.ticker().trim().toUpperCase());
        return m.matches() && assets.stream().anyMatch(a -> baseTicker(a.getTicker()).startsWith(m.group(1)));
    }

    /** O ISIN da linha e o de um ativo do grupo. */
    private static boolean ownIsin(List<Asset> assets, FnetDividend p) {
        return p.isin() != null && assets.stream().anyMatch(a -> p.isin().trim().equalsIgnoreCase(a.getIsin()));
    }

    /**
     * R9/R12: pagamentos do fundo com a linha da propria cota — no lote e nos eventos ja gravados com o ISIN do ativo.
     * {@code isinOnly}: so as linhas com o ISIN de um ativo do grupo (sem contar o ticker).
     */
    private Set<LocalDate> ownPaymentDates(List<Asset> assets, FnetClient.Batch batch, boolean isinOnly) {
        Set<LocalDate> dates = new HashSet<>();
        for (FnetNotice notice : batch.notices().values()) {
            for (FnetDividend p : notice.dividends()) {
                if (p.paymentDate() != null && (isinOnly ? ownIsin(assets, p) : ownLine(assets, p))) {
                    dates.add(p.paymentDate());
                }
            }
        }
        for (Asset a : assets) {
            if (a.getIsin() != null) {
                dates.addAll(jdbc.queryForList("""
                        SELECT DISTINCT payment_date FROM asset_cash_events
                        WHERE asset_id = ? AND isin = ? AND payment_date IS NOT NULL""", LocalDate.class,
                        a.getId(), a.getIsin()));
            }
        }
        return dates;
    }

    /** O ticker do provento e o de um ativo do grupo (MAXR11B conta como MAXR11). */
    private static boolean tickerOfGroup(List<Asset> assets, FnetDividend p) {
        if (p.ticker() == null) {
            return false;
        }
        String t = p.ticker().trim().toUpperCase();
        Matcher m = FUND_TICKER.matcher(t);
        String base = m.matches() ? m.group(1) + m.group(2) : t;
        return assets.stream().anyMatch(a -> baseTicker(a.getTicker()).equals(base));
    }

    /** R7: o ISIN e da cota de outro ativo do catalogo, ligado a outro emissor. */
    private boolean isinOfOtherIssuer(List<Asset> assets, String isin) {
        String code = isin.trim().toUpperCase();
        if (assets.stream().anyMatch(a -> code.equalsIgnoreCase(a.getIsin()))) {
            return false;
        }
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM assets o
                WHERE o.isin = ? AND o.issuer_id IS NOT NULL
                  AND o.issuer_id IS DISTINCT FROM (SELECT issuer_id FROM assets WHERE id = ?)""",
                Integer.class, code, assets.get(0).getId());
        return n != null && n > 0;
    }

    // ------------------------------------------------------------------
    // Gravacao
    // ------------------------------------------------------------------


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


    /** "Catalogo mudou" para a app-api refazer proventos e valorizacao dessas posicoes. */
    private void publishChange(List<Asset> assets) {
        Set<Long> ids = new HashSet<>();
        assets.forEach(a -> ids.add(a.getId()));
        if (!ids.isEmpty()) {
            catalogPublisher.publish(new CatalogChange(CatalogChange.Origin.PROVENTOS, ids));
        }
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

        void add(Counters other) {
            cash += other.cash;
            stock += other.stock;
        }
    }

}
