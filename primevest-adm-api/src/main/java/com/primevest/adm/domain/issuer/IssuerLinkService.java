package com.primevest.adm.domain.issuer;

import com.primevest.domain.issuer.Issuer;
import com.primevest.domain.issuer.IssuerKind;
import com.primevest.domain.issuer.IssuerLinkSource;
import com.primevest.domain.issuer.IssuerRepository;
import com.primevest.domain.issuer.ShareClass;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3Supplement;
import com.primevest.adm.integration.cvm.CvmCompany;
import com.primevest.adm.integration.cvm.CvmCsv;
import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmFca;
import com.primevest.adm.integration.cvm.CvmFiiGeneral;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ligacao ativo -> emissor (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F3).
 *
 * <p><b>Acoes</b>, em ordem, parando no primeiro que resolver:</p>
 * <ol>
 *   <li>FCA ({@code valor_mobiliario}, tres ultimos anos): ticker -> CNPJ. Classe
 *   pelo tipo do valor mobiliario (e a sigla PNA/PNB... da preferencial).</li>
 *   <li>Raiz do ticker: KLBN11 ligado pelo FCA leva KLBN3 e KLBN4 junto.</li>
 *   <li>B3 ({@code GetListedSupplementCompany} pela raiz) -> codigo CVM.</li>
 * </ol>
 * <p>O fracionario (PETR4F) segue o ticker sem o F. A unit ganha a composicao do
 * texto do FCA ({@link UnitCompositionText}).</p>
 *
 * <p><b>FIIs</b>: ISIN do catalogo (COTAHIST) -> informe mensal da CVM -> CNPJ.
 * Fundo marcado pela CVM como fora de bolsa tambem entra: a marca nao e
 * confiavel. A lista curada de CNPJs ({@link #applyCuratedFunds}) corrige o que o
 * ISIN nao resolve ou resolve errado (APTO11 e XPML11 no informe de 2026).</p>
 *
 * <p>Ligacao {@code MANUAL} nunca e sobrescrita. Sem resposta para um ativo, a
 * ligacao que ele ja tinha fica. Gravacao por SQL direto nas tres colunas da
 * ligacao — nao regrava o ativo inteiro, que o fechamento atualiza em paralelo.</p>
 */
@Slf4j
@Service
public class IssuerLinkService {

    private static final int FCA_YEARS = 3;

    private final CvmOpenDataClient cvm;
    private final B3Client b3Client;
    private final IssuerRepository issuerRepository;
    private final AssetRepository assetRepository;
    private final IssuerImportService importer;
    private final JdbcTemplate jdbc;
    private final long pauseMillis;
    private final Clock clock;

    @Autowired
    public IssuerLinkService(CvmOpenDataClient cvm, B3Client b3Client, IssuerRepository issuerRepository,
                             AssetRepository assetRepository, IssuerImportService importer, JdbcTemplate jdbc,
                             @Value("${primevest.issuers.pause-millis:1000}") long pauseMillis) {
        this.cvm = cvm;
        this.b3Client = b3Client;
        this.issuerRepository = issuerRepository;
        this.assetRepository = assetRepository;
        this.importer = importer;
        this.jdbc = jdbc;
        this.pauseMillis = pauseMillis;
        this.clock = Clock.system(ZoneId.of("America/Sao_Paulo"));
    }

    /** Resultado da rodada: quantos por origem e o que ficou para a conferencia. */
    public record Result(Map<IssuerLinkSource, Integer> stocksBySource, int fundsLinked, int fundsCreated,
                         int unitsComposed, List<String> stocksWithoutIssuer, List<String> fundsWithoutIssuer,
                         List<String> unitsWithoutComposition) {
    }

    /** Uma linha da lista curada de FIIs. */
    public record FundCnpj(String ticker, String cnpj) {
    }

    /**
     * O que aconteceu com cada linha da lista curada: CONFIRMADO (ja estava assim),
     * LIGADO (estava sem emissor), LIGACAO_CORRIGIDA (estava em outro emissor),
     * EMISSOR_CRIADO[_SEM_INFORME], TICKER_FORA_DO_CATALOGO, CNPJ_INVALIDO,
     * CNPJ_E_DE_COMPANHIA.
     */
    public record CuratedLine(String ticker, String cnpj, String status, String issuer) {
    }

    private record Link(Issuer issuer, ShareClass shareClass, IssuerLinkSource source, String unitText) {
    }

    public synchronized Result linkAll(String reason) {
        log.info("Ligacao ativo -> emissor ({}): inicio", reason);
        Map<IssuerLinkSource, Integer> bySource = new EnumMap<>(IssuerLinkSource.class);
        List<String> stocksMissing = new ArrayList<>();
        List<String> unitsMissing = new ArrayList<>();
        int units = linkStocks(bySource, stocksMissing, unitsMissing);
        List<String> fundsMissing = new ArrayList<>();
        int[] funds = linkFunds(fundsMissing);
        Result r = new Result(bySource, funds[0], funds[1], units, stocksMissing, fundsMissing, unitsMissing);
        log.info("Ligacao ({}) concluida: acoes {}, FIIs {} ({} emissores criados), units com composicao {}; "
                        + "sem emissor: {} acoes {}, {} FIIs {}; units sem composicao: {}",
                reason, bySource, funds[0], funds[1], units, stocksMissing.size(), stocksMissing,
                fundsMissing.size(), fundsMissing, unitsMissing);
        return r;
    }

    // ------------------------------------------------------------------
    // Acoes
    // ------------------------------------------------------------------

    int linkStocks(Map<IssuerLinkSource, Integer> bySource, List<String> missing, List<String> unitsMissing) {
        List<Asset> stocks = assetRepository.findAllWithType().stream()
                .filter(a -> "ACAO".equals(a.getAssetType().getName())).toList();
        Map<String, Issuer> byCnpj = issuerRepository.findByKind(IssuerKind.COMPANY).stream()
                .collect(Collectors.toMap(Issuer::getCnpj, Function.identity()));
        Map<Integer, Issuer> byCode = byCnpj.values().stream().filter(i -> i.getCvmCode() != null)
                .collect(Collectors.toMap(Issuer::getCvmCode, Function.identity()));
        Map<String, CvmFca.Security> fca = fcaByTicker();

        Map<String, List<Asset>> byBase = new TreeMap<>();
        for (Asset a : stocks) {
            byBase.computeIfAbsent(base(a.getTicker()), k -> new ArrayList<>()).add(a);
        }

        // 1. FCA
        Map<String, Link> links = new HashMap<>();
        for (String base : byBase.keySet()) {
            CvmFca.Security sec = fca.get(base);
            Issuer issuer = sec == null ? null : byCnpj.get(sec.cnpj());
            if (issuer != null) {
                links.put(base, new Link(issuer, classOf(sec, base), IssuerLinkSource.FCA, sec.unitComposition()));
            }
        }
        // 2. Raiz do ticker
        Map<String, Issuer> byRoot = new HashMap<>();
        links.forEach((base, link) -> {
            Issuer known = byRoot.putIfAbsent(root(base), link.issuer());
            if (known != null && !known.getId().equals(link.issuer().getId())) {
                log.warn("Ligacao: raiz {} aparece em dois emissores ({} e {})", root(base),
                        known.getLegalName(), link.issuer().getLegalName());
            }
        });
        for (String base : byBase.keySet()) {
            Issuer issuer = links.containsKey(base) ? null : byRoot.get(root(base));
            if (issuer != null) {
                links.put(base, new Link(issuer, ShareClass.fromTicker(base), IssuerLinkSource.PREFIXO, null));
            }
        }
        // 3. B3 pela raiz. Codigo CVM que ainda nao e emissor, mas esta no cadastro
        // da CVM, vira emissor aqui (companhia fora do criterio D2, ex.: WDCN3).
        Map<Integer, CvmCompany> registryByCode = new HashMap<>();
        TreeSet<String> pendingRoots = byBase.keySet().stream().filter(b -> !links.containsKey(b))
                .map(IssuerLinkService::root).collect(Collectors.toCollection(TreeSet::new));
        for (String root : pendingRoots) {
            Issuer issuer = null;
            try {
                B3Supplement sup = b3Client.companySupplement(root);
                if (sup != null && sup.codeCVM() != null && !sup.codeCVM().isBlank()) {
                    Integer code = Integer.valueOf(sup.codeCVM().trim());
                    issuer = byCode.get(code);
                    if (issuer == null) {
                        if (registryByCode.isEmpty()) {
                            importer.companyRegistry().values().forEach(c -> registryByCode.put(c.cvmCode(), c));
                        }
                        CvmCompany c = registryByCode.get(code);
                        if (c != null) {
                            issuer = importer.createCompany(c);
                            byCode.put(code, issuer);
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.debug("Ligacao: B3 sem empresa para a raiz {}: {}", root, e.getMessage());
            }
            pause();
            if (issuer == null) {
                continue;
            }
            for (String base : byBase.keySet()) {
                if (!links.containsKey(base) && root(base).equals(root)) {
                    links.put(base, new Link(issuer, ShareClass.fromTicker(base), IssuerLinkSource.B3, null));
                }
            }
        }

        // Gravacao
        List<Object[]> updates = new ArrayList<>();
        int composed = 0;
        for (Map.Entry<String, List<Asset>> e : byBase.entrySet()) {
            Link link = links.get(e.getKey());
            for (Asset a : e.getValue()) {
                if (a.getIssuerLinkSource() == IssuerLinkSource.MANUAL) {
                    continue;
                }
                if (link == null) {
                    if (a.getIssuer() == null) {
                        missing.add(a.getTicker() + (a.isActive() ? "" : " (inativo)"));
                    }
                    continue;
                }
                updates.add(new Object[]{link.issuer().getId(), link.shareClass() == null ? null : link.shareClass().name(),
                        link.source().name(), a.getId()});
                bySource.merge(link.source(), 1, Integer::sum);
                if (link.shareClass() == ShareClass.UNIT) {
                    Map<ShareClass, Integer> comp = UnitCompositionText.parse(link.unitText());
                    if (comp.isEmpty()) {
                        if (!hasComposition(a.getId())) {
                            unitsMissing.add(a.getTicker());
                        }
                    } else {
                        saveComposition(a.getId(), comp);
                        composed++;
                    }
                }
            }
        }
        jdbc.batchUpdate("""
                UPDATE assets SET issuer_id = ?, share_class = ?, issuer_link_source = ?
                WHERE id = ? AND issuer_link_source IS DISTINCT FROM 'MANUAL'""", updates);
        return composed;
    }

    private Map<String, CvmFca.Security> fcaByTicker() {
        int year = LocalDate.now(clock).getYear();
        Map<String, CvmFca.Security> out = new HashMap<>();
        for (int y = year; y > year - FCA_YEARS; y--) {
            Optional<java.nio.file.Path> zip = cvm.yearly(CvmDataset.FCA, y);
            if (zip.isEmpty()) {
                continue;
            }
            for (List<CvmFca.Security> list : CvmFca.securities(zip.get(), y).values()) {
                for (CvmFca.Security s : list) {
                    if (s.ticker() == null || s.ticker().isBlank()) {
                        continue;
                    }
                    out.merge(s.ticker(), s, (a, b) -> b.referenceDate().isAfter(a.referenceDate()) ? b : a);
                }
            }
        }
        return out;
    }

    static ShareClass classOf(CvmFca.Security sec, String ticker) {
        String type = sec.type() == null ? "" : sec.type().toLowerCase();
        if (type.startsWith("unit")) {
            return ShareClass.UNIT;
        }
        if (type.contains("ordin")) {
            return ShareClass.ON;
        }
        if (type.contains("prefer")) {
            String acronym = sec.preferredClass();
            if (acronym != null) {
                switch (acronym) {
                    case "PNA": return ShareClass.PNA;
                    case "PNB": return ShareClass.PNB;
                    case "PNC": return ShareClass.PNC;
                    case "PND": return ShareClass.PND;
                    default: break;
                }
            }
            ShareClass byTicker = ShareClass.fromTicker(ticker);
            return byTicker != null && byTicker != ShareClass.ON && byTicker != ShareClass.UNIT ? byTicker : ShareClass.PN;
        }
        return ShareClass.fromTicker(ticker);
    }

    private boolean hasComposition(Long assetId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM unit_compositions WHERE asset_id = ?", Integer.class, assetId);
        return n != null && n > 0;
    }

    private void saveComposition(Long assetId, Map<ShareClass, Integer> comp) {
        jdbc.update("DELETE FROM unit_compositions WHERE asset_id = ?", assetId);
        comp.forEach((c, q) -> jdbc.update(
                "INSERT INTO unit_compositions (asset_id, share_class, quantity) VALUES (?, ?, ?)", assetId, c.name(), q));
    }

    // ------------------------------------------------------------------
    // FIIs
    // ------------------------------------------------------------------

    /** @return {ligados, emissores criados} */
    int[] linkFunds(List<String> missing) {
        Map<String, CvmFiiGeneral> reports = importer.latestFundReports();
        Map<String, CvmFiiGeneral> byIsin = new HashMap<>();
        reports.values().forEach(r -> {
            if (r.isin() != null && !r.isin().isBlank()) {
                byIsin.merge(r.isin().trim().toUpperCase(), r,
                        (a, b) -> b.referenceDate().isAfter(a.referenceDate()) ? b : a);
            }
        });
        List<Asset> funds = assetRepository.findAllWithType().stream()
                .filter(a -> "FII".equals(a.getAssetType().getName())).toList();
        Map<String, String> isinByBase = new HashMap<>();
        funds.forEach(a -> {
            if (a.getIsin() != null && !a.getTicker().endsWith("F")) {
                isinByBase.put(a.getTicker(), a.getIsin().trim().toUpperCase());
            }
        });
        Map<String, Issuer> byCnpj = new HashMap<>(issuerRepository.findAll().stream()
                .collect(Collectors.toMap(Issuer::getCnpj, Function.identity())));

        List<Object[]> updates = new ArrayList<>();
        int created = 0;
        for (Asset a : funds) {
            if (a.getIssuerLinkSource() == IssuerLinkSource.MANUAL) {
                continue;
            }
            String isin = a.getIsin() != null ? a.getIsin().trim().toUpperCase() : isinByBase.get(base(a.getTicker()));
            CvmFiiGeneral report = isin == null ? null : byIsin.get(isin);
            if (report == null) {
                if (a.getIssuer() == null) {
                    missing.add(a.getTicker() + (a.isActive() ? "" : " (inativo)"));
                }
                continue;
            }
            Issuer issuer = byCnpj.get(report.cnpj());
            if (issuer != null && issuer.getKind() != IssuerKind.FII) {
                log.warn("Ligacao: {} aponta para o CNPJ {}, que e companhia", a.getTicker(), report.cnpj());
                continue;
            }
            if (issuer == null) {
                issuer = Issuer.builder().kind(IssuerKind.FII).cnpj(report.cnpj()).build();
                IssuerImportService.applyFund(issuer, report);
                issuer = issuerRepository.save(issuer);
                byCnpj.put(issuer.getCnpj(), issuer);
                created++;
            }
            updates.add(new Object[]{issuer.getId(), IssuerLinkSource.ISIN.name(), a.getId()});
        }
        jdbc.batchUpdate("""
                UPDATE assets SET issuer_id = ?, share_class = NULL, issuer_link_source = ?
                WHERE id = ? AND issuer_link_source IS DISTINCT FROM 'MANUAL'""", updates);
        return new int[]{updates.size(), created};
    }

    /**
     * Lista curada de CNPJs de FII (planilha conferida, docs/planos/PLANO_EMPRESAS_BALANCOS.md
     * 1.6): liga como {@code MANUAL}, o fracionario junto, criando o emissor quando
     * falta — com os dados do informe, se o CNPJ estiver la, ou so com o nome do ativo.
     */
    @Transactional
    public List<CuratedLine> applyCuratedFunds(List<FundCnpj> lines) {
        Map<String, CvmFiiGeneral> reports = importer.latestFundReports();
        Map<String, Asset> byTicker = assetRepository.findAllWithType().stream()
                .filter(a -> "FII".equals(a.getAssetType().getName()))
                .collect(Collectors.toMap(a -> a.getTicker().toUpperCase(), Function.identity()));
        List<CuratedLine> out = new ArrayList<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (FundCnpj line : lines) {
            String ticker = line.ticker() == null ? "" : line.ticker().trim().toUpperCase();
            String cnpj = CvmCsv.cnpj(line.cnpj());
            Asset asset = byTicker.get(ticker);
            String status;
            Issuer issuer = null;
            if (asset == null) {
                status = "TICKER_FORA_DO_CATALOGO";
            } else if (cnpj == null) {
                status = "CNPJ_INVALIDO";
            } else {
                issuer = issuerRepository.findByCnpj(cnpj).orElse(null);
                if (issuer != null && issuer.getKind() != IssuerKind.FII) {
                    status = "CNPJ_E_DE_COMPANHIA";
                    issuer = null;
                } else {
                    boolean created = issuer == null;
                    if (created) {
                        issuer = Issuer.builder().kind(IssuerKind.FII).cnpj(cnpj).build();
                        CvmFiiGeneral report = reports.get(cnpj);
                        if (report != null) {
                            IssuerImportService.applyFund(issuer, report);
                        } else {
                            issuer.setLegalName(asset.getName());
                        }
                        issuer = issuerRepository.save(issuer);
                    }
                    boolean changed = asset.getIssuer() == null || !issuer.getId().equals(asset.getIssuer().getId());
                    for (String t : List.of(ticker, ticker + "F")) {
                        if (byTicker.containsKey(t)) {
                            jdbc.update("UPDATE assets SET issuer_id = ?, share_class = NULL, issuer_link_source = 'MANUAL' "
                                    + "WHERE id = ?", issuer.getId(), byTicker.get(t).getId());
                        }
                    }
                    status = created ? (reports.containsKey(cnpj) ? "EMISSOR_CRIADO" : "EMISSOR_CRIADO_SEM_INFORME")
                            : asset.getIssuer() == null ? "LIGADO"
                            : changed ? "LIGACAO_CORRIGIDA" : "CONFIRMADO";
                }
            }
            totals.merge(status, 1, Integer::sum);
            out.add(new CuratedLine(ticker, cnpj, status, issuer == null ? null : issuer.getLegalName()));
        }
        log.info("Lista curada de FIIs: {}", totals);
        return out;
    }

    /**
     * Ligacao manual de um ativo (curadoria), pelo id do emissor ou pelo CNPJ.
     * CNPJ de companhia que ainda nao e emissor e procurado no cadastro da CVM e
     * vira emissor. Sem id e sem CNPJ, desfaz a ligacao. A classe informada vale
     * como esta — nula para o que nao e acao (ex.: bonus de subscricao).
     */
    @Transactional
    public void setManual(Long assetId, Long issuerId, String cnpj, ShareClass shareClass) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Ativo " + assetId + " nao existe"));
        if (issuerId == null && (cnpj == null || cnpj.isBlank())) {
            jdbc.update("UPDATE assets SET issuer_id = NULL, share_class = NULL, issuer_link_source = NULL WHERE id = ?",
                    asset.getId());
            jdbc.update("DELETE FROM unit_compositions WHERE asset_id = ?", asset.getId());
            return;
        }
        Issuer issuer;
        if (issuerId != null) {
            issuer = issuerRepository.findById(issuerId)
                    .orElseThrow(() -> new IllegalArgumentException("Emissor " + issuerId + " nao existe"));
        } else {
            String digits = CvmCsv.cnpj(cnpj);
            issuer = digits == null ? null : issuerRepository.findByCnpj(digits).orElseGet(() -> {
                CvmCompany c = importer.companyRegistry().get(digits);
                return c == null ? null : importer.createCompany(c);
            });
            if (issuer == null) {
                throw new IllegalArgumentException("CNPJ " + cnpj + " nao e emissor nem esta no cadastro da CVM");
            }
        }
        if (shareClass != ShareClass.UNIT) {
            jdbc.update("DELETE FROM unit_compositions WHERE asset_id = ?", asset.getId());
        }
        jdbc.update("UPDATE assets SET issuer_id = ?, share_class = ?, issuer_link_source = 'MANUAL' WHERE id = ?",
                issuer.getId(), shareClass == null ? null : shareClass.name(), asset.getId());
    }

    /**
     * Composicao manual de uma unit (curadoria): substitui a que houver e marca a
     * ligacao como MANUAL, para a rotina nao reescrever com o texto do FCA.
     */
    @Transactional
    public void setUnitComposition(Long assetId, Map<ShareClass, Integer> composition) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Ativo " + assetId + " nao existe"));
        if (asset.getShareClass() != ShareClass.UNIT || asset.getIssuer() == null) {
            throw new IllegalStateException(asset.getTicker() + " nao esta ligado como UNIT");
        }
        if (composition == null || composition.isEmpty() || composition.containsKey(ShareClass.UNIT)
                || composition.values().stream().anyMatch(q -> q == null || q <= 0)) {
            throw new IllegalStateException("Composicao invalida: " + composition);
        }
        saveComposition(asset.getId(), composition);
        jdbc.update("UPDATE assets SET issuer_link_source = 'MANUAL' WHERE id = ?", asset.getId());
    }

    // ------------------------------------------------------------------

    /** Ticker sem o F do fracionario. */
    static String base(String ticker) {
        String t = ticker.trim().toUpperCase();
        return t.length() > 5 && t.endsWith("F") ? t.substring(0, t.length() - 1) : t;
    }

    static String root(String ticker) {
        return ticker.length() >= 4 ? ticker.substring(0, 4) : ticker;
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
}
