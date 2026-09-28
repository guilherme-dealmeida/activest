package com.primevest.adm.integration.b3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FNET refeito na F8 (PLANO_EMPRESAS_BALANCOS.md, 1.5): avisos reais do cache
 * {@code data/fnet} nos dois formatos de XML, e o cache em disco no lugar do download.
 */
class FnetClientTest {

    @TempDir
    Path tmp;

    private static FnetNotice aviso(long id) {
        return FnetClient.parseNotice(B3Fixtures.read("fnet/" + id + ".xml"));
    }

    @Test
    @DisplayName("Formato antigo (2017): valor em ValorProventoCota, ISIN e ticker da cota nos dados gerais")
    void formatoAntigo() {
        FnetNotice n = aviso(19693);

        assertThat(n.fundCnpj()).isEqualTo("17554274000125");
        assertThat(n.proventos()).singleElement().satisfies(p -> {
            assertThat(p.ticker()).isEqualTo("VISC11");
            assertThat(p.isin()).isEqualTo("BRVISCCTF005");
            assertThat(p.amortization()).isFalse();
            assertThat(p.dataBase()).isEqualTo(LocalDate.of(2017, 11, 30));
            assertThat(p.paymentDate()).isEqualTo(LocalDate.of(2017, 12, 14));
            assertThat(p.value()).isEqualByComparingTo("0.61");
            assertThat(p.incomeTaxExempt()).isTrue();
        });
    }

    @Test
    @DisplayName("Formato antigo com BOM e ticker com B no fim (WPLZ11B e o WPLZ11)")
    void tickerComB() {
        FnetNotice n = aviso(10021);

        assertThat(n.fundCnpj()).isEqualTo("09326861000139");
        assertThat(n.proventos()).singleElement().satisfies(p -> {
            assertThat(p.ticker()).isEqualTo("WPLZ11");
            assertThat(p.value()).isEqualByComparingTo("0.30323276450236");
        });
    }

    @Test
    @DisplayName("CNPJ pontuado vira so digitos")
    void cnpjPontuado() {
        FnetNotice n = aviso(7078);

        assertThat(n.fundCnpj()).isEqualTo("11274415000170");
        assertThat(n.proventos()).singleElement().satisfies(p -> {
            assertThat(p.ticker()).isEqualTo("MAXR11");
            assertThat(p.value()).isEqualByComparingTo("6.712812896");
        });
    }

    @Test
    @DisplayName("Formato atual: um provento por cota, recibo incluido (quem descarta e o servico)")
    void formatoAtualComRecibo() {
        FnetNotice n = aviso(1002663);

        assertThat(n.fundCnpj()).isEqualTo("32754734000152");
        assertThat(n.proventos()).extracting(FnetProvento::ticker).containsExactly("PLAG11", "PLAG13");
        assertThat(n.proventos()).allSatisfy(p -> assertThat(p.value()).isEqualByComparingTo("0.48"));
    }

    @Test
    @DisplayName("Data-base digitada errada (2108) chega como veio: a regra de descarte e do servico")
    void dataBaseErrada() {
        assertThat(aviso(36458).proventos()).singleElement()
                .satisfies(p -> assertThat(p.dataBase()).isEqualTo(LocalDate.of(2108, 11, 30)));
    }

    @Test
    @DisplayName("Cache: aviso em data/fnet e lido do disco; sem arquivo e sem rede, fica para a proxima")
    void cache() throws Exception {
        FnetClient client = new FnetClient(WebClient.builder(), "http://localhost:0", 1, 2, 0, 2, 1, 1,
                tmp.toString(), new ObjectMapper());
        Path dir = client.cacheDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("19693.xml"), B3Fixtures.read("fnet/19693.xml"), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("5.xml"), "nao e xml <", StandardCharsets.UTF_8);

        FnetClient.Batch b = client.incomeNotices(List.of(19693L, 5L, 999L));

        assertThat(b.notices()).containsOnlyKeys(19693L);
        assertThat(b.unreadable()).containsExactly(5L);
        assertThat(b.failed()).containsExactly(999L);
        assertThat(b.downloaded()).isZero();
        assertThat(dir.resolve("999.xml")).doesNotExist();
    }
}
