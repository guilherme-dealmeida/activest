package com.primevest.adm.domain.issuer;

import com.primevest.domain.issuer.Issuer;
import com.primevest.domain.issuer.IssuerKind;
import com.primevest.domain.issuer.IssuerRepository;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3CompanyDetail;
import com.primevest.adm.integration.cvm.CvmCompany;
import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmFca;
import com.primevest.adm.integration.cvm.CvmFiiGeneral;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Importacao dos emissores (PLANO_EMPRESAS_BALANCOS.md, F1).
 *
 * <ul>
 *   <li><b>Companhias</b>: cadastro da CVM + FCA dos tres ultimos anos. Entram as
 *   ativas em bolsa e as que tem ticker com a raiz de alguma acao do catalogo,
 *   mesmo canceladas (decisao D2).</li>
 *   <li><b>FIIs</b>: informe mensal (parte geral) do ano corrente e do anterior;
 *   entram os negociados em bolsa. O segmento curado nao e tocado.</li>
 *   <li><b>Classificacao setorial da B3</b> (setor / subsetor / segmento):
 *   {@code GetDetail} por codigo CVM, so para quem ainda nao tem.</li>
 * </ul>
 *
 * <p>Idempotente: roda de novo e atualiza pelo CNPJ. Os arquivos da CVM ficam
 * em {@code data/cvm} (ver {@link CvmOpenDataClient}).</p>
 */
@Slf4j
@Service
public class IssuerImportService {

    private static final int FCA_YEARS = 3;

    private final CvmOpenDataClient cvm;
    private final B3Client b3Client;
    private final IssuerRepository issuerRepository;
    private final AssetRepository assetRepository;
    private final long pauseMillis;
    private final Clock clock;

    @Autowired
    public IssuerImportService(CvmOpenDataClient cvm, B3Client b3Client, IssuerRepository issuerRepository,
                               AssetRepository assetRepository,
                               @Value("${primevest.issuers.pause-millis:1000}") long pauseMillis) {
        this(cvm, b3Client, issuerRepository, assetRepository, pauseMillis, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    IssuerImportService(CvmOpenDataClient cvm, B3Client b3Client, IssuerRepository issuerRepository,
                        AssetRepository assetRepository, long pauseMillis, Clock clock) {
        this.cvm = cvm;
        this.b3Client = b3Client;
        this.issuerRepository = issuerRepository;
        this.assetRepository = assetRepository;
        this.pauseMillis = pauseMillis;
        this.clock = clock;
    }

    public record Result(int companiesCreated, int companiesUpdated, int fundsCreated, int fundsUpdated,
                         int classified, int classificationFailures) {
    }

    /** Companhias, FIIs e classificacao da B3, nessa ordem. */
    public synchronized Result importAll(String reason) {
        log.info("Emissores ({}): inicio", reason);
        int[] companies = importCompanies();
        int[] funds = importFunds();
        int[] b3 = classifyCompanies();
        Result r = new Result(companies[0], companies[1], funds[0], funds[1], b3[0], b3[1]);
        log.info("Emissores ({}) concluido: {}", reason, r);
        return r;
    }

    /** Cadastro de companhias da CVM, uma entrada por CNPJ. */
    public Map<String, CvmCompany> companyRegistry() {
        try (InputStream in = Files.newInputStream(cvm.companyRegistry())) {
            return CvmCompany.parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Emissor de uma companhia do cadastro da CVM que ficou fora do criterio D2
     * (ex.: Livetech/WDCN3, ativa na CVM mas sem mercado BOLSA no cadastro).
     * Usado quando a B3 ou a curadoria apontam para ela. A proxima importacao
     * completa FCA e classificacao, porque o emissor ja existe.
     */
    @org.springframework.transaction.annotation.Transactional
    public Issuer createCompany(CvmCompany c) {
        Optional<Issuer> existing = issuerRepository.findByCnpj(c.cnpj());
        if (existing.isPresent()) {
            return existing.get();
        }
        Issuer issuer = Issuer.builder().kind(IssuerKind.COMPANY).cnpj(c.cnpj()).cvmCode(c.cvmCode())
                .legalName(c.legalName() != null ? c.legalName() : c.cnpj()).tradeName(c.tradeName())
                .registrationStatus(c.status()).cvmSector(c.sector()).registrationCategory(c.category()).build();
        log.info("Emissores: companhia {} ({}) criada fora do criterio de bolsa", c.legalName(), c.cvmCode());
        return issuerRepository.save(issuer);
    }

    /** @return {criados, atualizados} */
    int[] importCompanies() {
        Map<String, CvmCompany> registry = companyRegistry();

        int year = LocalDate.now(clock).getYear();
        Map<String, CvmFca.General> general = new HashMap<>();
        Map<String, List<CvmFca.Security>> securities = new HashMap<>();
        Map<String, LocalDate> securitiesDate = new HashMap<>();
        for (int y = year; y > year - FCA_YEARS; y--) {
            Optional<Path> zip = cvm.yearly(CvmDataset.FCA, y);
            if (zip.isEmpty()) {
                continue;
            }
            CvmFca.general(zip.get(), y).forEach((cnpj, g) -> general.merge(cnpj, g,
                    (a, b) -> a.referenceDate().isAfter(b.referenceDate()) ? a : b));
            CvmFca.securities(zip.get(), y).forEach((cnpj, list) -> {
                LocalDate ref = list.get(0).referenceDate();
                LocalDate known = securitiesDate.get(cnpj);
                if (known == null || ref.isAfter(known)) {
                    securities.put(cnpj, list);
                    securitiesDate.put(cnpj, ref);
                }
            });
        }

        Set<String> catalogRoots = catalogStockRoots();
        Map<String, Issuer> existing = issuerRepository.findByKind(IssuerKind.COMPANY).stream()
                .collect(Collectors.toMap(Issuer::getCnpj, Function.identity()));
        Set<Integer> takenCodes = issuerRepository.findAll().stream()
                .map(Issuer::getCvmCode).filter(c -> c != null).collect(Collectors.toCollection(HashSet::new));

        List<Issuer> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;
        for (CvmCompany c : registry.values()) {
            List<CvmFca.Security> secs = securities.getOrDefault(c.cnpj(), List.of());
            boolean inCatalog = secs.stream().map(CvmFca.Security::ticker)
                    .anyMatch(t -> t != null && t.length() >= 4 && catalogRoots.contains(t.substring(0, 4)));
            Issuer issuer = existing.get(c.cnpj());
            if (!c.activeOnExchange() && !inCatalog && issuer == null) {
                continue;
            }
            if (issuer == null) {
                if (takenCodes.contains(c.cvmCode())) {
                    log.warn("Emissores: codigo CVM {} ja usado por outro CNPJ; {} ignorado", c.cvmCode(), c.cnpj());
                    continue;
                }
                issuer = Issuer.builder().kind(IssuerKind.COMPANY).cnpj(c.cnpj()).build();
                takenCodes.add(c.cvmCode());
                created++;
            } else {
                updated++;
            }
            issuer.setCvmCode(c.cvmCode());
            issuer.setLegalName(c.legalName() != null ? c.legalName() : c.cnpj());
            issuer.setTradeName(c.tradeName());
            issuer.setRegistrationStatus(c.status());
            issuer.setCvmSector(c.sector());
            issuer.setRegistrationCategory(c.category());
            CvmFca.General g = general.get(c.cnpj());
            if (g != null) {
                issuer.setFiscalYearEndMonth(g.fiscalYearEndMonth());
                issuer.setWebsite(g.website());
                issuer.setSourceUpdatedAt(g.referenceDate().atStartOfDay());
            }
            secs.stream().filter(CvmFca.Security::onExchange).map(CvmFca.Security::listingSegment)
                    .filter(s -> s != null && !s.isBlank()).findFirst().ifPresent(issuer::setListingSegment);
            toSave.add(issuer);
        }
        issuerRepository.saveAll(toSave);
        log.info("Emissores: companhias {} novas, {} atualizadas (cadastro com {} CNPJs)", created, updated,
                registry.size());
        return new int[]{created, updated};
    }

    /** @return {criados, atualizados} */
    int[] importFunds() {
        Map<String, CvmFiiGeneral> latest = latestFundReports();
        Map<String, Issuer> existing = issuerRepository.findAll().stream()
                .collect(Collectors.toMap(Issuer::getCnpj, Function.identity()));

        List<Issuer> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;
        for (CvmFiiGeneral f : latest.values()) {
            Issuer issuer = existing.get(f.cnpj());
            if (issuer != null && issuer.getKind() != IssuerKind.FII) {
                log.warn("Emissores: CNPJ {} do informe de FII ja e companhia; ignorado", f.cnpj());
                continue;
            }
            if (issuer == null && !f.onExchange()) {
                continue;
            }
            if (issuer == null) {
                issuer = Issuer.builder().kind(IssuerKind.FII).cnpj(f.cnpj()).build();
                created++;
            } else {
                updated++;
            }
            applyFund(issuer, f);
            toSave.add(issuer);
        }
        issuerRepository.saveAll(toSave);
        log.info("Emissores: FIIs {} novos, {} atualizados (informe com {} fundos)", created, updated, latest.size());
        return new int[]{created, updated};
    }

    /** Dados do informe no emissor FII. O segmento curado ({@code segment}) nunca e tocado. */
    static void applyFund(Issuer issuer, CvmFiiGeneral f) {
        issuer.setLegalName(f.name() != null ? f.name() : f.cnpj());
        issuer.setRegistrationStatus(f.onExchange() ? "ATIVO" : "FORA_DE_BOLSA");
        issuer.setCvmSegment(f.cvmSegment());
        issuer.setManagementType(f.managementType());
        issuer.setAdministratorName(f.administratorName());
        issuer.setAdministratorCnpj(f.administratorCnpj());
        issuer.setWebsite(f.website());
        issuer.setSourceUpdatedAt(f.referenceDate().atStartOfDay());
    }

    /**
     * Informe mais recente de cada FII (ano corrente e anterior), inclusive os que
     * a CVM marca como fora de bolsa — a marca nao e confiavel (F3, 27/09/2026).
     */
    public Map<String, CvmFiiGeneral> latestFundReports() {
        int year = LocalDate.now(clock).getYear();
        Map<String, CvmFiiGeneral> latest = new HashMap<>();
        for (int y = year - 1; y <= year; y++) {
            int yy = y;
            cvm.yearly(CvmDataset.FII_MONTHLY, y).ifPresent(zip -> CvmFiiGeneral.latest(zip, yy)
                    .forEach((cnpj, g) -> latest.merge(cnpj, g,
                            (a, b) -> a.referenceDate().isAfter(b.referenceDate()) ? a : b)));
        }
        return latest;
    }

    /**
     * Setor, subsetor e segmento da B3 para as companhias sem classificacao.
     *
     * @return {classificadas, falhas}
     */
    int[] classifyCompanies() {
        List<Issuer> pending = issuerRepository.findByKind(IssuerKind.COMPANY).stream()
                .filter(i -> i.getB3Sector() == null && i.getCvmCode() != null)
                .filter(i -> "ATIVO".equalsIgnoreCase(i.getRegistrationStatus()))
                .toList();
        int ok = 0;
        int failed = 0;
        for (Issuer issuer : pending) {
            try {
                B3CompanyDetail detail = b3Client.companyDetail(String.valueOf(issuer.getCvmCode()));
                List<String> parts = detail == null ? List.of() : detail.classification();
                if (parts.size() == 3) {
                    issuer.setB3Sector(parts.get(0));
                    issuer.setB3Subsector(parts.get(1));
                    issuer.setB3Segment(parts.get(2));
                    issuerRepository.save(issuer);
                    ok++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.debug("Emissores: sem classificacao da B3 para {} ({}): {}", issuer.getLegalName(),
                        issuer.getCvmCode(), e.getMessage());
            }
            pause();
        }
        log.info("Emissores: classificacao B3 em {} de {} companhias ({} sem resposta)", ok, pending.size(), failed);
        return new int[]{ok, failed};
    }

    private Set<String> catalogStockRoots() {
        Set<String> roots = new HashSet<>();
        for (Asset a : assetRepository.findAllWithType()) {
            if ("ACAO".equals(a.getAssetType().getName()) && a.getTicker().length() >= 4) {
                roots.add(a.getTicker().substring(0, 4).toUpperCase());
            }
        }
        return roots;
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
