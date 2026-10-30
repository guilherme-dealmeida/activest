package com.activest.domain.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** F10 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: o arquivo em carga/data/logos e o estado — sem coluna no catalogo. */
class AssetLogosTest {

    @TempDir
    Path dir;

    private static Asset assetEntity(String ticker, String type) {
        return Asset.builder().ticker(ticker).assetType(AssetType.builder().name(type).build()).build();
    }

    @Test
    @DisplayName("Indexa a pasta: ticker → arquivo; o booleano da tela e nulo para tipo sem logo")
    void index() throws Exception {
        Files.writeString(dir.resolve("PETR4.svg"), "<svg/>");
        Files.writeString(dir.resolve("BTC.png"), "x");
        Files.writeString(dir.resolve("leia-me.txt"), "nao e logo");
        AssetLogos logos = new AssetLogos(dir.toString());

        assertThat(logos.reindex()).isEqualTo(2);
        assertThat(logos.hasLogo("petr4")).isTrue();
        assertThat(logos.hasLogo("VALE3")).isFalse();
        assertThat(logos.find("BTC")).get().satisfies(f -> assertThat(f.mediaType().toString()).isEqualTo("image/png"));
        assertThat(logos.find("VALE3")).isEmpty();
        assertThat(logos.logoFlag(assetEntity("PETR4", "ACAO"))).isTrue();
        assertThat(logos.logoFlag(assetEntity("VALE3", "ACAO"))).isFalse();
        assertThat(logos.logoFlag(assetEntity("TESOURO SELIC 2029", "TESOURO_DIRETO"))).isNull();
    }

    @Test
    @DisplayName("Quem grava avisa: o indice muda na hora; troca de extensao aponta o arquivo antigo")
    void storage() throws Exception {
        AssetLogos logos = new AssetLogos(dir.toString());
        logos.reindex();
        Files.writeString(dir.resolve("HGLG11.png"), "antigo");
        Files.writeString(dir.resolve("HGLG11.svg"), "<svg/>");

        logos.stored("HGLG11", "HGLG11.svg");

        assertThat(logos.hasLogo("HGLG11")).isTrue();
        assertThat(logos.otherFiles("HGLG11", "HGLG11.svg")).containsExactly(dir.resolve("HGLG11.png"));
        logos.stored("HGLG11", null);
        assertThat(logos.hasLogo("HGLG11")).isFalse();
    }

    @Test
    @DisplayName("Exterior (P4): o logo do par (tipo, ticker) vem da subpasta do tipo; META cripto fica na raiz")
    void foreignByType() throws Exception {
        Files.writeString(dir.resolve("META.png"), "cripto");
        Files.createDirectories(dir.resolve("STOCK"));
        Files.writeString(dir.resolve("STOCK").resolve("META.svg"), "<svg/>");
        Files.createDirectories(dir.resolve("ETF_INTERNACIONAL").resolve("logos_gestoras"));
        Files.writeString(dir.resolve("ETF_INTERNACIONAL").resolve("logos_gestoras").resolve("VANGUARD.png"), "x");
        AssetLogos logos = new AssetLogos(dir.toString());

        assertThat(logos.reindex()).isEqualTo(2);
        assertThat(logos.find("META", "STOCK")).get()
            .satisfies(f -> assertThat(f.path()).isEqualTo(dir.resolve("STOCK").resolve("META.svg")));
        assertThat(logos.find("META", "CRIPTO")).get()
            .satisfies(f -> assertThat(f.path()).isEqualTo(dir.resolve("META.png")));
        assertThat(logos.find("META")).get().satisfies(f -> assertThat(f.path()).isEqualTo(dir.resolve("META.png")));
        assertThat(logos.find("META", "REIT")).isEmpty();
        assertThat(logos.find("VANGUARD", "ETF_INTERNACIONAL")).isEmpty();
        assertThat(logos.logoFlag(assetEntity("META", "STOCK"))).isTrue();
        assertThat(logos.logoFlag(assetEntity("O", "REIT"))).isFalse();
    }
}
