package com.activest.adm.domain.fund;

import com.activest.domain.fund.FundKeys;
import com.activest.domain.fund.InvestmentFund;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Os cadastros de fundos da CVM lidos para o catálogo (PLANO_FUNDOS_INVESTIMENTO.md, F2, C2 e D10):
 *
 * <ul>
 *   <li>{@code registro_fundo_classe.zip} (RCVM 175): fundos, classes FIF e subclasses;</li>
 *   <li>{@code cad_fi_hist.zip}: denominações sociais e comerciais, atuais e anteriores;</li>
 *   <li>{@code cad_fi.csv} (formato antigo): classificação dos fundos encerrados antes da RCVM 175.</li>
 * </ul>
 *
 * <p>O universo do catálogo é: toda classe FIF e subclasse do registro, mais toda chave que já informou cota no informe
 * diário desde 2010 ({@code reportedKeys}) — o informe só tem fundos FIF/FI, então FIDC, FIP, FII e FIAGRO ficam fora
 * sem lista de tipos (D1). Regra sem banco: o serviço grava.</p>
 */
final class FundRegistry {

    /** Uma denominação (atual ou anterior) para a busca. */
    record Name(String name, boolean trade, LocalDate from, LocalDate to) {
    }

    private final Map<String, FundRecord> records = new LinkedHashMap<>();
    private final Map<String, List<Name>> namesByCnpj = new HashMap<>();
    private final Map<String, String> currentTradeByCnpj = new HashMap<>();

    Map<String, FundRecord> records() {
        return records;
    }

    /** Nomes atuais e anteriores do CNPJ (cad_fi_hist). */
    List<Name> names(String cnpj) {
        return namesByCnpj.getOrDefault(cnpj, List.of());
    }

    /**
     * @param registryZip    {@code registro_fundo_classe.zip}
     * @param historyZip     {@code cad_fi_hist.zip} (pode faltar)
     * @param oldRegistryCsv {@code cad_fi.csv} (pode faltar)
     * @param reportedKeys   chaves que informaram cota desde 2010
     */
    static FundRegistry load(Path registryZip, Path historyZip, Path oldRegistryCsv, Set<String> reportedKeys) {
        FundRegistry r = new FundRegistry();
        if (historyZip != null && Files.exists(historyZip)) {
            r.loadHistory(historyZip);
        }
        Map<String, SemicolonCsv.Row> fundById = new HashMap<>();
        Map<String, SemicolonCsv.Row> fundByCnpj = new HashMap<>();
        Map<String, SemicolonCsv.Row> classById = new HashMap<>();
        Map<String, SemicolonCsv.Row> classByCnpj = new HashMap<>();
        List<SemicolonCsv.Row> subclasses = new ArrayList<>();
        SemicolonCsv.readZip(registryZip, Map.of(
                "registro_fundo.csv", row -> {
                    fundById.putIfAbsent(row.get("ID_Registro_Fundo"), row);
                    String cnpj = FundKeys.cnpj(row.get("CNPJ_Fundo"));
                    if (cnpj != null) {
                        fundByCnpj.merge(cnpj, row, FundRegistry::betterFund);
                    }
                },
                "registro_classe.csv", row -> {
                    String type = row.get("Tipo_Classe");
                    if (type == null || !type.contains("FIF")) {
                        return;
                    }
                    classById.put(row.get("ID_Registro_Classe"), row);
                    String cnpj = FundKeys.cnpj(row.get("CNPJ_Classe"));
                    if (cnpj != null) {
                        classByCnpj.merge(cnpj, row, FundRegistry::better);
                    }
                },
                "registro_subclasse.csv", subclasses::add));
        Map<String, SemicolonCsv.Row> oldByCnpj = new HashMap<>();
        if (oldRegistryCsv != null && Files.exists(oldRegistryCsv)) {
            SemicolonCsv.read(oldRegistryCsv, row -> {
                String cnpj = FundKeys.cnpj(row.get("CNPJ_FUNDO"));
                if (cnpj != null) {
                    oldByCnpj.merge(cnpj, row, FundRegistry::betterOld);
                }
            });
        }

        // classes FIF
        for (Map.Entry<String, SemicolonCsv.Row> e : classByCnpj.entrySet()) {
            r.put(r.fromClass(e.getKey(), e.getValue(), fundById.get(e.getValue().get("ID_Registro_Fundo"))));
        }
        // subclasses das classes FIF
        for (SemicolonCsv.Row sub : subclasses) {
            SemicolonCsv.Row cls = classById.get(sub.get("ID_Registro_Classe"));
            String subId = sub.get("ID_Subclasse");
            if (cls == null || subId == null) {
                continue;
            }
            String cnpj = FundKeys.cnpj(cls.get("CNPJ_Classe"));
            if (cnpj == null) {
                continue;
            }
            r.put(r.fromSubclass(cnpj, subId, sub, cls, fundById.get(cls.get("ID_Registro_Fundo"))));
        }
        // chaves do informe fora do registro de classes (formato antigo, e as 271 de 10.3)
        for (String key : reportedKeys) {
            if (r.records.containsKey(key)) {
                continue;
            }
            int dash = key.indexOf('-');
            String cnpj = dash < 0 ? key : key.substring(0, dash);
            String subId = dash < 0 ? null : key.substring(dash + 1);
            r.put(r.fromFund(key, cnpj, subId, fundByCnpj.get(cnpj), oldByCnpj.get(cnpj)));
        }
        return r;
    }

    private void put(FundRecord rec) {
        records.put(rec.key(), rec);
    }

    private FundRecord fromClass(String cnpj, SemicolonCsv.Row c, SemicolonCsv.Row fund) {
        return new FundRecord(cnpj, cnpj, null, null, c.get("Denominacao_Social"), currentTradeByCnpj.get(cnpj),
                kind(c.get("Tipo_Classe"), fund), c.get("Classificacao"), c.get("Classificacao_Anbima"),
                c.get("Publico_Alvo"), yes(c.get("Exclusivo")), open(c.get("Forma_Condominio")),
                yes(c.get("Tributacao_Longo_Prazo")), fund == null ? null : fund.get("Gestor"),
                fund == null ? null : FundKeys.cnpj(fund.get("CPF_CNPJ_Gestor")),
                fund == null ? null : fund.get("Administrador"),
                fund == null ? null : FundKeys.cnpj(fund.get("CNPJ_Administrador")), c.get("Situacao"),
                date(c.get("Data_Inicio_Situacao")), date(first(c.get("Data_Inicio"), c.get("Data_Constituicao"))),
                decimal(c.get("Patrimonio_Liquido")), date(c.get("Data_Patrimonio_Liquido")));
    }

    private FundRecord fromSubclass(String cnpj, String subId, SemicolonCsv.Row s, SemicolonCsv.Row c,
                                    SemicolonCsv.Row fund) {
        String className = c.get("Denominacao_Social");
        String subName = s.get("Denominacao_Social");
        String legal;
        if (subName == null) {
            legal = className + " - " + subId;
        } else if (FundKeys.normalize(subName).startsWith("SUBCLASSE") && className != null) {
            legal = className + " - " + subName;
        } else {
            legal = subName;
        }
        return new FundRecord(InvestmentFund.ticker(cnpj, subId), cnpj, subId, cnpj, legal, null,
                kind(c.get("Tipo_Classe"), fund), c.get("Classificacao"), c.get("Classificacao_Anbima"),
                first(s.get("Publico_Alvo"), c.get("Publico_Alvo")), yes(first(s.get("Exclusivo"), c.get("Exclusivo"))),
                open(first(s.get("Forma_Condominio"), c.get("Forma_Condominio"))), yes(c.get("Tributacao_Longo_Prazo")),
                fund == null ? null : fund.get("Gestor"), fund == null ? null : FundKeys.cnpj(fund.get("CPF_CNPJ_Gestor")),
                fund == null ? null : fund.get("Administrador"),
                fund == null ? null : FundKeys.cnpj(fund.get("CNPJ_Administrador")), s.get("Situacao"),
                date(s.get("Data_Inicio_Situacao")), date(first(s.get("Data_Inicio"), s.get("Data_Constituicao"))),
                null, null);
    }

    private FundRecord fromFund(String key, String cnpj, String subId, SemicolonCsv.Row fundRow,
                                SemicolonCsv.Row oldRow) {
        // O CNPJ pode ter outro registro de tipo fora do informe (FIDC, FIP, FII...): o fundo que informou a cota é o
        // de formato FI; o registro de outro tipo serve só de nome, sem dar situação, tipo nem gestor (D1).
        SemicolonCsv.Row fund = fundRow != null && informeType(fundRow.get("Tipo_Fundo")) ? fundRow : null;
        SemicolonCsv.Row old = oldRow != null && informeType(oldRow.get("TP_FUNDO")) ? oldRow : null;
        String legal = first(fund == null ? null : fund.get("Denominacao_Social"),
                old == null ? null : old.get("DENOM_SOCIAL"), lastLegalName(cnpj),
                fundRow == null ? null : fundRow.get("Denominacao_Social"),
                oldRow == null ? null : oldRow.get("DENOM_SOCIAL"));
        if (legal == null) {
            legal = "Fundo " + FundKeys.format(cnpj);
        }
        if (subId != null) {
            legal = legal + " - " + subId;
        }
        String status = fund != null ? fund.get("Situacao") : old == null ? null : old.get("SIT");
        return new FundRecord(key, cnpj, subId, subId == null ? null : cnpj, legal,
                subId == null ? currentTradeByCnpj.get(cnpj) : null,
                first(fund == null ? null : fund.get("Tipo_Fundo"), old == null ? null : old.get("TP_FUNDO")),
                old == null ? null : old.get("CLASSE"), old == null ? null : old.get("CLASSE_ANBIMA"),
                old == null ? null : old.get("PUBLICO_ALVO"), old == null ? null : yes(old.get("FUNDO_EXCLUSIVO")),
                old == null ? null : open(old.get("CONDOM")), old == null ? null : yes(old.get("TRIB_LPRAZO")),
                first(fund == null ? null : fund.get("Gestor"), old == null ? null : old.get("GESTOR")),
                FundKeys.cnpj(first(fund == null ? null : fund.get("CPF_CNPJ_Gestor"),
                        old == null ? null : old.get("CPF_CNPJ_GESTOR"))),
                first(fund == null ? null : fund.get("Administrador"), old == null ? null : old.get("ADMIN")),
                FundKeys.cnpj(first(fund == null ? null : fund.get("CNPJ_Administrador"),
                        old == null ? null : old.get("CNPJ_ADMIN"))),
                status,
                date(fund != null ? fund.get("Data_Inicio_Situacao") : old == null ? null : old.get("DT_INI_SIT")),
                date(fund != null ? fund.get("Data_Constituicao") : old == null ? null : old.get("DT_INI_ATIV")),
                decimal(fund != null ? fund.get("Patrimonio_Liquido") : old == null ? null : old.get("VL_PATRIM_LIQ")),
                date(fund != null ? fund.get("Data_Patrimonio_Liquido") : old == null ? null : old.get("DT_PATRIM_LIQ")));
    }

    private void loadHistory(Path zip) {
        SemicolonCsv.readZip(zip, Map.of(
                "cad_fi_hist_denom_social.csv", row -> addName(row, "DENOM_SOCIAL", false),
                "cad_fi_hist_denom_comerc.csv", row -> addName(row, "DENOM_COMERC", true)));
    }

    private void addName(SemicolonCsv.Row row, String column, boolean trade) {
        String cnpj = FundKeys.cnpj(row.get("CNPJ_FUNDO"));
        String name = row.get(column);
        if (cnpj == null || name == null || name.length() > 250) {
            return;
        }
        String suffix = trade ? "COMERC" : "SOCIAL";
        LocalDate from = date(row.get("DT_INI_DENOM_" + suffix));
        LocalDate to = date(row.get("DT_FIM_DENOM_" + suffix));
        namesByCnpj.computeIfAbsent(cnpj, k -> new ArrayList<>()).add(new Name(name, trade, from, to));
        if (trade && to == null) {
            currentTradeByCnpj.put(cnpj, name);
        }
    }

    private String lastLegalName(String cnpj) {
        return names(cnpj).stream().filter(n -> !n.trade())
                .max((a, b) -> compare(a.from(), b.from())).map(Name::name).orElse(null);
    }

    private static int compare(LocalDate a, LocalDate b) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        return b == null ? 1 : a.compareTo(b);
    }

    /**
     * Tipos de fundo que não informam cota diária (D1): FII e FIIM vêm da B3; FIDC, FIP, FIAGRO, Funcine e os
     * antecessores do FIP (FMIEE) ficam fora. Num CNPJ com registros de vários tipos (7 mil no registro_fundo, ex.: FI
     * cancelado + FIDC vivo), a chave do informe é o fundo de formato FI.
     */
    private static final Set<String> NON_INFORME_TYPES = Set.of("FIDC", "FIP", "FII", "FIIM", "FIAGRO", "FUNCINE",
            "FMIEE");

    static boolean informeType(String type) {
        return type == null || type.isBlank() || !NON_INFORME_TYPES.contains(FundKeys.normalize(type));
    }

    /** Entre dois registros de fundo do mesmo CNPJ: o de tipo que informa cota, depois a regra de {@link #better}. */
    private static SemicolonCsv.Row betterFund(SemicolonCsv.Row a, SemicolonCsv.Row b) {
        boolean ia = informeType(a.get("Tipo_Fundo"));
        boolean ib = informeType(b.get("Tipo_Fundo"));
        if (ia != ib) {
            return ib ? b : a;
        }
        return better(a, b);
    }

    /** O mesmo para o cadastro antigo ({@code cad_fi.csv}): tipo que informa cota, depois o vivo. */
    private static SemicolonCsv.Row betterOld(SemicolonCsv.Row a, SemicolonCsv.Row b) {
        boolean ia = informeType(a.get("TP_FUNDO"));
        boolean ib = informeType(b.get("TP_FUNDO"));
        if (ia != ib) {
            return ib ? b : a;
        }
        return statusRank(b.get("SIT")) > statusRank(a.get("SIT")) ? b : a;
    }

    /** Entre dois registros do mesmo CNPJ: o vivo antes do cancelado; empatando, o registro mais novo. */
    private static SemicolonCsv.Row better(SemicolonCsv.Row a, SemicolonCsv.Row b) {
        int ra = statusRank(a.get("Situacao"));
        int rb = statusRank(b.get("Situacao"));
        if (ra != rb) {
            return rb > ra ? b : a;
        }
        String da = a.get("Data_Registro");
        String db = b.get("Data_Registro");
        return db != null && (da == null || db.compareTo(da) > 0) ? b : a;
    }

    private static int statusRank(String status) {
        String s = FundKeys.normalize(status);
        if (s.contains("FUNCIONAMENTO NORMAL")) {
            return 3;
        }
        if (s.contains("PRE-OPERACIONAL") || s.contains("LIQUIDACAO") || s.contains("ESPECIAL")) {
            return 2;
        }
        return s.isEmpty() ? 0 : 1;
    }

    private static String kind(String classType, SemicolonCsv.Row fund) {
        if (classType != null && classType.contains("FAPI")) {
            return "FAPI";
        }
        if (classType != null && classType.contains("FIF")) {
            return "FIF";
        }
        return fund == null ? null : fund.get("Tipo_Fundo");
    }

    private static Boolean yes(String v) {
        return v == null ? null : v.trim().equalsIgnoreCase("S");
    }

    private static Boolean open(String v) {
        return v == null ? null : FundKeys.normalize(v).startsWith("ABERTO");
    }

    @SafeVarargs
    private static <T> T first(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    static LocalDate date(String v) {
        if (v == null || v.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(v.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static BigDecimal decimal(String v) {
        if (v == null) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v.replace(",", "."));
            return d.abs().compareTo(new BigDecimal("1E17")) >= 0 ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
