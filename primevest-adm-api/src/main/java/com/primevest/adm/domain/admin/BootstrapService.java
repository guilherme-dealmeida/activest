package com.primevest.adm.domain.admin;

import com.primevest.adm.domain.dividend.CvmPaymentDateService;
import com.primevest.adm.domain.dividend.PaymentDateSurveyService;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.primevest.adm.domain.fundamentals.FinancialStatementImportService;
import com.primevest.adm.domain.issuer.IssuerImportService;
import com.primevest.adm.domain.marketindex.MarketIndexImportService;
import com.primevest.adm.domain.pricehistory.HistoricalQuotesImportService;
import com.primevest.adm.domain.pricehistory.TreasuryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Carga inicial num banco vazio (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 6.3 e F11; D13: manual, nunca na subida).
 *
 * <p>Emissores do catalogo, ligacoes e units vem do seed (V30); aqui entra o volume, em ordem:</p>
 * <ol>
 *   <li><b>emissores</b> — cadastro, FCA e informe da CVM: completa o seed com as companhias e os FIIs
 *   negociados que nao estao no catalogo (o banco novo fica igual ao de quem ja rodou a rotina de
 *   emissores); nunca sobrescreve o segmento curado nem as ligacoes;</li>
 *   <li><b>COTAHIST</b> 2010+ — precos e o ISIN de cada ativo (a rotina de proventos casa a B3 pelo ISIN);</li>
 *   <li><b>proventos</b> — rodada completa do catalogo: 12 meses da B3 (com o acumulado de
 *   {@code carga/data/b3/proventos-12m}), eventos societarios e o historico longo (acoes de
 *   {@code carga/data/b3/proventos}, FII de {@code carga/data/fnet});</li>
 *   <li><b>datas de pagamento</b> 2022+ — relatorios de proventos da CVM ({@code carga/data/cvm/ipe},
 *   {@code carga/data/cvm/proventos}) completam a data que o historico da B3 nao traz (docs/planos/PLANO_CARGA_INICIAL.md, 5.2);</li>
 *   <li><b>datas de pagamento do levantamento</b> 2010-2026 ({@code carga/data/levantamento/datas-pagamento.json})
 *   completam o que ainda falta, sem passar por cima da B3, do FNET nem da CVM (docs/planos/PLANO_CARGA_INICIAL.md, 11);</li>
 *   <li><b>balancos</b> DFP/ITR 2010+ e a serie trimestral — depois dos proventos, porque a passada
 *   {@code EVENTO} da escala usa os grupamentos e desdobramentos gravados;</li>
 *   <li><b>informe mensal de FII</b> 2016+;</li>
 *   <li><b>indices de mercado</b> 2010+ (CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, PTAX, Ibovespa, IFIX —
 *   docs/planos/PLANO_INDICES_MERCADO.md, 4.1): independente das outras; anos fechados de
 *   {@code carga/data/bcb} e {@code carga/data/b3/indices}.</li>
 *   <li><b>preco do Tesouro Direto</b> 2010+ (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R1): o PU de venda de cada
 *   titulo, de {@code carga/data/tesouro} (ou do Tesouro Transparente, se mudou).</li>
 *   <li><b>historico de cripto</b> (docs/planos/PLANO_RENTABILIDADE.md, 3.3): {@code carga/data/coingecko/historico}
 *   e {@code carga/data/binance/klines} — o benchmark BTC e o que estiver em carteira.</li>
 * </ol>
 *
 * <p><b>Retomavel:</b> toda etapa e idempotente e o que ja esta em {@code carga/data/} nao e baixado de novo
 * (o arquivo e o estado). Etapa que falha e registrada e a seguinte roda assim mesmo; rodar de novo
 * completa o que faltou. Uma carga por vez.</p>
 */
@Slf4j
@Service
public class BootstrapService {

    /** Falha da rotina vira mensagem de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.adm.domain.notice.AdminNotices notices;

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final IssuerImportService issuers;
    private final HistoricalQuotesImportService quotes;
    private final ProventosCatalogService proventos;
    private final FinancialStatementImportService statements;
    private final FiiMonthlyImportService fiiMonthly;
    private final CvmPaymentDateService paymentDates;
    private final PaymentDateSurveyService surveyDates;
    private final MarketIndexImportService indices;
    private final TreasuryPriceService treasury;
    private final com.primevest.adm.domain.fixedincome.FixedIncomeIssuerImportService fixedIncomeIssuers;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    /** Historico de cripto (docs/planos/PLANO_RENTABILIDADE.md, 3.3); opcional para os testes que montam a carga. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.primevest.adm.domain.pricehistory.CryptoHistoryService cryptoHistory;

    @org.springframework.beans.factory.annotation.Autowired
    public BootstrapService(IssuerImportService issuers, HistoricalQuotesImportService quotes,
                            ProventosCatalogService proventos, FinancialStatementImportService statements,
                            FiiMonthlyImportService fiiMonthly, CvmPaymentDateService paymentDates,
                            PaymentDateSurveyService surveyDates, MarketIndexImportService indices,
                            TreasuryPriceService treasury,
                            com.primevest.adm.domain.fixedincome.FixedIncomeIssuerImportService fixedIncomeIssuers) {
        this(issuers, quotes, proventos, statements, fiiMonthly, paymentDates, surveyDates, indices, treasury,
                fixedIncomeIssuers, Clock.system(ZONE));
    }

    BootstrapService(IssuerImportService issuers, HistoricalQuotesImportService quotes,
                     ProventosCatalogService proventos, FinancialStatementImportService statements,
                     FiiMonthlyImportService fiiMonthly, CvmPaymentDateService paymentDates,
                     PaymentDateSurveyService surveyDates, MarketIndexImportService indices,
                     TreasuryPriceService treasury,
                     com.primevest.adm.domain.fixedincome.FixedIncomeIssuerImportService fixedIncomeIssuers,
                     Clock clock) {
        this.issuers = issuers;
        this.quotes = quotes;
        this.proventos = proventos;
        this.statements = statements;
        this.fiiMonthly = fiiMonthly;
        this.paymentDates = paymentDates;
        this.surveyDates = surveyDates;
        this.indices = indices;
        this.treasury = treasury;
        this.fixedIncomeIssuers = fixedIncomeIssuers;
        this.clock = clock;
    }

    /** Uma etapa: nome, se terminou sem excecao, segundos, resumo (ou o erro). */
    public record Step(String name, boolean ok, long seconds, String summary) {
    }

    public record Result(List<Step> steps, long seconds) {
        public boolean ok() {
            return steps.stream().allMatch(Step::ok);
        }
    }

    /** Se ja ha uma carga rodando, nao faz nada e devolve {@code null}. */
    public Result run(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Carga inicial ({}): ja existe uma em andamento; nada feito", trigger);
            return null;
        }
        try {
            long started = System.currentTimeMillis();
            int year = LocalDate.now(clock).getYear();
            String reason = "carga inicial " + trigger;
            List<Step> steps = new ArrayList<>();
            log.info("Carga inicial ({}): emissores, COTAHIST, proventos, datas de pagamento, balancos, informe de FII, indices e Tesouro", trigger);
            steps.add(step("emissores", () -> issuers.importAll(reason)));
            steps.add(step("emissoras de renda fixa", () -> fixedIncomeIssuers.importAll(reason)));
            steps.add(step("COTAHIST " + HistoricalQuotesImportService.FIRST_YEAR + "-" + year,
                    () -> quotes.importYears(HistoricalQuotesImportService.FIRST_YEAR, year, reason)));
            steps.add(step("proventos", () -> proventos.syncAll(reason)));
            steps.add(step("datas de pagamento CVM " + CvmPaymentDateService.FIRST_YEAR + "-" + year,
                    () -> paymentDates.run(CvmPaymentDateService.FIRST_YEAR, year, reason)));
            steps.add(step("datas de pagamento do levantamento", () -> surveyDates.run(reason)));
            steps.add(step("balancos " + FinancialStatementImportService.FIRST_YEAR + "-" + year,
                    () -> statements.importYears(FinancialStatementImportService.FIRST_YEAR, year, false, reason)));
            steps.add(step("informe FII " + FiiMonthlyImportService.FIRST_YEAR + "-" + year,
                    () -> fiiMonthly.importYears(FiiMonthlyImportService.FIRST_YEAR, year, false, reason)));
            steps.add(step("indices de mercado", () -> indices.importAll(reason)));
            steps.add(step("preco do Tesouro " + TreasuryPriceService.FIRST_YEAR + "+",
                    () -> treasury.importFrom(TreasuryPriceService.FIRST_YEAR, reason)));
            if (cryptoHistory != null) {
                steps.add(step("historico de cripto", () -> cryptoHistory.importAll(reason)));
            }
            Result r = new Result(steps, (System.currentTimeMillis() - started) / 1000);
            log.info("Carga inicial ({}) concluida em {}s{}", trigger, r.seconds(),
                    r.ok() ? "" : " COM FALHA — rode de novo: o que ja entrou e pulado");
            steps.forEach(s -> log.info("Carga inicial: {} — {} em {}s: {}", s.name(), s.ok() ? "ok" : "FALHOU",
                    s.seconds(), s.summary()));
            return r;
        } finally {
            running.set(false);
        }
    }

    private Step step(String name, Supplier<Object> work) {
        long t = System.currentTimeMillis();
        log.info("Carga inicial: {}...", name);
        try {
            Object result = work.get();
            return new Step(name, result != null, (System.currentTimeMillis() - t) / 1000,
                    result != null ? String.valueOf(result) : "nao rodou (outra execucao em andamento)");
        } catch (RuntimeException e) {
            log.error("Carga inicial: {} falhou: {}", name, e.getMessage(), e);
            if (notices != null) {
                notices.routineFailed("Carga inicial — " + name, e.getMessage());
            }
            return new Step(name, false, (System.currentTimeMillis() - t) / 1000, e.getMessage());
        }
    }
}
