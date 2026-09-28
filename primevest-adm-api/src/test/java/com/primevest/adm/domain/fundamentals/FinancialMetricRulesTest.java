package com.primevest.adm.domain.fundamentals;

import com.primevest.domain.issuer.StatementLayout;
import com.primevest.adm.integration.cvm.CvmFixtures;
import com.primevest.adm.integration.cvm.CvmStatements;
import com.primevest.adm.integration.cvm.CvmStatements.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras das metricas sobre recortes reais da CVM (src/test/resources/cvm): DFP
 * 2025 de Petrobras, Itau, BB, IRB, Klabin, Telebras, Vivara e Camil; ITR 2026 de
 * Petrobras, Itau e Camil; DFP 2015 de BB e Petrobras.
 */
class FinancialMetricRulesTest {

    @TempDir
    static Path tmp;

    static Map<CvmStatements.DocKey, Document> dfp;
    static Map<CvmStatements.DocKey, Document> itr;
    static Map<CvmStatements.DocKey, Document> dfp2015;

    static final LocalDate DEZ_2025 = LocalDate.of(2025, 12, 31);

    @BeforeAll
    static void ler() {
        dfp = CvmStatements.read(CvmFixtures.zip("dfp_cia_aberta_2026", 2025, tmp), "dfp", 2025, c -> true);
        itr = CvmStatements.read(CvmFixtures.zip("itr_cia_aberta_2026", 2026, tmp), "itr", 2026, c -> true);
        dfp2015 = CvmStatements.read(CvmFixtures.zip("dfp2015", 2015, tmp), "dfp", 2015, c -> true);
    }

    private static Document doc(Map<CvmStatements.DocKey, Document> docs, String cnpj, LocalDate ref) {
        return docs.get(new CvmStatements.DocKey(cnpj, ref));
    }

    private static List<CvmStatements.Line> basis(Document d) {
        return d.consolidated().isEmpty() ? d.individual() : d.consolidated();
    }

    private static List<FinancialMetricRules.Value> metrics(Document d) {
        List<CvmStatements.Line> lines = basis(d);
        return FinancialMetricRules.extract(lines, FinancialMetricRules.layoutOf(lines));
    }

    /** O valor da metrica no periodo acumulado (ou na posicao) terminado na data do documento. */
    private static BigDecimal value(List<FinancialMetricRules.Value> values, FinancialMetric m, LocalDate end) {
        return values.stream().filter(v -> v.metric() == m && v.periodEnd().equals(end))
                .sorted((a, b) -> a.periodStart() == null ? -1 : b.periodStart() == null ? 1
                        : a.periodStart().compareTo(b.periodStart()))
                .map(FinancialMetricRules.Value::value).findFirst().orElse(null);
    }

    private static BigDecimal mi(long millions) {
        return BigDecimal.valueOf(millions).multiply(BigDecimal.valueOf(1_000_000));
    }

    @Test
    @DisplayName("Petrobras DFP 2025: as contas do plano (LPA 8,54; PL 417.587 mi; payout pela DVA)")
    void petrobras() {
        Document d = doc(dfp, "33000167000101", DEZ_2025);
        assertThat(d.version()).isEqualTo(1);
        assertThat(FinancialMetricRules.layoutOf(basis(d))).isEqualTo(StatementLayout.COMERCIAL);
        var v = metrics(d);
        assertThat(value(v, FinancialMetric.RECEITA, DEZ_2025)).isEqualByComparingTo(mi(497_549));
        assertThat(value(v, FinancialMetric.EBIT, DEZ_2025)).isEqualByComparingTo(mi(145_628));
        assertThat(value(v, FinancialMetric.LUCRO_LIQUIDO, DEZ_2025)).isEqualByComparingTo(mi(110_605));
        assertThat(value(v, FinancialMetric.LUCRO_CONTROLADORA, DEZ_2025)).isEqualByComparingTo(mi(110_129));
        assertThat(value(v, FinancialMetric.ATIVO_TOTAL, DEZ_2025)).isEqualByComparingTo(mi(1_223_389));
        assertThat(value(v, FinancialMetric.CAIXA, DEZ_2025)).isEqualByComparingTo(mi(35_608 + 15_000));
        assertThat(value(v, FinancialMetric.DIVIDA_BRUTA, DEZ_2025)).isEqualByComparingTo(mi(67_253 + 316_772));
        assertThat(value(v, FinancialMetric.PL, DEZ_2025)).isEqualByComparingTo(mi(417_587));
        assertThat(value(v, FinancialMetric.PL_NAO_CONTROLADORES, DEZ_2025)).isEqualByComparingTo(mi(1_801));
        assertThat(value(v, FinancialMetric.FCO, DEZ_2025)).isEqualByComparingTo(mi(200_333));
        assertThat(value(v, FinancialMetric.JCP_DECLARADO, DEZ_2025)).isEqualByComparingTo(mi(30_682));
        assertThat(value(v, FinancialMetric.DIVIDENDOS_DECLARADOS, DEZ_2025)).isEqualByComparingTo(mi(10_554));
        // LPA nao leva a escala MIL
        assertThat(value(v, FinancialMetric.LPA_ON, DEZ_2025)).isEqualByComparingTo("8.54");
        assertThat(value(v, FinancialMetric.LPA_PN, DEZ_2025)).isEqualByComparingTo("8.54");
        // o balanco e posicao: sem inicio de periodo
        assertThat(v).filteredOn(x -> x.metric() == FinancialMetric.PL).allMatch(x -> x.periodStart() == null);
        assertThat(d.capital().total()).isEqualTo(12_888_732_761L);
    }

    @Test
    @DisplayName("Bancos: lucro e PL achados pela descricao (Itau 3.09/2.08, BB 3.11/2.07); sem caixa e divida")
    void bancos() {
        var itau = metrics(doc(dfp, "60872504000123", DEZ_2025));
        assertThat(FinancialMetricRules.layoutOf(basis(doc(dfp, "60872504000123", DEZ_2025))))
                .isEqualTo(StatementLayout.BANCO);
        assertThat(value(itau, FinancialMetric.LUCRO_CONTROLADORA, DEZ_2025)).isEqualByComparingTo(mi(44_857));
        assertThat(value(itau, FinancialMetric.PL, DEZ_2025)).isEqualByComparingTo(mi(215_076));
        assertThat(value(itau, FinancialMetric.PL_NAO_CONTROLADORES, DEZ_2025)).isEqualByComparingTo(mi(10_575));
        assertThat(value(itau, FinancialMetric.DIVIDENDOS_DECLARADOS, DEZ_2025)).isEqualByComparingTo(mi(33_704));
        assertThat(value(itau, FinancialMetric.LPA_ON, DEZ_2025)).isEqualByComparingTo("4.05");
        assertThat(value(itau, FinancialMetric.CAIXA, DEZ_2025)).isNull();
        assertThat(value(itau, FinancialMetric.DIVIDA_BRUTA, DEZ_2025)).isNull();
        assertThat(value(itau, FinancialMetric.EBIT, DEZ_2025)).isNull();
        // a versao 2 da DFP do Itau substitui a 1
        assertThat(doc(dfp, "60872504000123", DEZ_2025).version()).isEqualTo(2);

        var bb = metrics(doc(dfp, "00000000000191", DEZ_2025));
        assertThat(value(bb, FinancialMetric.LUCRO_LIQUIDO, DEZ_2025)).isEqualByComparingTo("16781938000");
        assertThat(value(bb, FinancialMetric.LUCRO_CONTROLADORA, DEZ_2025)).isEqualByComparingTo("13698124000");
        assertThat(value(bb, FinancialMetric.PL, DEZ_2025)).isEqualByComparingTo("193567416000");
        assertThat(value(bb, FinancialMetric.JCP_DECLARADO, DEZ_2025)).isEqualByComparingTo("5219106000");
        assertThat(value(bb, FinancialMetric.FCO, DEZ_2025)).isEqualByComparingTo("159254089000");
    }

    @Test
    @DisplayName("BB em 2015 (3.09/2.08): as mesmas regras acham lucro e PL")
    void bb2015() {
        LocalDate dez2015 = LocalDate.of(2015, 12, 31);
        var bb = metrics(doc(dfp2015, "00000000000191", dez2015));
        assertThat(value(bb, FinancialMetric.LUCRO_CONTROLADORA, dez2015)).isEqualByComparingTo("14069582000");
        assertThat(value(bb, FinancialMetric.PL, dez2015)).isEqualByComparingTo("86229994000");
        assertThat(value(bb, FinancialMetric.PL_NAO_CONTROLADORES, dez2015)).isEqualByComparingTo("3672743000");
        // antes de 2020 nao ha composicao do capital no DFP
        assertThat(doc(dfp2015, "00000000000191", dez2015).capital()).isNull();
    }

    @Test
    @DisplayName("IRB: controladora e nao controladores informados como 0 -> lucro inteiro da controladora")
    void seguradora() {
        Document irb = doc(dfp, "33376989000191", DEZ_2025);
        assertThat(FinancialMetricRules.layoutOf(basis(irb))).isEqualTo(StatementLayout.SEGURADORA);
        assertThat(irb.version()).isEqualTo(3);
        var v = metrics(irb);
        assertThat(value(v, FinancialMetric.LUCRO_LIQUIDO, DEZ_2025)).isEqualByComparingTo("390945000");
        assertThat(value(v, FinancialMetric.LUCRO_CONTROLADORA, DEZ_2025)).isEqualByComparingTo("390945000");
        assertThat(value(v, FinancialMetric.PL, DEZ_2025)).isEqualByComparingTo("5282690000");
        assertThat(value(v, FinancialMetric.EBIT, DEZ_2025)).isEqualByComparingTo("559916000");
    }

    @Test
    @DisplayName("Telebras so publica individual: vale o individual (D6), lucro sem quebra = controladora")
    void individual() {
        Document d = doc(dfp, "00336701000104", DEZ_2025);
        assertThat(d.consolidated()).isEmpty();
        assertThat(d.individual()).isNotEmpty();
        var v = metrics(d);
        assertThat(value(v, FinancialMetric.LUCRO_CONTROLADORA, DEZ_2025))
                .isEqualByComparingTo(value(v, FinancialMetric.LUCRO_LIQUIDO, DEZ_2025));
        assertThat(value(v, FinancialMetric.PL, DEZ_2025)).isNotNull();
    }

    @Test
    @DisplayName("Vivara em escala UNIDADE: valor como veio, sem multiplicar por mil")
    void escalaUnidade() {
        Document d = doc(dfp, "33839910000111", DEZ_2025);
        CvmStatements.Line pl = basis(d).stream()
                .filter(l -> l.statement() == CvmStatements.Statement.BPP && l.key().startsWith("patrimonio liquido"))
                .findFirst().orElseThrow();
        assertThat(pl.thousands()).isFalse();
        assertThat(value(metrics(d), FinancialMetric.PL, DEZ_2025)).isEqualByComparingTo(pl.value());
    }

    @Test
    @DisplayName("ITR: o DRE traz o trimestre e o acumulado; exercicio em fevereiro (Camil) tem as proprias datas")
    void itr() {
        LocalDate jun = LocalDate.of(2026, 6, 30);
        var v = metrics(doc(itr, "33000167000101", jun));
        var lucros = v.stream().filter(x -> x.metric() == FinancialMetric.LUCRO_CONTROLADORA && x.periodEnd().equals(jun))
                .map(FinancialMetricRules.Value::periodStart).sorted().toList();
        assertThat(lucros).containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));
        // DVA e DFC no ITR so no acumulado
        assertThat(v).filteredOn(x -> x.metric() == FinancialMetric.FCO)
                .extracting(FinancialMetricRules.Value::periodStart).containsExactly(LocalDate.of(2026, 1, 1));

        List<LocalDate> camil = itr.keySet().stream().filter(k -> k.cnpj().equals("64904295000103"))
                .map(CvmStatements.DocKey::referenceDate).sorted().toList();
        assertThat(camil).isNotEmpty().allMatch(dt -> dt.getMonthValue() % 3 == 2);
    }
}
