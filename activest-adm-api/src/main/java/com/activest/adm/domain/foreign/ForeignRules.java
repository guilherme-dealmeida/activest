package com.activest.adm.domain.foreign;

import com.activest.adm.integration.foreign.NasdaqTraderClient.Listing;
import com.activest.adm.integration.foreign.OpenFigiClient.Figi;
import com.activest.domain.asset.AssetTypeNames;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras do catálogo vivo dos EUA (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C1–C4): o que fica fora, o tipo, o ticker
 * no formato da C3, o nome de exibição e as categorias do ETF (palavras do nome) e do REIT (setor da Nareit). Sem estado:
 * a tarefa {@code FOREIGN_CATALOG} junta as fontes e chama estas funções.
 */
public final class ForeignRules {

    private ForeignRules() {
    }

    // ------------------------------------------------------------------ C1/C2: o que fica fora

    private static final int FLAGS = Pattern.CASE_INSENSITIVE;
    private static final Pattern WARRANT = Pattern.compile("\\bwarrants?\\b|\\bwts?\\b", FLAGS);
    private static final Pattern RIGHT = Pattern.compile("\\brights?\\b", FLAGS);
    private static final Pattern UNIT = Pattern.compile("\\bunits?\\b", FLAGS);
    /** Unit de SPAC: "Units, each consisting of one share and one warrant". A unit de MLP (Common Units) é ação. */
    private static final Pattern SPAC_UNIT = Pattern.compile(
            "each consisting|consisting of|one (class [a-z] )?(ordinary |common )?share|one right|warrant|acquisition"
                    + "|one-(half|third|fourth|fifth)", FLAGS);
    private static final Pattern STRUCTURED = Pattern.compile(
            "corporate units|tangible equity units|equity units|\\bzones\\b|purchase contract|"
                    + "exchangeable (notes|debentures)|structured products?|trust certificates|strats|"
                    + "\\bPPLUS\\b|\\bSATURNS\\b|\\bCorTS\\b|power bonds|\\bquarterly income\\b.*\\bnotes\\b", FLAGS);
    private static final Pattern DEBT = Pattern.compile(
            "\\bnotes?\\b|\\bdebentures?\\b|\\bbonds? due\\b|\\bdue (19|20)\\d\\d\\b|\\bsenior (secured |unsecured )?notes\\b",
            FLAGS);

    /** Nota negociada em bolsa (ETN): o OpenFIGI decide (ETP = ETF), não a regra da nota de dívida. */
    private static final Pattern ETN = Pattern.compile("exchange[- ]traded notes?|\\bETNs?\\b", FLAGS);
    /**
     * Preferencial pelo nome: na Nasdaq o símbolo não tem {@code $} (AGNCL, ACGLO) e o OpenFIGI não acha a
     * preferencial pelo ticker.
     */
    private static final Pattern PREFERRED_NAME = Pattern.compile(
            "preferred|depositary shares|cumulative|perpetual|redeemable|fixed-to-floating|fixed-rate reset", FLAGS);

    /** Preferencial: {@code $} no símbolo (NYSE) ou o nome (Nasdaq). */
    public static boolean preferred(Listing l) {
        return l.preferred() || (!l.etf() && l.name() != null && PREFERRED_NAME.matcher(l.name()).find());
    }

    /** {@code securityType} do OpenFIGI que é ação (D4): comum, ADR, MLP, preferencial... */
    static final Set<String> FIGI_STOCK = Set.of("Common Stock", "ADR", "MLP", "Ltd Part", "Royalty Trst", "Tracking Stk",
            "NY Reg Shrs", "GDR", "Preference");
    /** Fundo listado: ETP; o fundo fechado também (P1). */
    static final Set<String> FIGI_FUND = Set.of("ETP", "Closed-End Fund");
    /** Fora por definição (C2). */
    static final Set<String> FIGI_OUT = Set.of("Equity WRT", "Right", "Unit");

    /** Por que o papel fica fora pela lista (nome), ou nulo. A ETF da bolsa nunca cai aqui. */
    public static String excludedByName(Listing l) {
        if (l.etf()) {
            return null;
        }
        String n = l.name() == null ? "" : l.name();
        if (UNIT.matcher(n).find() && SPAC_UNIT.matcher(n).find()) {
            return "unit de SPAC";
        }
        if (WARRANT.matcher(n).find()) {
            return "warrant";
        }
        if (RIGHT.matcher(n).find()) {
            return "right";
        }
        if (STRUCTURED.matcher(n).find()) {
            return "papel estruturado";
        }
        if (DEBT.matcher(n).find() && !n.toLowerCase(Locale.ROOT).contains("preferred") && !ETN.matcher(n).find()) {
            return "nota ou título de dívida";
        }
        return null;
    }

    /** O tipo decidido e de onde veio (D4, D5, P1). */
    public record Typed(String type, String source) {
    }

    /**
     * Tipo do papel (C2), na ordem: marca de ETF da bolsa → ETF; preferencial pelo símbolo ({@code $}) → STOCK (o
     * OpenFIGI não acha preferencial pelo ticker); {@code securityType} do OpenFIGI. Nulo = fora; {@link #whyNot} diz
     * por quê.
     */
    public static Typed type(Listing l, Figi f) {
        if (l.etf()) {
            return new Typed(AssetTypeNames.ETF_INTERNACIONAL,
                    f != null && FIGI_FUND.contains(f.securityType()) ? "OPENFIGI" : "LIST");
        }
        if (l.preferred()) {
            return new Typed(AssetTypeNames.STOCK, "LIST");
        }
        if (f == null || f.securityType() == null) {
            return preferred(l) ? new Typed(AssetTypeNames.STOCK, "LIST") : null;
        }
        String t = f.securityType();
        if ("REIT".equals(t)) {
            return new Typed(AssetTypeNames.REIT, "OPENFIGI");
        }
        if (FIGI_FUND.contains(t)) {
            return new Typed(AssetTypeNames.ETF_INTERNACIONAL, "OPENFIGI");
        }
        if (FIGI_STOCK.contains(t)) {
            return new Typed(AssetTypeNames.STOCK, "OPENFIGI");
        }
        return null;
    }

    /** O motivo de {@link #type} ter devolvido nulo. */
    public static String whyNot(Figi f) {
        if (f == null) {
            return "sem FIGI no OpenFIGI";
        }
        if (FIGI_OUT.contains(f.securityType())) {
            return "OpenFIGI: " + f.securityType();
        }
        return "tipo do OpenFIGI fora da regra: " + f.securityType();
    }

    // ------------------------------------------------------------------ C3: ticker

    /** Ticker do catálogo (C3): classe com ponto ({@code BRK.B}), preferencial com hífen e a série ({@code ABR-E}). */
    public static String ticker(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT).replace('$', '-');
    }

    /** Forma Bloomberg para o OpenFIGI ({@code BRK/B}); nula na preferencial, que o OpenFIGI não acha pelo ticker. */
    public static String bloomberg(String symbol) {
        return symbol.contains("$") ? null : symbol.trim().toUpperCase(Locale.ROOT).replace('.', '/');
    }

    /** Forma da SEC ({@code BRK-B}, preferencial {@code ABR-PE}). */
    public static String secTicker(String symbol) {
        String s = symbol.trim().toUpperCase(Locale.ROOT).replace('.', '-');
        int p = s.indexOf('$');
        return p < 0 ? s : s.substring(0, p) + "-P" + s.substring(p + 1);
    }

    /** O papel da empresa: o símbolo sem a classe e sem a série da preferencial ({@code BRK.B} → {@code BRK}). */
    public static String root(String symbol) {
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        int cut = s.length();
        for (char c : new char[]{'$', '.'}) {
            int p = s.indexOf(c);
            if (p > 0 && p < cut) {
                cut = p;
            }
        }
        return s.substring(0, cut);
    }

    // ------------------------------------------------------------------ nome

    private static final Pattern NAME_TAIL = Pattern.compile(
            "\\s*,?\\s+(-\\s+)?((Class|Series) [A-Z0-9]+\\s+)?(New\\s+)?(Common Stock|Common Shares|Ordinary Shares|"
                    + "Common Units?|American Depositary Shares?|American Depository Shares?|ADSs?\\b|Sponsored ADRs?|"
                    + "Depositary Shares?|Shares of Beneficial Interest|Units? Representing|Limited Partnership Units?|"
                    + "Subordinate Voting Shares|Voting Common Stock|Capital Stock|Registered Shares|Common Shares? of "
                    + "Beneficial Interest).*$", FLAGS);
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Nome de exibição: o da lista, sem a descrição do papel ("Apple Inc. - Common Stock" → "Apple Inc."); ETF e
     * preferencial ficam com o nome inteiro (a série e o cupom dizem qual é).
     */
    public static String displayName(Listing l) {
        String n = l.name() == null ? l.symbol() : SPACES.matcher(l.name().trim()).replaceAll(" ");
        if (!l.etf() && !l.preferred()) {
            int dash = n.indexOf(" - ");
            if (dash > 0) {
                n = n.substring(0, dash);
            }
            String cut = NAME_TAIL.matcher(n).replaceFirst("").trim();
            if (!cut.isEmpty()) {
                n = cut;
            }
            n = n.replaceAll("[,\\s]+$", "");
        }
        return n.length() > 255 ? n.substring(0, 255) : n;
    }

    private static final Set<String> NAME_NOISE = Set.of("INC", "INCORPORATED", "CORP", "CORPORATION", "CO", "COMPANY",
            "LTD", "LIMITED", "PLC", "NV", "SA", "AG", "SE", "LP", "LLC", "HOLDINGS", "HOLDING", "GROUP", "THE", "ADR",
            "ADS", "SPONSORED", "CLASS", "NEW", "DE", "AB", "ASA", "SPA", "OYJ", "TRUST");

    /** Nome normalizado para casar a empresa com o BDR (C4): maiúsculas, sem acento, sem pontuação e sem "Inc.". */
    public static String companyKey(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replace("&", " AND ").replaceAll("[^A-Z0-9]+", " ");
        List<String> words = new java.util.ArrayList<>();
        for (String w : s.trim().split(" ")) {
            if (!w.isEmpty() && !NAME_NOISE.contains(w)) {
                words.add(w);
            }
        }
        // a letra da classe no fim ("Berkshire Hathaway Inc. B", "Alphabet Inc. A")
        while (words.size() > 1 && words.get(words.size() - 1).length() == 1) {
            words.remove(words.size() - 1);
        }
        return String.join(" ", words);
    }

    // ------------------------------------------------------------------ C4: categorias

    private record Rule(String category, Pattern pattern) {
    }

    /** Regras do ETF, em ordem (C4): alavancado/inverso primeiro; o resto em Outros. */
    private static final List<Rule> ETF_RULES = List.of(
            new Rule("ALAVANCADOS_INVERSOS", Pattern.compile(
                    "\\b[1-9](\\.\\d+)?[xX]\\b|-[1-9](\\.\\d+)?[xX]\\b|\\bUltra(Pro)?(Short)?\\b(?!-)|\\bleverag|\\binverse\\b|"
                            + "\\bbear\\b|\\bbull\\b|\\bdaily\\b.*\\b(long|short)\\b",
                    FLAGS)),
            new Rule("CRIPTO", Pattern.compile(
                    "bitcoin|\\bether(eum)?\\b|crypto|\\bsolana\\b|\\bXRP\\b|dogecoin|litecoin|digital assets?|\\bcardano\\b|"
                            + "hyperliquid|\\bstaking\\b|\\bBNB\\b|\\bTRON\\b|"
                            + "\\bavalanche\\b|\\bpolkadot\\b|\\bchainlink\\b|\\bSUI\\b|\\bHBAR\\b", FLAGS)),
            new Rule("OURO_COMMODITIES", Pattern.compile(
                    "\\bgold\\b|\\bsilver\\b|precious metal|platinum|palladium|commodit|\\bcrude\\b|\\boil fund\\b|"
                            + "natural gas fund|\\bbrent\\b|\\bWTI\\b|\\bcorn\\b|\\bwheat\\b|soybean|\\bsugar fund\\b|"
                            + "agricultur(e|al) fund|copper index|\\bcarbon (allowances|credit)", FLAGS)),
            new Rule("RENDA_FIXA", Pattern.compile(
                    "\\bbonds?\\b|treasur|\\bT-?Bills?\\b|municipal|\\bmuni\\b|aggregate|corporate|high yield|fixed income|"
                            + "\\bcredit\\b|floating rate|duration|\\bTIPS\\b|inflation[- ]protected|mortgage[- ]backed|\\bMBS\\b|"
                            + "\\bCLO\\b|\\bloans?\\b|securitized|\\bdebt\\b|government|ultra[- ]short|money market|"
                            + "\\bincome opportunit|investment grade|\\bnotes?\\b|\\bcash\\b|inflation", FLAGS)),
            new Rule("IMOBILIARIO", Pattern.compile("real estate|\\bREITs?\\b|realty|property|properties|homebuilders?",
                    FLAGS)),
            new Rule("DIVIDENDOS_RENDA", Pattern.compile(
                    "dividend|covered call|buy-?write|premium income|option income|equity income|\\byield\\b|\\bincome\\b",
                    FLAGS)),
            new Rule("OUTROS_ESTRATEGIA", Pattern.compile(
                    "buffer|defined outcome|hedged|managed futures|long/short|market neutral|tail risk|"
                            + "(?<!low |minimum )volatility|\\bVIX\\b|"
                            + "allocation|target date|multi-?asset|merger arbitrage|\\bfloor\\b|\\bcap(ped)?\\b.*\\boutcome|"
                            + "\\bprotect", FLAGS)),
            new Rule("ACOES_INTERNACIONAIS", Pattern.compile(
                    "international|emerging|\\bEAFE\\b|global|world|ex[- ]?U\\.?S|developed|europe|eurozone|japan|china|"
                            + "chinese|india|brazil|korea|taiwan|\\basia|pacific|latin|canada|mexico|germany|\\bU\\.?K\\.?\\b|"
                            + "united kingdom|australia|frontier|israel|switzerland|france|italy|spain|vietnam|indonesia|"
                            + "saudi|south africa|singapore|hong kong|chile|peru|argentina|colombia|philippines|thailand|"
                            + "malaysia|poland|turkey|greece|nordic|sweden|netherlands|ireland|BRIC|\\bACWI\\b|\\bnon-U\\.?S",
                    FLAGS)),
            new Rule("ACOES_EUA", Pattern.compile(
                    "S&P|nasdaq|\\bQQQ\\b|russell|\\bdow\\b|\\bU\\.?S\\.?\\b|america|total (stock )?market|large[- ]?cap|mid[- ]?cap|"
                            + "small[- ]?cap|micro[- ]?cap|\\bgrowth\\b|\\bvalue\\b|equal weight|momentum|quality|"
                            + "low volatility|minimum volatility|\\bequity\\b|\\bstock\\b|sector|technology|financials?|"
                            + "health ?care|energy|utilities|industrials|materials|consumer|semiconductor|biotech|"
                            + "\\b500\\b|\\b1000\\b|\\b2000\\b|communication|discretionary|staples|software|cyber|robotic|"
                            + "artificial intelligence|\\bAI\\b|cloud|internet|innovation|blockchain|clean energy|solar|"
                            + "infrastructure|aerospace|defense|pharma|medical|\\bbanks?\\b|insurance|retail|transport|"
                            + "\\bwater\\b|uranium|nuclear|lithium|battery|fintech|\\bspace\\b|genomic|oil|gas\\b", FLAGS)));

    /** "Short" é inverso (ProShares Short S&P500), menos no fundo de títulos curtos (Short Treasury, Short-Term). */
    private static final Pattern SHORT = Pattern.compile("\\bshort\\b", FLAGS);
    private static final Pattern BOND_WORDS = Pattern.compile(
            "bond|treasur|duration|maturity|term|income|muni|credit|yield|government|corporate|T-?Bill|floating|loan|"
                    + "\\bcash\\b|money market|fixed", FLAGS);

    /** Categoria do ETF pelas palavras do nome (C4); sem regra, Outros e estratégia. */
    public static String etfCategory(String name) {
        String n = name == null ? "" : name;
        if (SHORT.matcher(n).find() && !BOND_WORDS.matcher(n).find()) {
            return "ALAVANCADOS_INVERSOS";
        }
        for (Rule r : ETF_RULES) {
            if (r.pattern().matcher(n).find()) {
                return r.category();
            }
        }
        return "OUTROS_ESTRATEGIA";
    }

    private static final Map<String, String> NAREIT = Map.ofEntries(
            Map.entry("RESIDENTIAL", "RESIDENCIAL"),
            Map.entry("INDUSTRIAL", "INDUSTRIAL"),
            Map.entry("OFFICE", "ESCRITORIOS"),
            Map.entry("RETAIL", "VAREJO"),
            Map.entry("DATA CENTERS", "DATA_CENTERS"),
            Map.entry("TELECOMMUNICATIONS", "TORRES_TELECOM"),
            Map.entry("INFRASTRUCTURE", "TORRES_TELECOM"),
            Map.entry("HEALTH CARE", "SAUDE"),
            Map.entry("SELF STORAGE", "SELF_STORAGE"),
            Map.entry("LODGING/RESORTS", "HOTEIS_LAZER"),
            Map.entry("DIVERSIFIED", "DIVERSIFICADOS"),
            Map.entry("SPECIALTY", "ESPECIAIS"),
            Map.entry("GAMING", "ESPECIAIS"),
            Map.entry("TIMBERLAND", "ESPECIAIS"),
            Map.entry("TIMBER", "ESPECIAIS"),
            Map.entry("MORTGAGE", "HIPOTECAS"));

    /** Grupo do REIT pelo setor da Nareit (C4); "Industrial/Office" (anos antigos) vai pelo subsetor. */
    public static String reitCategory(String propertySector, String subsector) {
        if (propertySector == null) {
            return null;
        }
        String s = propertySector.trim().toUpperCase(Locale.ROOT);
        if (s.equals("INDUSTRIAL/OFFICE")) {
            String sub = subsector == null ? "" : subsector.trim().toUpperCase(Locale.ROOT);
            return sub.startsWith("INDUSTRIAL") ? "INDUSTRIAL" : sub.startsWith("OFFICE") ? "ESCRITORIOS" : "DIVERSIFICADOS";
        }
        return NAREIT.get(s);
    }
}
