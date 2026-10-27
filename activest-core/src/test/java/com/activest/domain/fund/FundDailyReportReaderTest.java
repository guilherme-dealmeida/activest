package com.activest.domain.fund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Os 3 layouts do informe diario da CVM, achados pelo cabecalho (PLANO_FUNDOS_INVESTIMENTO.md, 4.4 e C5). */
class FundDailyReportReaderTest {

    private static byte[] zip(String... nameAndContent) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < nameAndContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameAndContent[i]));
                z.write(nameAndContent[i + 1].getBytes(StandardCharsets.ISO_8859_1));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static List<FundDailyReportReader.Row> read(byte[] zip, String key) throws Exception {
        List<FundDailyReportReader.Row> rows = new ArrayList<>();
        FundDailyReportReader.read(new ByteArrayInputStream(zip), key == null ? null : key::equals, rows::add);
        return rows;
    }

    @Test
    @DisplayName("Anual com meses em layouts diferentes (so CNPJ_FUNDO e TP_FUNDO;CNPJ_FUNDO) no mesmo ZIP")
    void annualMixedLayouts() throws Exception {
        byte[] z = zip(
            "inf_diario_fi_201001.csv", "CNPJ_FUNDO;DT_COMPTC;VL_TOTAL;VL_QUOTA;VL_PATRIM_LIQ;CAPTC_DIA;RESG_DIA;NR_COTST\n"
                + "03.926.431/0001-71;2010-01-04;4161520408.61;3.162881000000;4150194182.99;2145755.73;0.00;1\n"
                + "00.000.684/0001-21;2010-01-04;1;2;3;0;0;5\n",
            "inf_diario_fi_201002.csv", "TP_FUNDO;CNPJ_FUNDO;DT_COMPTC;VL_TOTAL;VL_QUOTA;VL_PATRIM_LIQ;CAPTC_DIA;RESG_DIA;NR_COTST\n"
                + "FI;03.926.431/0001-71;2010-02-01;1;3.17;3;0;0;1\n");
        List<FundDailyReportReader.Row> rows = read(z, "03926431000171");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2010, 1, 4));
        assertThat(rows.get(0).quote()).isEqualByComparingTo("3.162881");
        assertThat(rows.get(0).fundType()).isNull();
        assertThat(rows.get(0).holders()).isEqualTo(1);
        assertThat(rows.get(1).fundType()).isEqualTo("FI");
        assertThat(read(z, null)).hasSize(3);
    }

    @Test
    @DisplayName("Layout da RCVM 175: TP_FUNDO_CLASSE;CNPJ_FUNDO_CLASSE;ID_SUBCLASSE — subclasse vira chave propria")
    void rcvm175() throws Exception {
        byte[] z = zip("inf_diario_fi_202610.csv",
            "TP_FUNDO_CLASSE;CNPJ_FUNDO_CLASSE;ID_SUBCLASSE;DT_COMPTC;VL_TOTAL;VL_QUOTA;VL_PATRIM_LIQ;CAPTC_DIA;RESG_DIA;NR_COTST\n"
                + "CLASSES - FIF;03.926.431/0001-71;;2026-10-15;4957221190.36;9.680832831000;4951303835.49;0.00;2375861.99;1\n"
                + "CLASSES - FIF;11.111.111/0001-11;MZMRC1747322915;2026-10-15;10;1.5;10;0;0;3\n");
        List<FundDailyReportReader.Row> rows = read(z, null);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).key()).isEqualTo("03926431000171");
        assertThat(rows.get(0).quote()).isEqualByComparingTo("9.680832831");
        assertThat(rows.get(0).fundType()).isEqualTo("CLASSES - FIF");
        assertThat(rows.get(1).key()).isEqualTo("11111111000111-MZMRC1747322915");
        assertThat(rows.get(1).subclassId()).isEqualTo("MZMRC1747322915");
    }

    @Test
    @DisplayName("Cota utilizavel: positiva e abaixo de 10^10 (a coluna de preco); valor absurdo do informe e descartado")
    void usableQuote() {
        assertThat(FundDailyReportReader.usableQuote(new java.math.BigDecimal("9.680832831"))).isTrue();
        assertThat(FundDailyReportReader.usableQuote(new java.math.BigDecimal("9999999999.99999999"))).isTrue();
        assertThat(FundDailyReportReader.usableQuote(new java.math.BigDecimal("90335366100.00"))).isFalse();
        assertThat(FundDailyReportReader.usableQuote(java.math.BigDecimal.ZERO)).isFalse();
        assertThat(FundDailyReportReader.usableQuote(new java.math.BigDecimal("-1"))).isFalse();
        assertThat(FundDailyReportReader.usableQuote(null)).isFalse();
    }
}
