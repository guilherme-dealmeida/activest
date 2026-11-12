package com.activest.adm.integration.indices;

import com.activest.adm.integration.treasury.TreasuryPriceClient;
import com.activest.domain.marketindex.IndexPoint;
import com.activest.domain.marketindex.YieldCurve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** docs/planos/PLANO_JUROS_FUTUROS.md, J2: a fonte da curva le o arquivo do Tesouro e calcula cada prazo. */
class TreasuryCurveSourceTest {

    private static final String HEADER = "Tipo Titulo;Data Vencimento;Data Base;Taxa Compra Manha;Taxa Venda Manha;"
            + "PU Compra Manha;PU Venda Manha;PU Base Manha";

    @TempDir
    Path dir;

    private TreasuryCurveSource source(Path file, JdbcTemplate jdbc) {
        TreasuryPriceClient client = mock(TreasuryPriceClient.class);
        when(client.file()).thenReturn(file);
        return new TreasuryCurveSource(client, jdbc);
    }

    private Path csv(String... lines) throws Exception {
        Path f = dir.resolve("PrecoTaxaTesouroDireto.csv");
        Files.writeString(f, HEADER + "\n" + String.join("\n", lines) + "\n", StandardCharsets.ISO_8859_1);
        return f;
    }

    @Test
    @DisplayName("Prazo entre dois titulos sai por flat-forward; fora dos titulos o dia nao entra; outros tipos ignorados")
    void interpolates() throws Exception {
        Path f = csv(
                "Tesouro IPCA+;15/05/2029;10/11/2026;7,51;7,63;3990,06;3977,60;3977,60",
                "Tesouro IPCA+;15/05/2035;10/11/2026;7,71;7,83;2549,56;2524,76;2524,76",
                "Tesouro Selic;01/03/2031;10/11/2026;0,11;0,12;20180,58;20161,59;20161,59",
                "Tesouro IPCA+;15/05/2029;09/11/2026;7,50;7,62;3990,06;3977,60;3977,60");
        TreasuryCurveSource s = source(f, mock(JdbcTemplate.class));

        List<IndexPoint> real5 = s.year("REAL:5", 2026);
        assertThat(real5).hasSize(1);
        assertThat(real5.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(real5.get(0).value().doubleValue()).isBetween(7.57, 7.77);
        assertThat(s.year("REAL:20", 2026)).isEmpty();
        assertThat(s.year("REAL:5", 2025)).isEmpty();
    }

    @Test
    @DisplayName("Prefixada de 1 ano usa o CDI gravado do dia como ancora do curto (I4)")
    void cdiAnchor() throws Exception {
        Path f = csv("Tesouro Prefixado;01/01/2028;10/11/2026;14,08;14,20;861,13;859,65;859,65");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getDate(1)).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 11, 10)));
            when(rs.getBigDecimal(2)).thenReturn(new BigDecimal("0.055131"));
            h.processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(), any());

        List<IndexPoint> pre1 = source(f, jdbc).year("PRE:1", 2026);

        assertThat(pre1).hasSize(1);
        double cdi = YieldCurve.annualize(new BigDecimal("0.055131")).doubleValue();
        assertThat(pre1.get(0).value().doubleValue()).isBetween(14.14, cdi);
        assertThat(source(f, mock(JdbcTemplate.class)).year("PRE:1", 2026)).isEmpty(); // sem CDI, sem ancora
    }

    @Test
    @DisplayName("Sem o arquivo do Tesouro e fonte indisponivel, nao zero linhas")
    void missingFile() {
        TreasuryCurveSource s = source(dir.resolve("nao-existe.csv"), mock(JdbcTemplate.class));
        assertThatThrownBy(() -> s.year("PRE:1", 2026)).isInstanceOf(IndexSourceException.class)
                .hasMessageContaining("TREASURY_PRICES");
    }

    @Test
    @DisplayName("Mesmo titulo no mesmo vencimento: vale o sem cupom; taxa = media de compra e venda")
    void zeroCouponWins() throws Exception {
        Path f = csv("Tesouro Prefixado com Juros Semestrais;01/01/2031;10/11/2026;14,96;15,08;896,21;892,65;892,65",
                "Tesouro Prefixado;01/01/2031;10/11/2026;15,00;15,20;564,06;561,33;561,33",
                "Tesouro Prefixado;01/01/2029;10/11/2026;14,59;14,71;749,31;747,24;747,24");
        List<IndexPoint> pre3 = source(f, mock(JdbcTemplate.class)).year("PRE:3", 2026);
        assertThat(pre3).hasSize(1);
        // entre 14,65 (2029) e 15,10 (2031, o sem cupom), mais perto de 2029
        assertThat(pre3.get(0).value().doubleValue()).isBetween(14.65, 15.10).isCloseTo(14.8, within(0.15));
    }
}
