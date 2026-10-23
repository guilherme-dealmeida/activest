package com.primevest.adm.domain.fundamentals;

import com.primevest.domain.issuer.CompanyQuarter;
import com.primevest.domain.issuer.CompanyQuarterRepository;
import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmFixtures;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * F5 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: importacao de DFP/ITR com os recortes reais
 * (DFP 2025 e ITR 2026 de 8 companhias, DFP 2015 de BB e Petrobras, FRE 2025 e 2015).
 */
class FinancialStatementImportIT extends IntegrationTestBase {

    @TempDir
    static Path tmp;

    @MockBean
    CvmOpenDataClient cvm;

    @Autowired
    FinancialStatementImportService service;

    @Autowired
    CompanyQuarterRepository quarters;

    @Autowired
    CompanyQuarterService quarterService;

    static final String[][] COMPANIES = {
        {"33000167000101", "PETROBRAS"}, {"60872504000123", "ITAU UNIBANCO"}, {"00000000000191", "BANCO DO BRASIL"},
        {"33376989000191", "IRB"}, {"89637490000145", "KLABIN"}, {"00336701000104", "TELEBRAS"},
        {"33839910000111", "VIVARA"}, {"64904295000103", "CAMIL"}};

    @BeforeEach
    void scenario() {
        // os testes de emissores apagam issuers; aqui cada companhia e garantida pelo CNPJ
        for (String[] c : COMPANIES) {
            jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES ('COMPANY', ?, ?) ON CONFLICT (cnpj) DO NOTHING",
                    c[0], c[1]);
        }
        when(cvm.yearly(any(), anyInt())).thenReturn(Optional.empty());
        when(cvm.yearly(CvmDataset.DFP, 2025)).thenReturn(Optional.of(CvmFixtures.zip("dfp_cia_aberta_2026", 2025, tmp)));
        when(cvm.yearly(CvmDataset.ITR, 2026)).thenReturn(Optional.of(CvmFixtures.zip("itr_cia_aberta_2026", 2026, tmp)));
        when(cvm.yearly(CvmDataset.DFP, 2015)).thenReturn(Optional.of(CvmFixtures.zip("dfp2015", 2015, tmp)));
        when(cvm.yearly(CvmDataset.FRE, 2025)).thenReturn(Optional.of(CvmFixtures.zip("fre_cia_aberta_2026", 2025, tmp)));
        when(cvm.yearly(CvmDataset.FRE, 2015)).thenReturn(Optional.of(CvmFixtures.zip("fre2015", 2015, tmp)));
    }

    private Map<String, Object> quantity(String cnpj, String docType, String ref) {
        return jdbc.queryForMap("""
            SELECT s.source, s.total_shares, s.total_treasury, s.scale_factor, s.scale_status, s.scale_check
            FROM share_counts s JOIN cvm_documents d ON d.id = s.document_id JOIN issuers i ON i.id = d.issuer_id
            WHERE i.cnpj = ? AND d.doc_type = ? AND d.reference_date = CAST(? AS DATE)""", cnpj, docType, ref);
    }

    private BigDecimal metrica(String cnpj, String docType, String ref, String metric) {
        return jdbc.queryForObject("""
            SELECT v.value FROM cvm_statement_values v JOIN cvm_documents d ON d.id = v.document_id
            JOIN issuers i ON i.id = d.issuer_id
            WHERE i.cnpj = ? AND d.doc_type = ? AND d.reference_date = CAST(? AS DATE) AND v.metric = ?
            ORDER BY v.period_start NULLS FIRST LIMIT 1""", BigDecimal.class, cnpj, docType, ref, metric);
    }

    private int documentList(String docType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cvm_documents WHERE doc_type = ?", Integer.class, docType);
    }

    @Test
    @DisplayName("DFP 2025 e ITR 2026: metricas, quantidade de acoes com escala conferida pelo FRE e layout do emissor")
    void imports() {
        FinancialStatementImportService.Result r = service.importYears(2025, 2026, false, "teste");

        assertThat(documentList("DFP")).isEqualTo(8);
        assertThat(documentList("ITR")).isPositive();
        assertThat(r.created()).isEqualTo(r.documents()).isEqualTo(documentList("DFP") + documentList("ITR"));

        assertThat(metrica("33000167000101", "DFP", "2025-12-31", "LUCRO_CONTROLADORA"))
                .isEqualByComparingTo("110129000000");
        assertThat(metrica("33000167000101", "DFP", "2025-12-31", "LPA_ON")).isEqualByComparingTo("8.54");
        assertThat(metrica("60872504000123", "DFP", "2025-12-31", "PL")).isEqualByComparingTo("215076000000");

        // Itau: 11.026.869 no arquivo, 11.026.869.192 no FRE -> fator 1.000
        assertThat(quantity("60872504000123", "DFP", "2025-12-31"))
                .containsEntry("source", "COMPOSICAO").containsEntry("total_shares", 11_026_869L)
                .containsEntry("total_treasury", 345L).containsEntry("scale_factor", 1000)
                .containsEntry("scale_status", "OK").containsEntry("scale_check", "FRE");
        assertThat(quantity("00000000000191", "DFP", "2025-12-31"))
                .containsEntry("scale_factor", 1).containsEntry("scale_check", "FRE");
        // Camil fecha o exercicio em fevereiro
        assertThat(quantity("64904295000103", "DFP", "2025-02-28"))
                .containsEntry("total_shares", 350_000_000L).containsEntry("scale_factor", 1);

        assertThat(jdbc.queryForObject("SELECT consolidated FROM cvm_documents d JOIN issuers i ON i.id = d.issuer_id "
                + "WHERE i.cnpj = '00336701000104'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForMap("""
            SELECT (SELECT statement_layout FROM issuers WHERE cnpj = '60872504000123') AS itau,
                   (SELECT statement_layout FROM issuers WHERE cnpj = '33376989000191') AS irb,
                   (SELECT statement_layout FROM issuers WHERE cnpj = '33000167000101') AS petr"""))
                .containsEntry("itau", "BANCO").containsEntry("irb", "SEGURADORA").containsEntry("petr", "COMERCIAL");
        assertThat(r.shareCounts()).doesNotContainKey("COMPOSICAO_REVISAR");
    }

    @Test
    @DisplayName("Idempotente: segunda rodada nao regrava; versao nova (reapresentacao) substitui; force reprocessa")
    void idempotente() {
        FinancialStatementImportService.Result first = service.importYears(2025, 2026, false, "teste");
        Integer amounts = jdbc.queryForObject("SELECT COUNT(*) FROM cvm_statement_values", Integer.class);

        FinancialStatementImportService.Result second = service.importYears(2025, 2026, false, "teste");
        assertThat(second.unchanged()).isEqualTo(first.documents());
        assertThat(second.created() + second.updated()).isZero();

        // como se a versao gravada fosse anterior a do arquivo
        jdbc.update("UPDATE cvm_documents SET version = 0 WHERE doc_type = 'DFP' AND issuer_id = "
                + "(SELECT id FROM issuers WHERE cnpj = '33000167000101')");
        FinancialStatementImportService.Result third = service.importYears(2025, 2025, false, "teste");
        assertThat(third.updated()).isEqualTo(1);

        FinancialStatementImportService.Result forced = service.importYears(2025, 2026, true, "teste");
        assertThat(forced.updated()).isEqualTo(first.documents());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cvm_statement_values", Integer.class)).isEqualTo(amounts);
    }

    @Test
    @DisplayName("Antes de 2020 (DFP 2015): quantidade de acoes do historico de capital do FRE, sem tesouraria")
    void before2020() {
        service.importYears(2015, 2015, false, "teste");

        Map<String, Object> bb = quantity("00000000000191", "DFP", "2015-12-31");
        assertThat(bb).containsEntry("source", "FRE").containsEntry("scale_factor", 1).containsEntry("scale_status", "OK");
        assertThat(bb.get("total_treasury")).isNull();
        assertThat((Long) bb.get("total_shares")).isGreaterThan(2_000_000_000L);
        assertThat(metrica("00000000000191", "DFP", "2015-12-31", "LUCRO_CONTROLADORA"))
                .isEqualByComparingTo("14069582000");
        assertThat(quantity("33000167000101", "DFP", "2015-12-31"))
                .containsEntry("source", "FRE").containsEntry("total_shares", 13_044_496_930L);
    }

    @Test
    @DisplayName("Sem conferencia (REVISAR): o documento validado mais proximo decide a escala; erro na fonte fica")
    void neighbor() {
        service.importYears(2025, 2026, false, "teste");
        // como companhia recem-listada: DFP 2025 do Itau sem FRE nem LPA que feche
        String itauDfp = "(SELECT d.id FROM cvm_documents d JOIN issuers i ON i.id = d.issuer_id "
                + "WHERE i.cnpj = '60872504000123' AND d.doc_type = 'DFP')";
        jdbc.update("UPDATE share_counts SET scale_factor = NULL, scale_status = 'REVISAR', scale_check = NULL "
                + "WHERE document_id = " + itauDfp);
        // e um erro de digitacao na fonte (um digito a mais), como o Grupo Mateus em 06/2022
        String petrDfp = "(SELECT d.id FROM cvm_documents d JOIN issuers i ON i.id = d.issuer_id "
                + "WHERE i.cnpj = '33000167000101' AND d.doc_type = 'DFP')";
        jdbc.update("UPDATE share_counts SET total_shares = total_shares * 10, scale_factor = NULL, "
                + "scale_status = 'REVISAR', scale_check = NULL WHERE document_id = " + petrDfp);

        assertThat(service.resolveByNeighbors()).isEqualTo(1);

        // o ITR de 03/2026 do Itau (11.026.869, validado pelo FRE com fator 1.000) e o vizinho
        assertThat(quantity("60872504000123", "DFP", "2025-12-31"))
                .containsEntry("scale_factor", 1000).containsEntry("scale_status", "OK")
                .containsEntry("scale_check", "VIZINHO");
        assertThat(quantity("33000167000101", "DFP", "2025-12-31"))
                .containsEntry("scale_status", "REVISAR").containsEntry("scale_factor", null);
    }

    @Test
    @DisplayName("F6: serie trimestral recalculada ao fim da carga — trimestre isolado, 12 meses e posicao")
    void quarterlySeries() {
        FinancialStatementImportService.Result r = service.importYears(2025, 2026, false, "teste");
        Integer dates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT DISTINCT issuer_id, reference_date FROM cvm_documents) x", Integer.class);
        assertThat(r.quarters()).isEqualTo(dates);

        Long petr = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, "33000167000101");
        List<CompanyQuarter> serie = quarters.findByIssuerIdOrderByPeriodEnd(petr);
        assertThat(serie).extracting(CompanyQuarter::getPeriodEnd).containsExactly(
                LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31"), LocalDate.parse("2026-06-30"));

        CompanyQuarter ten = serie.get(0);
        assertThat(ten.getNetIncomeParentTtm()).isEqualByComparingTo("110129000000");  // anual do DFP
        assertThat(ten.getNetIncomeParentQ()).isNull();                                 // sem o ITR de 09/2025
        assertThat(ten.getDividendsDeclaredTtm()).isEqualByComparingTo("41236000000"); // 10.554 + 30.682 mi
        assertThat(ten.getEquityParent()).isEqualByComparingTo("415786000000");         // 417.587 - 1.801 mi
        assertThat(ten.getSharesOutstanding()).isPositive();
        assertThat(ten.getFiscalYearStart()).isEqualTo("2025-01-01");

        CompanyQuarter mar = serie.get(1);
        CompanyQuarter jun = serie.get(2);
        assertThat(mar.getRevenueQ()).isEqualByComparingTo("123686000000");            // acumulado do 1T
        assertThat(jun.getRevenueQ()).isEqualByComparingTo("169530000000");            // coluna de 3 meses
        assertThat(jun.getNetIncomeQ()).isEqualByComparingTo("52495000000");
        // DFC so acumulada: 2T = 6 meses - 3 meses
        BigDecimal fco6 = metrica("33000167000101", "ITR", "2026-06-30", "FCO");
        BigDecimal fco3 = metrica("33000167000101", "ITR", "2026-03-31", "FCO");
        assertThat(jun.getFcoQ()).isEqualByComparingTo(fco6.subtract(fco3));
        assertThat(jun.getRevenueTtm()).isNull();                                       // falta o 3T de 2025
        assertThat(jun.getSourceDocId()).isNotNull();

        // recalcular de novo da o mesmo
        CompanyQuarterService.Result again = quarterService.rebuild("teste");
        assertThat(again.quarters()).isEqualTo(r.quarters());
    }

    private long document(long issuer, String ref, String received, long total, Integer factor, String status) {
        Long id = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, received_at, consolidated)
                VALUES (?, 'ITR', CAST(? AS DATE), 1, CAST(? AS DATE), true) RETURNING id""",
                Long.class, issuer, ref, received);
        jdbc.update("""
                INSERT INTO share_counts (document_id, source, total_shares, scale_factor, scale_status, scale_check)
                VALUES (?, 'COMPOSICAO', ?, ?, ?, ?)""", id, total, factor, status, factor == null ? null : "FRE");
        return id;
    }

    @Test
    @DisplayName("Grupamento entre dois balancos: a quantidade em REVISAR fecha pelo evento registrado (ate a entrega)")
    void reverseSplitBetweenStatements() {
        long vivara = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = '33839910000111'", Long.class);
        jdbc.update("UPDATE assets SET issuer_id = ? WHERE ticker = 'VIVA3'", vivara);
        long assetId = jdbc.queryForObject("SELECT id FROM assets WHERE ticker = 'VIVA3'", Long.class);
        document(vivara, "2026-03-31", "2026-05-10", 48_800_409L, 1, "OK");
        // grupamento 10:1 em 10/06: ITR de junho com 4,88 mi (Viver 2026, real)
        long june = document(vivara, "2026-06-30", "2026-08-10", 4_880_040L, null, "REVISAR");
        // grupamento 5:1 em 15/10, depois do fim do trimestre e antes da entrega: ja vem grupado
        long september = document(vivara, "2026-09-30", "2026-11-10", 976_008L, null, "REVISAR");
        // numero que nenhum evento explica (erro na fonte): continua REVISAR
        long december = document(vivara, "2026-12-31", "2027-03-10", 9_760_080L, null, "REVISAR");
        jdbc.update("""
                INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
                VALUES (?, 'GRUPAMENTO', DATE '2026-06-10', 0.1, 'B3'), (?, 'GRUPAMENTO', DATE '2026-10-15', 0.2, 'B3')""",
                assetId, assetId);

        assertThat(service.resolveByStockEvents()).isEqualTo(2);

        assertThat(jdbc.queryForMap("SELECT scale_factor, scale_status, scale_check FROM share_counts WHERE document_id = ?",
                june)).containsEntry("scale_factor", 1).containsEntry("scale_status", "OK").containsEntry("scale_check", "EVENTO");
        assertThat(jdbc.queryForObject("SELECT scale_check FROM share_counts WHERE document_id = ?", String.class, september))
                .isEqualTo("EVENTO");
        assertThat(jdbc.queryForObject("SELECT scale_status FROM share_counts WHERE document_id = ?", String.class, december))
                .isEqualTo("REVISAR");
    }
}
