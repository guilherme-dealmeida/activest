package com.primevest.adm.domain.admin;

import com.primevest.adm.domain.dividend.CvmPaymentDateService;
import com.primevest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.primevest.adm.domain.fundamentals.FinancialStatementImportService;
import com.primevest.adm.domain.issuer.IssuerLinkService;
import com.primevest.adm.domain.marketindex.MarketIndexImportService;
import com.primevest.adm.domain.pricehistory.HistoricalQuotesImportService;
import com.primevest.adm.domain.pricehistory.TreasuryPriceService;
import com.primevest.adm.task.FiiCnpjCurationTask;
import com.primevest.adm.task.UnitCompositionTask;
import com.primevest.config.SecurityUtils;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.issuer.IssuerRepository;
import com.primevest.domain.issuer.ShareClass;
import com.primevest.domain.task.TaskExecution;
import com.primevest.domain.task.TaskExecutor;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disparo manual das rotinas externas (docs/planos/PLANO_BRAPI.md: "semanal e sob demanda"). So para usuario com
 * {@code is_admin}.
 *
 * <p>adm-api (docs/planos/PLANO_ADM_API.md, 10): so rotinas de catalogo.</p>
 *
 * <p>Desde as Tarefas (docs/planos/PLANO_TAREFAS.md, D16 e D22) as rotas e as respostas ficam como eram, mas o
 * disparo passa pelo {@link TaskExecutor}: cada pedido vira uma rodada da tarefa do tipo, com os parametros da rota
 * por cima dos atributos dela, e aparece no historico da tela de Tarefas. A resposta 202 ganhou o
 * {@code executionId}. A curadoria continua sincrona e devolvendo o resultado.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final IssuerRepository issuerRepository;
    private final MarketIndexImportService marketIndexImportService;
    private final TaskExecutor executor;

    @PostMapping("/catalog-audit")
    public ResponseEntity<Map<String, Object>> catalogAudit() {
        return start("conferencia do catalogo", "CATALOG_AUDIT", Map.of());
    }

    @PostMapping("/logos")
    public ResponseEntity<Map<String, Object>> logos() {
        return start("logos", "ASSET_LOGOS", Map.of("ONLY_MISSING", "false"));
    }

    @PostMapping("/closing")
    public ResponseEntity<Map<String, Object>> closing() {
        UUID user = requireAdmin().getId();
        TaskExecution b3 = executor.runType("B3_CLOSING", user, Map.of());
        TaskExecution crypto = executor.runType("CRYPTO_CLOSING", user, Map.of());
        Map<String, Object> body = accepted("fechamento");
        body.put("executionIds", List.of(b3.getId(), crypto.getId()));
        return ResponseEntity.accepted().body(body);
    }

    /**
     * Emissores: companhias (cadastro + FCA da CVM), FIIs (informe mensal) e a
     * classificacao setorial da B3 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F1).
     */
    @PostMapping("/issuers/import")
    public ResponseEntity<Map<String, Object>> issuersImport() {
        return start("emissores", "ISSUERS_IMPORT", Map.of());
    }

    /**
     * Emissoras e distribuidoras de renda fixa: BCB (IF.data) + CVM (intermediarios, companhias abertas, informes de
     * CRI/CRA). docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 4.
     */
    @PostMapping("/fixed-income-issuers")
    public ResponseEntity<Map<String, Object>> fixedIncomeIssuers() {
        return start("emissoras de renda fixa", "FIXED_INCOME_ISSUERS",
            Map.of("WITH_ISSUERS", "true", "WITH_LOGOS", "false"));
    }

    /**
     * Logos das emissoras e distribuidoras de renda fixa por raiz de CNPJ (Open Finance, logos-bancos-br), em
     * carga/data/logos/rf. {@code all=true} baixa de novo quem as fontes tiverem. Plano de emissoras, Adendo A.
     */
    @PostMapping("/fixed-income-issuers/logos")
    public ResponseEntity<Map<String, Object>> fixedIncomeLogos(@RequestParam(defaultValue = "false") boolean all) {
        return start("logos de renda fixa", "FIXED_INCOME_ISSUERS",
            Map.of("WITH_ISSUERS", "false", "WITH_LOGOS", "true", "LOGOS_REFRESH_ALL", String.valueOf(all)));
    }

    /** Ligacao ativo -> emissor: acoes (FCA, raiz, B3) e FIIs (ISIN). docs/planos/PLANO_EMPRESAS_BALANCOS.md, F3. */
    @PostMapping("/issuers/link")
    public ResponseEntity<Map<String, Object>> issuersLink() {
        return start("ligacao de emissores", "ISSUERS_LINK", Map.of());
    }

    /**
     * Lista curada de CNPJs de FII (ticker, cnpj): liga como MANUAL e cria o
     * emissor que faltar. Sincrono: a resposta traz a situacao de cada linha.
     */
    @PostMapping("/issuers/fii-cnpjs")
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<IssuerLinkService.CuratedLine>> curatedFunds(
            @RequestBody List<IssuerLinkService.FundCnpj> lines) {
        UUID user = requireAdmin().getId();
        if (lines == null || lines.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        var outcome = executor.runTypeSync("FII_CNPJ_CURATION", user,
            Map.of("LINES", FiiCnpjCurationTask.format(lines)));
        return ResponseEntity.ok((List<IssuerLinkService.CuratedLine>) outcome.result());
    }

    /**
     * Ligacao manual de um ativo a um emissor (curadoria), por {@code issuerId} ou
     * {@code cnpj} (companhia fora dos emissores e procurada no cadastro da CVM).
     * Sem os dois, desfaz.
     */
    public record ManualLink(Long issuerId, String cnpj, ShareClass shareClass) {
    }

    @PutMapping("/assets/{assetId}/issuer")
    public ResponseEntity<Void> manualLink(@PathVariable Long assetId, @RequestBody ManualLink body) {
        UUID user = requireAdmin().getId();
        String ticker = tickerOf(assetId);
        String cnpj = body.cnpj();
        if (body.issuerId() != null) {
            cnpj = issuerRepository.findById(body.issuerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Emissor nao encontrado: " + body.issuerId()))
                .getCnpj();
        }
        Map<String, String> values = new HashMap<>();
        values.put("TICKER", ticker);
        values.put("ISSUER_CNPJ", cnpj);
        values.put("SHARE_CLASS", body.shareClass() == null ? null : body.shareClass().name());
        try {
            executor.runTypeSync("ASSET_ISSUER_LINK", user, values);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /** Composicao manual de uma unit, ex.: {@code {"ON": 1, "PNA": 2}}. Marca a ligacao como MANUAL. */
    @PutMapping("/assets/{assetId}/unit-composition")
    public ResponseEntity<Void> unitComposition(@PathVariable Long assetId,
                                                @RequestBody Map<ShareClass, Integer> composition) {
        UUID user = requireAdmin().getId();
        String ticker = tickerOf(assetId);
        try {
            executor.runTypeSync("UNIT_COMPOSITION", user,
                Map.of("TICKER", ticker, "COMPOSITION", UnitCompositionTask.format(composition)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Serie historica da B3 (COTAHIST) em asset_price_history, de {@code from}
     * (padrao 2010) a {@code to} (padrao: ano corrente). docs/planos/PLANO_EMPRESAS_BALANCOS.md, F2.
     */
    @PostMapping("/b3/cotahist")
    public ResponseEntity<Map<String, Object>> cotahist(
            @RequestParam(defaultValue = "" + HistoricalQuotesImportService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to) {
        int last = to != null ? to : LocalDate.now(SAO_PAULO).getYear();
        if (from < 1986 || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("serie historica B3", "B3_YEARLY_QUOTES",
            Map.of("FROM_YEAR", String.valueOf(from), "TO_YEAR", String.valueOf(last)));
    }

    /**
     * Cotacoes pregao a pregao pelo COTAHIST diario (docs/planos/PLANO_PRECO_TETO.md, 4.5). Sem {@code from}: do
     * pregao seguinte ao ultimo gravado ate hoje (o mesmo da rotina); com {@code from}/{@code to}, o periodo.
     */
    @PostMapping("/b3/cotahist/diario")
    public ResponseEntity<Map<String, Object>> cotahistDiario(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<String, String> values = new HashMap<>();
        values.put("FROM_DATE", null);
        values.put("TO_DATE", null);
        if (from != null) {
            LocalDate last = to != null ? to : LocalDate.now(SAO_PAULO);
            if (last.isBefore(from) || from.isBefore(last.minusDays(366))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
            }
            values.put("FROM_DATE", from.toString());
            values.put("TO_DATE", last.toString());
        }
        return start("cotacoes diarias B3", "B3_DAILY_QUOTES", values);
    }

    /**
     * Balancos da CVM (DFP e ITR) de {@code from} (padrao 2010) a {@code to} (padrao: ano corrente).
     * Documento ja gravado na mesma versao e pulado; {@code force=true} reprocessa a partir de
     * carga/data/cvm, sem baixar de novo (regra nova). docs/planos/PLANO_EMPRESAS_BALANCOS.md, F5.
     */
    @PostMapping("/cvm/import")
    public ResponseEntity<Map<String, Object>> cvmImport(
            @RequestParam(defaultValue = "" + FinancialStatementImportService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean force) {
        int last = to != null ? to : LocalDate.now(SAO_PAULO).getYear();
        if (from < FinancialStatementImportService.FIRST_YEAR || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("balancos CVM", "CVM_STATEMENTS_IMPORT", Map.of("WITH_STATEMENTS", "true",
            "STATEMENTS_FROM_YEAR", String.valueOf(from), "STATEMENTS_TO_YEAR", String.valueOf(last),
            "FORCE", String.valueOf(force), "WITH_FII_MONTHLY", "false"));
    }

    /**
     * Recalcula a serie trimestral ({@code company_quarters}) a partir dos balancos ja gravados. A carga
     * de balancos ja faz isso ao terminar; aqui e para depois de corrigir quantidade de acoes a mao. F6.
     */
    @PostMapping("/cvm/quarters")
    public ResponseEntity<Map<String, Object>> cvmQuarters() {
        return start("serie trimestral", "CVM_QUARTERS", Map.of());
    }

    /**
     * Informe mensal de FII da CVM em fii_monthly, de {@code from} (padrao 2016) a {@code to} (padrao: ano
     * corrente). Mes ja gravado na mesma versao e pulado; {@code force=true} regrava a partir de carga/data/cvm/fii.
     * docs/planos/PLANO_EMPRESAS_BALANCOS.md, F7.
     */
    @PostMapping("/cvm/fii-monthly")
    public ResponseEntity<Map<String, Object>> fiiMonthly(
            @RequestParam(defaultValue = "" + FiiMonthlyImportService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean force) {
        int last = to != null ? to : LocalDate.now(SAO_PAULO).getYear();
        if (from < FiiMonthlyImportService.FIRST_YEAR || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("informe FII", "CVM_STATEMENTS_IMPORT", Map.of("WITH_STATEMENTS", "false",
            "WITH_FII_MONTHLY", "true", "FII_FROM_YEAR", String.valueOf(from), "FII_TO_YEAR", String.valueOf(last),
            "FORCE", String.valueOf(force)));
    }

    /**
     * Carga inicial num banco vazio (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 6.3, F11): emissores, COTAHIST, proventos, balancos
     * e informe de FII, em ordem. Retomavel; nunca dispara sozinha ao subir.
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap() {
        return start("carga inicial", "BOOTSTRAP", Map.of());
    }

    /**
     * Datas de pagamento pelos relatorios de proventos da CVM (docs/planos/PLANO_CARGA_INICIAL.md, 5.2 — P3):
     * {@code from}/{@code to} em anos, de 2022 em diante.
     */
    @PostMapping("/cvm/proventos")
    public ResponseEntity<Map<String, Object>> cvmProventos(
            @RequestParam(defaultValue = "" + CvmPaymentDateService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to) {
        int last = to != null ? to : LocalDate.now(SAO_PAULO).getYear();
        if (from < CvmPaymentDateService.FIRST_YEAR || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("datas de pagamento CVM", "CVM_PAYMENT_DATES",
            Map.of("FROM_YEAR", String.valueOf(from), "TO_YEAR", String.valueOf(last)));
    }

    /**
     * Datas de pagamento do levantamento ({@code carga/data/levantamento/datas-pagamento.json},
     * docs/planos/PLANO_CARGA_INICIAL.md secao 11): completa so os proventos sem data, sem passar por cima de
     * nenhuma outra fonte. Mesma etapa do bootstrap, para aplicar num banco que ja existe.
     */
    @PostMapping("/proventos/levantamento")
    public ResponseEntity<Map<String, Object>> proventosLevantamento() {
        return start("datas de pagamento do levantamento", "PAYMENT_DATE_SURVEY", Map.of());
    }

    /**
     * Indices de mercado (docs/planos/PLANO_INDICES_MERCADO.md, 4.1): {@code codes} separados por virgula (padrao:
     * todos os ativos), de {@code from} (padrao: {@code history_from} de cada indice, 2010) a {@code to} (padrao:
     * ano corrente). Ano fechado com arquivo em carga/data nao vai a fonte; valor igual e pulado, corrigido e
     * sobrescrito com log. {@code recent=true} roda a rodada incremental da rotina diaria (ignora o periodo).
     */
    @PostMapping("/indices")
    public ResponseEntity<Map<String, Object>> indices(
            @RequestParam(required = false) List<String> codes,
            @RequestParam(required = false) Integer from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean recent) {
        int last = to != null ? to : LocalDate.now(SAO_PAULO).getYear();
        if ((from != null && (from < 1986 || last < from)) || last < 1986) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        if (codes != null && !codes.isEmpty()) {
            try {
                marketIndexImportService.validateCodes(codes);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        Map<String, String> values = new HashMap<>();
        values.put("INDEX_CODES", null);
        values.put("FROM_YEAR", null);
        values.put("TO_YEAR", null);
        if (!recent) {
            // a carga do periodo (sem "from": desde o inicio de cada indice); o tipo so faz o incremental sem nada
            values.put("INDEX_CODES", codes == null || codes.isEmpty() ? null : String.join(",", codes));
            values.put("FROM_YEAR", from == null ? null : String.valueOf(from));
            values.put("TO_YEAR", String.valueOf(last));
        }
        return start("indices", "MARKET_INDICES", values);
    }

    /**
     * Preco oficial do Tesouro Direto (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R1): PU de venda de cada titulo do
     * catalogo desde {@code from} (padrao 2010) em asset_price_history e o ultimo em assets.current_price.
     * {@code recent=true} = a rodada diaria das 08:00. O arquivo do Tesouro (~14 MB) so e baixado se mudou.
     */
    @PostMapping("/tesouro")
    public ResponseEntity<Map<String, Object>> tesouro(
            @RequestParam(defaultValue = "" + TreasuryPriceService.FIRST_YEAR) int from,
            @RequestParam(defaultValue = "false") boolean recent) {
        if (from < 2002) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O historico do Tesouro comeca em 2002");
        }
        Map<String, String> values = new HashMap<>();
        values.put("FROM_YEAR", recent ? null : String.valueOf(from));
        return start("tesouro", "TREASURY_PRICES", values);
    }

    /**
     * Historico de preco de cripto (docs/planos/PLANO_RENTABILIDADE.md, 3.3): CoinGecko nos ultimos 365 dias (somado ao
     * arquivo de carga/data/coingecko/historico) e Binance BTC/BRL antes disso, para toda cripto que esta ou ja esteve
     * em carteira e para o benchmark BTC. So preenche dias sem linha.
     */
    @PostMapping("/cripto/historico")
    public ResponseEntity<Map<String, Object>> cryptoHistory() {
        return start("historico de cripto", "CRYPTO_PRICE_HISTORY", Map.of());
    }

    /**
     * Rodada de proventos. {@code fnet=todos}: rele todos os avisos do FNET em disco, inclusive os que ja geraram evento
     * (depois de uma limpeza de dados como a V60 — docs/planos/PLANO_CORRECAO_PROVENTOS.md, C8). Sem a etapa da CVM,
     * como antes (a tarefa agendada roda com ela).
     */
    @PostMapping("/proventos")
    public ResponseEntity<Map<String, Object>> proventos(@RequestParam(defaultValue = "") String fnet) {
        boolean all = "todos".equalsIgnoreCase(fnet);
        return start("proventos", "DIVIDENDS_SYNC",
            Map.of("FNET_REREAD_ALL", String.valueOf(all), "WITH_CVM_PAYMENT_DATES", "false"));
    }

    /**
     * Situacao no FNET de todos os avisos em disco (ativo, inativo, cancelado), em
     * {@code carga/data/fnet/status} — docs/planos/PLANO_CORRECAO_PROVENTOS.md, D5/R5. {@code missing=true}: so os
     * fundos ainda sem arquivo (retoma as falhas). Sem a releitura dos proventos, como antes.
     */
    @PostMapping("/proventos/fnet-status")
    public ResponseEntity<Map<String, Object>> fnetStatus(@RequestParam(defaultValue = "false") boolean missing) {
        return start("situacao dos avisos do FNET", "FNET_STATUS_SYNC",
            Map.of("ONLY_MISSING", String.valueOf(missing), "WITH_DIVIDENDS_REREAD", "false"));
    }

    private String tickerOf(Long assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo nao encontrado: " + assetId))
            .getTicker();
    }

    private User requireAdmin() {
        User user = userRepository.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador.");
        }
        return user;
    }

    private ResponseEntity<Map<String, Object>> start(String name, String type, Map<String, String> values) {
        UUID user = requireAdmin().getId();
        TaskExecution execution = executor.runType(type, user, values);
        Map<String, Object> body = accepted(name);
        body.put("executionId", execution.getId());
        body.put("status", execution.getStatus() == com.primevest.domain.task.TaskExecutionStatus.SKIPPED
            ? "pulada (ja em execucao)" : "iniciada");
        return ResponseEntity.accepted().body(body);
    }

    private static Map<String, Object> accepted(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "iniciada");
        body.put("rotina", name);
        body.put("resultado", "na tela de Tarefas e no log da API");
        return body;
    }
}
