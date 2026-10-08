package com.primevest.adm.domain.admin;

import com.primevest.config.SecurityUtils;
import com.primevest.adm.domain.asset.AssetLogoService;
import com.primevest.adm.domain.asset.CatalogAuditService;
import com.primevest.adm.domain.dividend.CvmPaymentDateService;
import com.primevest.adm.domain.dividend.PaymentDateSurveyService;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.adm.domain.fundamentals.CompanyQuarterService;
import com.primevest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.primevest.adm.domain.fundamentals.FinancialStatementImportService;
import com.primevest.adm.domain.issuer.IssuerImportService;
import com.primevest.adm.domain.issuer.IssuerLinkService;
import com.primevest.adm.domain.marketindex.MarketIndexImportService;
import com.primevest.domain.issuer.ShareClass;
import com.primevest.adm.domain.pricehistory.ClosingPriceService;
import com.primevest.adm.domain.pricehistory.HistoricalQuotesImportService;
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

import java.util.List;
import java.util.Map;

/**
 * Disparo manual das rotinas externas (docs/planos/PLANO_BRAPI.md: "semanal e sob
 * demanda"). So para usuario com {@code is_admin}.
 *
 * <p>adm-api (docs/planos/PLANO_ADM_API.md, 10): so rotinas de catalogo. O extrato de
 * proventos ({@code /proventos/extrato}) e importacao da carteira e ficou na
 * app-api ({@code ExtratoProventosController}).</p>
 *
 * <p>As rotinas rodam em segundo plano e respondem 202: o resultado sai no log
 * da adm-api (no Docker, {@code docker compose logs -f adm-api}).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final CatalogAuditService catalogAuditService;
    private final AssetLogoService assetLogoService;
    private final ClosingPriceService closingPriceService;
    private final ProventosCatalogService proventosCatalogService;
    private final IssuerImportService issuerImportService;
    private final IssuerLinkService issuerLinkService;
    private final HistoricalQuotesImportService historicalQuotesImportService;
    private final FinancialStatementImportService financialStatementImportService;
    private final CompanyQuarterService companyQuarterService;
    private final FiiMonthlyImportService fiiMonthlyImportService;
    private final BootstrapService bootstrapService;
    private final CvmPaymentDateService cvmPaymentDateService;
    private final PaymentDateSurveyService paymentDateSurveyService;
    private final MarketIndexImportService marketIndexImportService;

    @PostMapping("/catalog-audit")
    public ResponseEntity<Map<String, String>> catalogAudit() {
        return start("conferencia do catalogo", () -> {
            catalogAuditService.run("sob demanda");
            marketIndexImportService.audit("sob demanda");
        });
    }

    @PostMapping("/logos")
    public ResponseEntity<Map<String, String>> logos() {
        return start("logos", () -> assetLogoService.sync(false, "sob demanda"));
    }

    @PostMapping("/closing")
    public ResponseEntity<Map<String, String>> closing() {
        return start("fechamento", () -> {
            closingPriceService.runB3Closing("sob demanda");
            closingPriceService.runCryptoClosing("sob demanda");
        });
    }

    /**
     * Emissores: companhias (cadastro + FCA da CVM), FIIs (informe mensal) e a
     * classificacao setorial da B3 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F1).
     */
    @PostMapping("/issuers/import")
    public ResponseEntity<Map<String, String>> issuersImport() {
        return start("emissores", () -> issuerImportService.importAll("sob demanda"));
    }

    /** Ligacao ativo -> emissor: acoes (FCA, raiz, B3) e FIIs (ISIN). docs/planos/PLANO_EMPRESAS_BALANCOS.md, F3. */
    @PostMapping("/issuers/link")
    public ResponseEntity<Map<String, String>> issuersLink() {
        return start("ligacao de emissores", () -> issuerLinkService.linkAll("sob demanda"));
    }

    /**
     * Lista curada de CNPJs de FII (ticker, cnpj): liga como MANUAL e cria o
     * emissor que faltar. Sincrono: a resposta traz a situacao de cada linha.
     */
    @PostMapping("/issuers/fii-cnpjs")
    public ResponseEntity<List<IssuerLinkService.CuratedLine>> curatedFunds(
            @RequestBody List<IssuerLinkService.FundCnpj> lines) {
        requireAdmin();
        return ResponseEntity.ok(issuerLinkService.applyCuratedFunds(lines));
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
        requireAdmin();
        try {
            issuerLinkService.setManual(assetId, body.issuerId(), body.cnpj(), body.shareClass());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /** Composicao manual de uma unit, ex.: {@code {"ON": 1, "PNA": 2}}. Marca a ligacao como MANUAL. */
    @PutMapping("/assets/{assetId}/unit-composition")
    public ResponseEntity<Void> unitComposition(@PathVariable Long assetId,
                                                @RequestBody Map<ShareClass, Integer> composition) {
        requireAdmin();
        try {
            issuerLinkService.setUnitComposition(assetId, composition);
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
    public ResponseEntity<Map<String, String>> cotahist(
            @RequestParam(defaultValue = "" + HistoricalQuotesImportService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to) {
        int last = to != null ? to : java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
        if (from < 1986 || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("serie historica B3", () -> historicalQuotesImportService.importYears(from, last, "sob demanda"));
    }

    /**
     * Balancos da CVM (DFP e ITR) de {@code from} (padrao 2010) a {@code to} (padrao: ano corrente).
     * Documento ja gravado na mesma versao e pulado; {@code force=true} reprocessa a partir de
     * carga/data/cvm, sem baixar de novo (regra nova). docs/planos/PLANO_EMPRESAS_BALANCOS.md, F5.
     */
    @PostMapping("/cvm/import")
    public ResponseEntity<Map<String, String>> cvmImport(
            @RequestParam(defaultValue = "" + FinancialStatementImportService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean force) {
        int last = to != null ? to : java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
        if (from < FinancialStatementImportService.FIRST_YEAR || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("balancos CVM", () -> financialStatementImportService.importYears(from, last, force, "sob demanda"));
    }

    /**
     * Recalcula a serie trimestral ({@code company_quarters}) a partir dos balancos ja gravados. A carga
     * de balancos ja faz isso ao terminar; aqui e para depois de corrigir quantidade de acoes a mao. F6.
     */
    @PostMapping("/cvm/quarters")
    public ResponseEntity<Map<String, String>> cvmQuarters() {
        return start("serie trimestral", () -> companyQuarterService.rebuild("sob demanda"));
    }

    /**
     * Informe mensal de FII da CVM em fii_monthly, de {@code from} (padrao 2016) a {@code to} (padrao: ano
     * corrente). Mes ja gravado na mesma versao e pulado; {@code force=true} regrava a partir de carga/data/cvm/fii.
     * docs/planos/PLANO_EMPRESAS_BALANCOS.md, F7.
     */
    @PostMapping("/cvm/fii-monthly")
    public ResponseEntity<Map<String, String>> fiiMonthly(
            @RequestParam(defaultValue = "" + FiiMonthlyImportService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean force) {
        int last = to != null ? to : java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
        if (from < FiiMonthlyImportService.FIRST_YEAR || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("informe FII", () -> fiiMonthlyImportService.importYears(from, last, force, "sob demanda"));
    }

    /**
     * Carga inicial num banco vazio (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 6.3, F11): emissores, COTAHIST, proventos, balancos
     * e informe de FII, em ordem. Retomavel; nunca dispara sozinha ao subir.
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, String>> bootstrap() {
        return start("carga inicial", () -> bootstrapService.run("sob demanda"));
    }

    /**
     * Datas de pagamento pelos relatorios de proventos da CVM (docs/planos/PLANO_CARGA_INICIAL.md, 5.2 — P3):
     * {@code from}/{@code to} em anos, de 2022 em diante.
     */
    @PostMapping("/cvm/proventos")
    public ResponseEntity<Map<String, String>> cvmProventos(
            @RequestParam(defaultValue = "" + CvmPaymentDateService.FIRST_YEAR) int from,
            @RequestParam(required = false) Integer to) {
        int last = to != null ? to : java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
        if (from < CvmPaymentDateService.FIRST_YEAR || last < from) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo invalido: " + from + " a " + last);
        }
        return start("datas de pagamento CVM", () -> cvmPaymentDateService.run(from, last, "sob demanda"));
    }

    /**
     * Datas de pagamento do levantamento ({@code carga/data/levantamento/datas-pagamento.json},
     * docs/planos/PLANO_CARGA_INICIAL.md secao 11): completa so os proventos sem data, sem passar por cima de
     * nenhuma outra fonte. Mesma etapa do bootstrap, para aplicar num banco que ja existe.
     */
    @PostMapping("/proventos/levantamento")
    public ResponseEntity<Map<String, String>> proventosLevantamento() {
        return start("datas de pagamento do levantamento", () -> paymentDateSurveyService.run("sob demanda"));
    }

    /**
     * Indices de mercado (docs/planos/PLANO_INDICES_MERCADO.md, 4.1): {@code codes} separados por virgula (padrao:
     * todos os ativos), de {@code from} (padrao: {@code history_from} de cada indice, 2010) a {@code to} (padrao:
     * ano corrente). Ano fechado com arquivo em carga/data nao vai a fonte; valor igual e pulado, corrigido e
     * sobrescrito com log. {@code recent=true} roda a rodada incremental da rotina diaria (ignora o periodo).
     */
    @PostMapping("/indices")
    public ResponseEntity<Map<String, String>> indices(
            @RequestParam(required = false) List<String> codes,
            @RequestParam(required = false) Integer from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean recent) {
        int last = to != null ? to : java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
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
        if (recent) {
            return start("indices", () -> marketIndexImportService.importRecent("sob demanda"));
        }
        return start("indices", () -> marketIndexImportService.importYears(codes, from, last, "sob demanda"));
    }

    @PostMapping("/proventos")
    public ResponseEntity<Map<String, String>> proventos() {
        return start("proventos", () -> proventosCatalogService.syncAll("sob demanda"));
    }

    private User requireAdmin() {
        User user = userRepository.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador.");
        }
        return user;
    }

    private ResponseEntity<Map<String, String>> start(String name, Runnable routine) {
        requireAdmin();
        Thread.ofVirtual().name("admin-" + name.replace(' ', '-')).start(() -> {
            try {
                routine.run();
            } catch (RuntimeException e) {
                log.error("Rotina '{}' sob demanda falhou: {}", name, e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body(Map.of("status", "iniciada", "rotina", name,
            "resultado", "no log da API"));
    }
}
