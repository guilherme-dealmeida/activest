package com.activest.adm.domain.fixedincome;

import com.activest.adm.integration.bcb.BcbConglomerate;
import com.activest.adm.integration.bcb.BcbInstitution;
import com.activest.adm.integration.bcb.BcbInstitutionsClient;
import com.activest.adm.integration.cvm.CvmCompany;
import com.activest.adm.integration.cvm.CvmDataset;
import com.activest.adm.integration.cvm.CvmIntermediary;
import com.activest.adm.integration.cvm.CvmOpenDataClient;
import com.activest.adm.integration.cvm.CvmSecuritizer;
import com.activest.domain.fixedincome.FixedIncomeGroup;
import com.activest.domain.fixedincome.FixedIncomeGroupRepository;
import com.activest.domain.fixedincome.FixedIncomeIssuer;
import com.activest.domain.fixedincome.FixedIncomeIssuerKind;
import com.activest.domain.fixedincome.FixedIncomeIssuerRepository;
import com.activest.domain.fixedincome.Guarantor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cadastro de emissoras e distribuidoras de renda fixa a partir do BCB e da CVM
 * (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 4).
 *
 * <ol>
 *   <li><b>BCB, IF.data</b>: bancos, financeiras, cooperativas, corretoras e DTVMs (D1 b), vivos e mortos, com
 *   segmento, situacao e conglomerado — o grupo.</li>
 *   <li><b>CVM, intermediarios</b>: CNPJ completo, nome comercial e site de quem ja veio do BCB; cria o
 *   intermediario que so a CVM tem.</li>
 *   <li><b>CVM, companhias abertas</b>: emissoras de debenture e nota comercial.</li>
 *   <li><b>CVM, informes de CRI e CRA</b> (2021+): securitizadoras.</li>
 * </ol>
 *
 * <p>Junta tudo pela raiz do CNPJ (R1). <b>Nunca toca no curado</b> (R9): nome MANUAL do grupo, ligacao MANUAL
 * empresa -> grupo, regra de saida, ticker do logo. Empresa que some da fonte nao e apagada, e a que perde o
 * conglomerado na fonte (a morta, quase sempre) mantem o grupo que tinha (R3). Idempotente.</p>
 */
@Slf4j
@Service
public class FixedIncomeIssuerImportService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Primeiro ano dos informes de CRI/CRA nos dados abertos da CVM. */
    static final int SECURITIZATION_FIRST_YEAR = 2021;

    private final BcbInstitutionsClient bcb;
    private final CvmOpenDataClient cvm;
    private final FixedIncomeIssuerRepository issuers;
    private final FixedIncomeGroupRepository groups;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public FixedIncomeIssuerImportService(BcbInstitutionsClient bcb, CvmOpenDataClient cvm,
                                          FixedIncomeIssuerRepository issuers, FixedIncomeGroupRepository groups,
                                          PlatformTransactionManager txManager) {
        this(bcb, cvm, issuers, groups, txManager, Clock.system(ZONE));
    }

    FixedIncomeIssuerImportService(BcbInstitutionsClient bcb, CvmOpenDataClient cvm,
                                   FixedIncomeIssuerRepository issuers, FixedIncomeGroupRepository groups,
                                   PlatformTransactionManager txManager, Clock clock) {
        this.bcb = bcb;
        this.cvm = cvm;
        this.issuers = issuers;
        this.groups = groups;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public record Result(String baseMonth, int bcbInstitutions, int cvmIntermediaries, int cvmCompanies,
                         int securitizers, int issuersCreated, int issuersUpdated, int groupsCreated,
                         int groupsUpdated) {
    }

    /** Le as quatro fontes e grava numa transacao so. */
    public synchronized Result importAll(String reason) {
        log.info("Emissoras de renda fixa ({}): inicio", reason);
        BcbInstitutionsClient.Registry registry = bcb.latestRegistry();
        Map<String, String> websites = bcb.websites();
        Map<String, CvmIntermediary> intermediaries = CvmIntermediary.parse(cvm.intermediaryRegistry());
        Map<String, CvmCompany> companies = companyRegistry();
        Map<String, String> securitizers = securitizers();
        Result r = tx.execute(s -> persist(registry, websites, intermediaries, companies, securitizers));
        log.info("Emissoras de renda fixa ({}) concluido: {}", reason, r);
        return r;
    }

    private Map<String, CvmCompany> companyRegistry() {
        try (InputStream in = Files.newInputStream(cvm.companyRegistry())) {
            return CvmCompany.parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** CNPJ -> nome das securitizadoras dos informes de CRI e CRA, de 2021 ao ano corrente. */
    private Map<String, String> securitizers() {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> dates = new HashMap<>();
        int year = LocalDate.now(clock).getYear();
        for (int y = SECURITIZATION_FIRST_YEAR; y <= year; y++) {
            final int yy = y;
            cvm.yearly(CvmDataset.SECURIT_CRI, y).ifPresent(z -> CvmSecuritizer.parse(z, "cri", yy, out, dates));
            cvm.yearly(CvmDataset.SECURIT_CRA, y).ifPresent(z -> CvmSecuritizer.parse(z, "cra", yy, out, dates));
        }
        return out;
    }

    Result persist(BcbInstitutionsClient.Registry registry, Map<String, String> websites,
                   Map<String, CvmIntermediary> intermediaries, Map<String, CvmCompany> companies,
                   Map<String, String> securitizers) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, FixedIncomeIssuer> byRoot = new HashMap<>();
        for (FixedIncomeIssuer i : issuers.findAll()) {
            if (i.getCnpjRoot() != null) {
                byRoot.put(i.getCnpjRoot(), i);
            }
        }
        Map<String, FixedIncomeGroup> byCode = new HashMap<>();
        for (FixedIncomeGroup g : groups.findAll()) {
            if (g.getBcbCode() != null) {
                byCode.put(g.getBcbCode(), g);
            }
        }
        Set<FixedIncomeIssuer> created = new LinkedHashSet<>();
        Set<FixedIncomeGroup> createdGroups = new LinkedHashSet<>();
        Set<FixedIncomeGroup> touchedGroups = new LinkedHashSet<>();

        // 1. BCB
        int bcbCount = 0;
        for (BcbInstitution inst : registry.institutions()) {
            FixedIncomeIssuerKind kind = kindOfSegment(inst.segment());
            if (kind == null) {
                continue;
            }
            bcbCount++;
            FixedIncomeGroup group = null;
            if (inst.groupCode() != null) {
                BcbConglomerate c = registry.groups().get(inst.groupCode());
                group = byCode.get(inst.groupCode());
                if (group == null) {
                    group = FixedIncomeGroup.builder().bcbCode(inst.groupCode())
                            .nameSource(FixedIncomeGroup.NameSource.AUTOMATICO).build();
                    byCode.put(inst.groupCode(), group);
                    createdGroups.add(group);
                }
                if (touchedGroups.add(group)) {
                    String raw = c != null && c.name() != null ? c.name() : inst.name();
                    if (group.getNameSource() != FixedIncomeGroup.NameSource.MANUAL || group.getName() == null) {
                        group.setName(GroupNames.automatic(raw));
                    }
                    group.setDescription(raw);
                    group.setLeaderCnpjRoot(c != null && c.leaderRoot() != null ? c.leaderRoot() : inst.leaderRoot());
                    group.setExtinct(false);
                }
            }
            FixedIncomeIssuer issuer = byRoot.computeIfAbsent(inst.cnpjRoot(), root -> {
                FixedIncomeIssuer n = FixedIncomeIssuer.builder().cnpjRoot(root).build();
                created.add(n);
                return n;
            });
            issuer.setKind(kind);
            issuer.setLegalName(inst.name() != null ? inst.name() : inst.cnpjRoot());
            issuer.setSegment(cut(inst.segment(), 120));
            issuer.setStatus(bcbStatus(inst.situation()));
            issuer.setGuarantor(guarantorOf(kind, inst.segment()));
            String site = websites.get(inst.cnpjRoot());
            if (site != null) {
                issuer.setWebsite(cut(site, 255));
            }
            if (group != null && issuer.getGroupSource() != FixedIncomeIssuer.GroupSource.MANUAL) {
                issuer.setGroup(group);
                issuer.setGroupSource(FixedIncomeIssuer.GroupSource.BCB);
            }
            addSource(issuer, "BCB");
            issuer.setSourceUpdatedAt(now);
        }

        // 2. CVM, intermediarios
        int intermedCount = 0;
        for (CvmIntermediary c : intermediaries.values()) {
            String root = c.cnpj().substring(0, 8);
            FixedIncomeIssuer issuer = byRoot.get(root);
            if (issuer == null) {
                FixedIncomeIssuerKind kind = kindOfIntermediary(c);
                if (kind == null) {
                    continue;
                }
                issuer = FixedIncomeIssuer.builder().cnpjRoot(root).kind(kind)
                        .legalName(c.legalName() != null ? c.legalName() : root)
                        .guarantor(guarantorOf(kind, null)).build();
                byRoot.put(root, issuer);
                created.add(issuer);
            }
            intermedCount++;
            if (issuer.getCnpj() == null) {
                issuer.setCnpj(c.cnpj());
            }
            if (c.tradeName() != null) {
                issuer.setTradeName(cut(c.tradeName(), 255));
            }
            if (issuer.getWebsite() == null && c.website() != null) {
                issuer.setWebsite(cut(c.website().toLowerCase(), 255));
            }
            if (!hasSource(issuer, "BCB")) {
                issuer.setStatus(cvmStatus(c.status()));
                issuer.setSegment(cut(String.join(", ", c.types()), 120));
            }
            addSource(issuer, "CVM_INTERMED");
            issuer.setSourceUpdatedAt(now);
        }

        // 3. CVM, companhias abertas
        int ciaCount = 0;
        for (CvmCompany co : companies.values()) {
            String root = co.cnpj().substring(0, 8);
            FixedIncomeIssuer issuer = byRoot.get(root);
            if (issuer == null) {
                issuer = FixedIncomeIssuer.builder().cnpjRoot(root).kind(FixedIncomeIssuerKind.COMPANHIA)
                        .legalName(co.legalName() != null ? co.legalName() : root)
                        .guarantor(Guarantor.NENHUM).build();
                byRoot.put(root, issuer);
                created.add(issuer);
            }
            ciaCount++;
            if (issuer.getCnpj() == null) {
                issuer.setCnpj(co.cnpj());
            }
            if (issuer.getTradeName() == null && co.tradeName() != null) {
                issuer.setTradeName(cut(co.tradeName(), 255));
            }
            if (!hasSource(issuer, "BCB") && !hasSource(issuer, "CVM_INTERMED")) {
                issuer.setLegalName(co.legalName() != null ? co.legalName() : issuer.getLegalName());
                issuer.setStatus(cvmStatus(co.status()));
                issuer.setSegment(cut(co.sector(), 120));
            }
            addSource(issuer, "CVM_CIA");
            issuer.setSourceUpdatedAt(now);
        }

        // 4. CVM, securitizadoras
        int securCount = 0;
        for (Map.Entry<String, String> e : securitizers.entrySet()) {
            String root = e.getKey().substring(0, 8);
            FixedIncomeIssuer issuer = byRoot.get(root);
            if (issuer == null) {
                issuer = FixedIncomeIssuer.builder().cnpjRoot(root).kind(FixedIncomeIssuerKind.SECURITIZADORA)
                        .legalName(cut(e.getValue(), 255)).guarantor(Guarantor.NENHUM).build();
                byRoot.put(root, issuer);
                created.add(issuer);
            } else if (issuer.getKind() == FixedIncomeIssuerKind.COMPANHIA) {
                issuer.setKind(FixedIncomeIssuerKind.SECURITIZADORA);
            }
            securCount++;
            if (issuer.getCnpj() == null) {
                issuer.setCnpj(e.getKey());
            }
            addSource(issuer, "CVM_SECURIT");
            issuer.setSourceUpdatedAt(now);
        }

        groups.saveAll(byCode.values());
        issuers.saveAll(byRoot.values());
        int updatedGroups = touchedGroups.size() - (int) touchedGroups.stream().filter(createdGroups::contains).count();
        return new Result(registry.baseMonth(), bcbCount, intermedCount, ciaCount, securCount, created.size(),
                byRoot.size() - created.size(), createdGroups.size(), updatedGroups);
    }

    // ------------------------------------------------------------------
    // Regras de classificacao (secao 4.3)
    // ------------------------------------------------------------------

    /** Tipo pelo segmento do BCB; nulo nos segmentos que nao emitem nem distribuem renda fixa (D1 b). */
    static FixedIncomeIssuerKind kindOfSegment(String segment) {
        if (segment == null) {
            return null;
        }
        String s = plain(segment);
        if (s.startsWith("COOPERATIVA DE CREDITO")) {
            return FixedIncomeIssuerKind.COOPERATIVA;
        }
        if (s.startsWith("BANCO") || s.startsWith("CAIXA ECONOMICA") || s.equals("BNDES")) {
            return FixedIncomeIssuerKind.BANCO;
        }
        if (s.startsWith("SOCIEDADE DE CREDITO, FINANCIAMENTO") || s.startsWith("SOCIEDADE DE CREDITO IMOBILIARIO")
                || s.startsWith("COMPANHIA HIPOTECARIA") || s.startsWith("ASSOCIACAO DE POUPANCA")) {
            return FixedIncomeIssuerKind.FINANCEIRA;
        }
        if (s.startsWith("SOCIEDADE DISTRIBUIDORA DE TVM") || s.startsWith("SOCIEDADE CORRETORA DE TVM")) {
            return FixedIncomeIssuerKind.CORRETORA_DISTRIBUIDORA;
        }
        return null;
    }

    /** Tipo do intermediario que so a CVM tem. */
    static FixedIncomeIssuerKind kindOfIntermediary(CvmIntermediary c) {
        if (c.hasType("DISTRIBUIDORA") || c.hasType("CORRETORA")) {
            return FixedIncomeIssuerKind.CORRETORA_DISTRIBUIDORA;
        }
        if (c.hasType("COOPERATIVA")) {
            return FixedIncomeIssuerKind.COOPERATIVA;
        }
        if (c.hasType("BANCO") || c.hasType("CAIXA")) {
            return FixedIncomeIssuerKind.BANCO;
        }
        return null;
    }

    /** R11: FGC para bancos e financeiras, FGCoop para cooperativas e bancos cooperativos. */
    static Guarantor guarantorOf(FixedIncomeIssuerKind kind, String segment) {
        if (kind == FixedIncomeIssuerKind.COOPERATIVA) {
            return Guarantor.FGCOOP;
        }
        if (kind == FixedIncomeIssuerKind.BANCO && segment != null && plain(segment).contains("COOPERATIVO")) {
            return Guarantor.FGCOOP;
        }
        if (kind == FixedIncomeIssuerKind.BANCO || kind == FixedIncomeIssuerKind.FINANCEIRA) {
            return Guarantor.FGC;
        }
        return Guarantor.NENHUM;
    }

    static String bcbStatus(String situation) {
        if (situation == null) {
            return null;
        }
        return switch (situation.trim().toUpperCase()) {
            case "A" -> "ATIVA";
            case "I" -> "INATIVA";
            case "LE" -> "LIQUIDACAO_EXTRAJUDICIAL";
            case "LO" -> "LIQUIDACAO_ORDINARIA";
            case "F" -> "FALENCIA";
            default -> cut(situation.trim().toUpperCase(), 30);
        };
    }

    static String cvmStatus(String status) {
        if (status == null) {
            return null;
        }
        String s = plain(status);
        if (s.equals("ATIVO") || s.equals("EM FUNCIONAMENTO NORMAL")) {
            return "ATIVA";
        }
        if (s.startsWith("CANCELAD")) {
            return "CANCELADA";
        }
        if (s.startsWith("LIQUIDACAO EXTRAJUDICIAL")) {
            return "LIQUIDACAO_EXTRAJUDICIAL";
        }
        if (s.startsWith("SUSPENS")) {
            return "SUSPENSA";
        }
        return cut(s.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", ""), 30);
    }

    private static String plain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
    }

    private static boolean hasSource(FixedIncomeIssuer i, String source) {
        return i.getSources() != null && Arrays.asList(i.getSources().split(",")).contains(source);
    }

    private static void addSource(FixedIncomeIssuer i, String source) {
        if (hasSource(i, source)) {
            return;
        }
        i.setSources(i.getSources() == null || i.getSources().isBlank() ? source : i.getSources() + "," + source);
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    /** Contagens do cadastro, para o log e o bootstrap. */
    public Map<String, Long> counts() {
        return issuers.findAll().stream()
                .collect(Collectors.groupingBy(i -> i.getKind().name(), LinkedHashMap::new, Collectors.counting()));
    }

    Optional<FixedIncomeIssuer> byRoot(String root) {
        return issuers.findByCnpjRoot(root);
    }
}
