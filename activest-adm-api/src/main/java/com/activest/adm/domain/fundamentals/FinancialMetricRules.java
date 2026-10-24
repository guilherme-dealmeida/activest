package com.activest.adm.domain.fundamentals;

import com.activest.domain.issuer.StatementLayout;
import com.activest.adm.integration.cvm.CvmStatements.Line;
import com.activest.adm.integration.cvm.CvmStatements.Statement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Como cada metrica e achada nas contas de um DFP/ITR.
 *
 * <p><b>Por descricao, nao por codigo.</b> O plano previa um mapa fixo de codigos
 * por layout; os arquivos reais desmentem: no mesmo DFP 2025 o lucro liquido e a
 * conta 3.09 no Itau, 3.11 no BB e 3.13 no IRB, e o PL e 2.08, 2.07 e 2.03. O BB
 * mudou de 3.09/2.08 (ate 2015) para 3.11/2.07 (2020). As descricoes padronizadas
 * da CVM se mantem ("Lucro/Prejuizo Consolidado do Periodo", "Atribuido a Socios
 * da Empresa Controladora", "Patrimonio Liquido Consolidado") — e por elas que as
 * regras procuram, no nivel certo da arvore. O que e estavel por codigo (receita
 * 3.01, ativo total 1, FCO 6.01, caixa e divida do layout comercial) fica por
 * codigo.</p>
 *
 * <p>Metrica nova = regra nova aqui + reprocessar {@code carga/data/cvm} (sem rede).</p>
 */
public final class FinancialMetricRules {

    /** Um valor ja em reais (ou reais por acao, no LPA). {@code periodStart} nulo = posicao. */
    public record Value(FinancialMetric metric, LocalDate periodStart, LocalDate periodEnd, BigDecimal value) {
    }

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private FinancialMetricRules() {
    }

    /** Layout pelo nome da conta 3.01 (PLANO, 1.1): intermediacao financeira = banco; seguro = seguradora. */
    public static StatementLayout layoutOf(List<Line> lines) {
        for (Line l : lines) {
            if (l.statement() == Statement.DRE && l.code().equals("3.01")) {
                if (l.key().contains("intermediacao financeira")) {
                    return StatementLayout.BANCO;
                }
                if (l.key().contains("segur")) {
                    return StatementLayout.SEGURADORA;
                }
                return StatementLayout.COMERCIAL;
            }
        }
        return StatementLayout.COMERCIAL;
    }

    public static List<Value> extract(List<Line> lines, StatementLayout layout) {
        List<Value> out = new ArrayList<>();
        // um grupo por (demonstracao, periodo): no ITR o DRE traz o trimestre e o acumulado
        Map<GroupKey, Map<String, Line>> groups = new LinkedHashMap<>();
        for (Line l : lines) {
            Statement s = l.statement() == Statement.DFC_MD ? Statement.DFC_MI : l.statement();
            groups.computeIfAbsent(new GroupKey(s, l.periodStart(), l.periodEnd()), k -> new TreeMap<>())
                    .putIfAbsent(l.code(), l);
        }
        groups.forEach((g, accounts) -> {
            switch (g.statement()) {
                case DRE -> income(accounts, g, layout, out);
                case BPA -> assets(accounts, g, layout, out);
                case BPP -> liabilities(accounts, g, layout, out);
                case DFC_MI -> cashFlow(accounts, g, layout, out);
                case DVA -> valueAdded(accounts, g, layout, out);
                default -> { }
            }
        });
        return withoutBiologicalDepletion(out);
    }

    /**
     * D22 (docs/planos/PLANO_TELA_ATIVOS.md, 24/10/2026): a D&A do EBITDA e a da DVA menos a exaustao dos ativos
     * biologicos que a DFC traz em linha propria, no mesmo periodo. Klabin 2025: 4.990.437 - 2.697.982 = 2.292.455 mil
     * (= a "depreciacao e amortizacao" da DFC, e o EBITDA bate com o Investidor10). Sem a linha, a DVA fica como esta.
     */
    private static List<Value> withoutBiologicalDepletion(List<Value> out) {
        List<Value> result = new ArrayList<>(out.size());
        for (Value v : out) {
            if (v.metric() == FinancialMetric.DEPRECIACAO_AMORTIZACAO) {
                BigDecimal depletion = out.stream()
                        .filter(x -> x.metric() == FinancialMetric.EXAUSTAO_BIOLOGICA
                                && Objects.equals(x.periodStart(), v.periodStart()) && x.periodEnd().equals(v.periodEnd()))
                        .map(Value::value).findFirst().orElse(null);
                if (depletion != null) {
                    result.add(new Value(v.metric(), v.periodStart(), v.periodEnd(), v.value().subtract(depletion)));
                    continue;
                }
            }
            result.add(v);
        }
        return result;
    }

    private record GroupKey(Statement statement, LocalDate start, LocalDate end) {
    }

    // ------------------------------------------------------------------ DRE

    private static void income(Map<String, Line> a, GroupKey g, StatementLayout layout, List<Value> out) {
        put(out, FinancialMetric.RECEITA, g, amount(a.get("3.01")));
        put(out, FinancialMetric.EBIT, g, amount(level1(a, "3", k -> k.startsWith("resultado antes do resultado financeiro"))));
        // Contas novas (docs/planos/PLANO_TELA_ATIVOS.md, 2.5), por descricao como o EBIT: o codigo muda no banco
        // (IR/CSLL e 3.06 la) e o lucro bruto do banco e "de intermediacao financeira" (outra conta, fora).
        if (layout == StatementLayout.COMERCIAL) {
            put(out, FinancialMetric.LUCRO_BRUTO, g,
                    amount(level1(a, "3", k -> k.startsWith("resultado bruto") && !k.contains("intermediacao"))));
        }
        put(out, FinancialMetric.LAIR, g, amount(level1(a, "3", k -> k.startsWith("resultado antes dos tributos"))));
        put(out, FinancialMetric.IR_CSLL, g,
                amount(level1(a, "3", k -> k.startsWith("imposto de renda e contribuicao social"))));

        Line net = last(a, "3", FinancialMetricRules::isNetIncome);
        if (net != null) {
            BigDecimal total = amount(net);
            put(out, FinancialMetric.LUCRO_LIQUIDO, g, total);
            BigDecimal parent = amount(child(a, net.code(), k -> k.contains("controladora") && !k.contains("nao controlador")));
            BigDecimal minority = amount(child(a, net.code(), k -> k.contains("nao controlador")));
            // IRB, DFP 2025: controladora e nao controladores informados como 0 com lucro de 391 mi —
            // sem minoritarios, o lucro inteiro e da controladora. Individual nao tem a quebra.
            boolean split = parent != null && (parent.signum() != 0 || (minority != null && minority.signum() != 0));
            put(out, FinancialMetric.LUCRO_CONTROLADORA, g, split ? parent : total);
        }
        for (Line l : a.values()) {
            if (l.code().startsWith("3.99.01.") && l.depth() == 3 && l.value().signum() != 0) {
                String k = l.key();
                if (k.equals("on")) {
                    putFirst(out, FinancialMetric.LPA_ON, g, l.value());
                } else if (k.startsWith("pn")) {
                    putFirst(out, FinancialMetric.LPA_PN, g, l.value());
                }
            }
        }
    }

    /** "Lucro/Prejuizo Consolidado do Periodo", "Lucro ou Prejuizo Liquido Consolidado do Periodo", "Lucro/Prejuizo do Periodo". */
    static boolean isNetIncome(String k) {
        return k.startsWith("lucro") && (k.contains("periodo") || k.contains("exercicio"))
                && !k.contains("antes") && !k.contains("operacoes");
    }

    // ------------------------------------------------------------------ balanco

    private static void assets(Map<String, Line> a, GroupKey g, StatementLayout layout, List<Value> out) {
        put(out, FinancialMetric.ATIVO_TOTAL, g, amount(a.get("1")));
        if (layout == StatementLayout.COMERCIAL) {
            put(out, FinancialMetric.CAIXA, g, sum(amount(a.get("1.01.01")), amount(a.get("1.01.02"))));
            put(out, FinancialMetric.ATIVO_CIRCULANTE, g, amount(byCode(a, "1.01", "ativo circulante")));
        }
    }

    private static void liabilities(Map<String, Line> a, GroupKey g, StatementLayout layout, List<Value> out) {
        Line equity = level1(a, "2", k -> k.startsWith("patrimonio liquido"));
        if (equity != null) {
            put(out, FinancialMetric.PL, g, amount(equity));
            put(out, FinancialMetric.PL_NAO_CONTROLADORES, g,
                    amount(child(a, equity.code(), k -> k.contains("nao controlador"))));
        }
        if (layout == StatementLayout.COMERCIAL) {
            put(out, FinancialMetric.PASSIVO_CIRCULANTE, g, amount(byCode(a, "2.01", "passivo circulante")));
            Line shortTerm = a.get("2.01.04");
            Line longTerm = a.get("2.02.01");
            put(out, FinancialMetric.DIVIDA_BRUTA, g, sum(
                    shortTerm != null && shortTerm.key().startsWith("emprestimos") ? amount(shortTerm) : null,
                    longTerm != null && longTerm.key().startsWith("emprestimos") ? amount(longTerm) : null));
        }
    }

    // ------------------------------------------------------------------ DFC

    private static void cashFlow(Map<String, Line> a, GroupKey g, StatementLayout layout, List<Value> out) {
        put(out, FinancialMetric.FCO, g, amount(a.get("6.01")));
        put(out, FinancialMetric.DIVIDENDOS_PAGOS, g, dividendsPaid(a));
        if (layout == StatementLayout.COMERCIAL) {
            put(out, FinancialMetric.EXAUSTAO_BIOLOGICA, g, biologicalDepletion(a));
        }
    }

    /**
     * Ajustes do FCO (6.01.01.*) so de exaustao — sem depreciacao nem amortizacao na mesma linha ("Exaustao dos ativos
     * biologicos" da Klabin; a linha unica "Depreciacao, amortizacao e exaustao" fica, porque nao separa). Somadas;
     * nulo sem nenhuma.
     */
    static BigDecimal biologicalDepletion(Map<String, Line> a) {
        BigDecimal total = null;
        for (Line l : a.values()) {
            String k = l.key();
            if (l.code().startsWith("6.01.01.") && l.depth() == 3 && k.contains("exaust")
                    && !k.contains("deprec") && !k.contains("amortiz")) {
                BigDecimal v = amount(l);
                total = total == null ? v : total.add(v);
            }
        }
        return total;
    }

    /**
     * Dividendos/JCP pagos aos acionistas da controladora: contas das atividades de financiamento (6.03.*) com
     * dividendo ou JCP na descricao, menos as recebidas e as pagas a nao controladores, sem contar uma conta e a
     * filha dela. Os nomes variam ("Dividendos/JCP Pagos a Acionistas" na Vale, "Dividendos pagos a acionistas da
     * Petrobras", "Pgto de Dividendos/Juros s/ Capital Proprio" na WEG, controladores e nao controladores
     * separados no BB e no Itau); a conta e saida de caixa (negativa), o valor gravado e o pago (positivo).
     * Sem a demonstracao de financiamento, nulo; com ela e sem conta de dividendos, 0.
     */
    static BigDecimal dividendsPaid(Map<String, Line> a) {
        if (a.keySet().stream().noneMatch(c -> c.startsWith("6.03"))) {
            return null;
        }
        List<Line> matched = a.values().stream()
                .filter(l -> l.code().startsWith("6.03.") && isDividendPaid(l.key()))
                .toList();
        BigDecimal total = BigDecimal.ZERO;
        for (Line l : matched) {
            boolean ancestorMatched = matched.stream()
                    .anyMatch(p -> p != l && l.code().startsWith(p.code() + "."));
            if (!ancestorMatched) {
                total = total.add(amount(l));
            }
        }
        return total.negate().setScale(2, RoundingMode.HALF_UP);
    }

    static boolean isDividendPaid(String k) {
        boolean dividend = k.contains("dividend") || k.contains("capital proprio") || k.contains("jcp")
                || k.contains("juros s/");
        boolean excluded = k.contains("recebid") || k.contains("nao controlador") || k.contains("minoritari");
        return dividend && !excluded;
    }

    // ------------------------------------------------------------------ DVA

    private static void valueAdded(Map<String, Line> a, GroupKey g, StatementLayout layout, List<Value> out) {
        if (layout == StatementLayout.COMERCIAL) {
            // DVA 7.04.01 "Depreciacao, Amortizacao e Exaustao", filha de "Retencoes"; a conta vem negativa (retencao)
            // e o valor gravado e a despesa, positiva (base do EBITDA — docs/planos/PLANO_TELA_ATIVOS.md, 2.5)
            Line retentions = level1(a, "7", k -> k.startsWith("retencoes"));
            Line da = retentions == null ? null : child(a, retentions.code(), k -> k.startsWith("depreciacao"));
            BigDecimal v = amount(da);
            put(out, FinancialMetric.DEPRECIACAO_AMORTIZACAO, g, v == null ? null : v.negate());
        }
        Line own = a.values().stream()
                .filter(l -> l.depth() == 2 && l.key().startsWith("remuneracao de capitais proprios"))
                .findFirst().orElse(null);
        if (own == null) {
            return;
        }
        put(out, FinancialMetric.JCP_DECLARADO, g, amount(child(a, own.code(), k -> k.startsWith("juros sobre o capital"))));
        put(out, FinancialMetric.DIVIDENDOS_DECLARADOS, g, amount(child(a, own.code(), k -> k.startsWith("dividendos"))));
    }

    // ------------------------------------------------------------------ arvore

    /** A conta do codigo, se a descricao confere (codigo estavel no layout comercial, descricao como trava). */
    private static Line byCode(Map<String, Line> a, String code, String keyPrefix) {
        Line l = a.get(code);
        return l != null && l.key().startsWith(keyPrefix) ? l : null;
    }

    /** Conta de 1o nivel abaixo de {@code root} ("3.05", "2.03") cuja descricao atende. A primeira. */
    private static Line level1(Map<String, Line> a, String root, java.util.function.Predicate<String> test) {
        return a.values().stream()
                .filter(l -> l.depth() == 1 && l.code().startsWith(root + ".") && !l.code().startsWith("3.99"))
                .filter(l -> test.test(l.key()))
                .findFirst().orElse(null);
    }

    /** Idem, a ultima pela ordem do codigo (o lucro do periodo vem depois do das operacoes). */
    private static Line last(Map<String, Line> a, String root, java.util.function.Predicate<String> test) {
        return a.values().stream()
                .filter(l -> l.depth() == 1 && l.code().startsWith(root + ".") && !l.code().startsWith("3.99"))
                .filter(l -> test.test(l.key()))
                .max(Comparator.comparing(l -> sortable(l.code()))).orElse(null);
    }

    private static Line child(Map<String, Line> a, String parent, java.util.function.Predicate<String> test) {
        int depth = parent.split("\\.").length;
        return a.values().stream()
                .filter(l -> l.code().startsWith(parent + ".") && l.depth() == depth)
                .filter(l -> test.test(l.key()))
                .findFirst().orElse(null);
    }

    /** "3.9" < "3.10": compara nivel a nivel como numero. */
    private static String sortable(String code) {
        StringBuilder b = new StringBuilder();
        for (String p : code.split("\\.")) {
            b.append(String.format("%04d", Integer.parseInt(p.replaceAll("\\D", "0"))));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ valores

    private static BigDecimal amount(Line l) {
        if (l == null) {
            return null;
        }
        BigDecimal v = l.thousands() ? l.value().multiply(THOUSAND) : l.value();
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a.add(b);
    }

    private static void put(List<Value> out, FinancialMetric m, GroupKey g, BigDecimal v) {
        if (v != null) {
            out.add(new Value(m, g.start(), g.end(), v));
        }
    }

    private static void putFirst(List<Value> out, FinancialMetric m, GroupKey g, BigDecimal v) {
        boolean exists = out.stream().anyMatch(x -> x.metric() == m && Objects.equals(x.periodStart(), g.start())
                && x.periodEnd().equals(g.end()));
        if (!exists) {
            out.add(new Value(m, g.start(), g.end(), v));
        }
    }
}
