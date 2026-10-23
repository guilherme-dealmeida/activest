package com.activest.adm.integration.cvm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leitura do "Relatorio Proventos" da CVM (docs/planos/PLANO_CARGA_INICIAL.md, 5.2 — P3). Textos como o PDFBox
 * extrai dos PDFs reais do RAD (Petrobras 11/12/2025, Cemig 18/12/2025, Taesa 11/11/2025).
 */
class CvmDividendReportTest {

    private static final String PETROBRAS = """
            Provento
            Denominação Social Protocolo IPE
            PETROLEO BRASILEIRO S.A. 11/12/2025  009512IPE111220250105359316-03
            PETROBRAS Comunicado ao Mercado  Outros Comunicados Não
            Considerados Fatos Relevantes
            Ato Societário Outros
            Reunião de Diretoria
            Data Aprovação Ultimo dia de negociação com Direitos
            11/12/2025 22/12/2025
            Protocolo Provento Versão Data Envio
            1455971 1 11/12/2025
            Código ISIN Valor Bruto Período Execício Haverá Forma de Data
            (R$/Unidade) Base Social Atualização Pagamento Pagamento
            BRPETRACN 0,4716037800 Anual 2025 Não A Vista 20/02/2026
            OR9 0
            BRPETRACN 0,4716037800 Anual 2025 Não A Vista 20/02/2026
            PR6 0
            BRPETRACN 0,2964214361 Anual 2025 Não A Vista 20/03/2026
            PR6 1
            Parcelamento
            Dia de Pagamento das Parcelas Valor Bruto (R$/Unidade)
            """;

    private static final String CEMIG = """
            Data Aprovação Ultimo dia de negociação com Direitos
            18/12/2025 23/12/2025
            Código ISIN Valor Bruto Período Execício Haverá Forma de Data
            (R$/Unidade) Base Social Atualização Pagamento Pagamento
            BRCMIGACN 0,2368026322 Anual 2025 Não Parcelado
            OR6 8
            BRCMIGACN 0,2368026322 Anual 2025 Não Parcelado
            PR3 8
            Parcelamento
            Dia de Pagamento das Parcelas Valor Bruto (R$/Unidade)
            30/12/2026 0.11840131614
            30/06/2026 0.11840131614
            30/06/2026 0.11840131614
            0.11840131614
            """;

    private static final String TAESA = """
            Data Aprovação Ultimo dia de negociação com Direitos
            11/11/2025 14/11/2025
            BRTAEEACN 0,1398024111 3º Trimestre 2025 Não A Vista 28/01/2026
            OR9 7
            BRTAEECDA 0,5189532131 3º Trimestre 2025 Não A Vista 28/01/2026
            M10 4
            """;

    @Test
    @DisplayName("A vista: ISIN e valor que quebram na coluna sao remontados; data-com e o ultimo dia com direitos")
    void spot() {
        CvmDividendReport.Report r = CvmDividendReport.parse(PETROBRAS);

        assertThat(r.approvedOn()).isEqualTo(LocalDate.of(2025, 12, 11));
        assertThat(r.dataCom()).isEqualTo(LocalDate.of(2025, 12, 22));
        assertThat(r.items()).hasSize(3);
        assertThat(r.items().get(0).isin()).isEqualTo("BRPETRACNOR9");
        assertThat(r.items().get(0).value()).isEqualByComparingTo("0.47160378000");
        assertThat(r.items().get(0).paymentDate()).isEqualTo(LocalDate.of(2026, 2, 20));
        assertThat(r.items().get(2).value()).isEqualByComparingTo(new BigDecimal("0.29642143611"));
        assertThat(r.items()).noneMatch(CvmDividendReport.Item::installments);
    }

    @Test
    @DisplayName("Parcelado: as parcelas sao repartidas entre as especies, em ordem de pagamento; a definir fica sem data")
    void inInstallments() {
        CvmDividendReport.Report r = CvmDividendReport.parse(CEMIG);

        assertThat(r.items()).hasSize(2).allMatch(CvmDividendReport.Item::installments);
        assertThat(r.items().get(0).isin()).isEqualTo("BRCMIGACNOR6");
        assertThat(r.items().get(0).parcels()).extracting(CvmDividendReport.Parcel::paymentDate)
                .containsExactly(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 12, 30));
        assertThat(r.items().get(1).parcels()).extracting(CvmDividendReport.Parcel::paymentDate)
                .containsExactly(LocalDate.of(2026, 6, 30), null);
    }

    @Test
    @DisplayName("Unit e ISIN com final alfanumerico (Taesa)")
    void unit() {
        CvmDividendReport.Report r = CvmDividendReport.parse(TAESA);

        assertThat(r.items()).extracting(CvmDividendReport.Item::isin).containsExactly("BRTAEEACNOR9", "BRTAEECDAM10");
        assertThat(r.items().get(1).value()).isEqualByComparingTo("0.51895321314");
    }

    private static final String GERDAU_2022 = """
            Provento Dividendo 1
            Data Aprovação Ultimo dia de negociação com Direitos
            11/8/2022 12:00:00 AM 11/21/2022 12:00:00 AM
            Protocolo Provento Versão Data Envio
            1030028 1 11/9/2022 8:19:42 AM
            Código ISIN Valor Bruto Período Execício Haverá Forma de Data
            BRGGBRAC 1.7300000000 1 2022 N V 14/12/2022
            NOR1 0
            BRSTENDBS 450.34348900 8 2022 N V 12/17/2022
            """;

    @Test
    @DisplayName("Formato de 2022: cabecalho em ingles com hora, valor com ponto, N/V, pagamento dd/MM ou MM/dd")
    void format2022() {
        CvmDividendReport.Report r = CvmDividendReport.parse(GERDAU_2022);

        assertThat(r.approvedOn()).isEqualTo(LocalDate.of(2022, 11, 8));
        assertThat(r.dataCom()).isEqualTo(LocalDate.of(2022, 11, 21));
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).isin()).isEqualTo("BRGGBRACNOR1");
        assertThat(r.items().get(0).value()).isEqualByComparingTo("1.73");
        assertThat(r.items().get(0).paymentDate()).isEqualTo(LocalDate.of(2022, 12, 14));
        assertThat(CvmDividendReport.paymentDate("12/17/2022", r.dataCom())).isEqualTo(LocalDate.of(2022, 12, 17));
        assertThat(CvmDividendReport.paymentDate("05/12/2022", r.dataCom())).isEqualTo(LocalDate.of(2022, 12, 5));
    }

    @Test
    @DisplayName("Texto sem o formulario: sem data-com e sem proventos")
    void empty() {
        CvmDividendReport.Report r = CvmDividendReport.parse("Comunicado ao mercado qualquer");
        assertThat(r.dataCom()).isNull();
        assertThat(r.items()).isEmpty();
    }

    @Test
    @DisplayName("Marcador de 'a definir' (01/01/0001, 31/12/9999) ou data fora da janela: sem data")
    void invalidDate() {
        LocalDate dataCom = LocalDate.of(2023, 12, 27);
        assertThat(CvmDividendReport.paymentDate("01/01/0001", dataCom)).isNull();
        assertThat(CvmDividendReport.paymentDate("31/12/9999", dataCom)).isNull();
        assertThat(CvmDividendReport.paymentDate("31/12/2998", dataCom)).isNull();
        assertThat(CvmDividendReport.paymentDate("15/12/2023", dataCom)).isNull();
        assertThat(CvmDividendReport.paymentDate("27/12/2033", dataCom)).isEqualTo(LocalDate.of(2033, 12, 27));
        assertThat(CvmDividendReport.paymentDate("28/12/2033", dataCom)).isNull();
        assertThat(CvmDividendReport.paymentDate("01/01/0001", null)).isNull();
    }
}
