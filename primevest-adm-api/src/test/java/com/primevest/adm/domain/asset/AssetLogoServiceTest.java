package com.primevest.adm.domain.asset;

import com.primevest.domain.asset.AssetLogos;
import com.primevest.domain.asset.AssetType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** O que conta como logo valido (PLANO_BRAPI.md, F7): so imagem de verdade vai para data/logos. */
class AssetLogoServiceTest {

    private static final byte[] SVG = ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
            + "<circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>").getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("SVG e reconhecido pelo conteudo; pagina HTML de erro nao e logo")
    void extensao() {
        assertThat(AssetLogoService.extension(MediaType.valueOf("image/svg+xml"), SVG)).isEqualTo("svg");
        assertThat(AssetLogoService.extension(MediaType.TEXT_PLAIN, SVG)).isEqualTo("svg");
        assertThat(AssetLogoService.extension(MediaType.TEXT_HTML,
                "<html><body>Not found</body></html>                                   ".getBytes())).isNull();
        assertThat(AssetLogoService.extension(MediaType.IMAGE_PNG, new byte[100])).isEqualTo("png");
        assertThat(AssetLogoService.extension(MediaType.IMAGE_PNG, new byte[10])).isNull();
    }

    @Test
    @DisplayName("O fracionario usa o logo do papel inteiro")
    void fracionario() {
        assertThat(AssetLogoService.baseTicker("PETR4F")).isEqualTo("PETR4");
        assertThat(AssetLogoService.baseTicker("TAEE11F")).isEqualTo("TAEE11");
        assertThat(AssetLogoService.baseTicker("MXRF11")).isEqualTo("MXRF11");
        assertThat(AssetLogoService.baseTicker("MRSA5B")).isEqualTo("MRSA5B");
    }

    @Test
    @DisplayName("Renda fixa e Tesouro nao tem logo; acao, FII, ETF, BDR e cripto tem")
    void tiposComLogo() {
        assertThat(AssetLogos.typeHasLogo(AssetType.builder().name("ACAO").build())).isTrue();
        assertThat(AssetLogos.typeHasLogo(AssetType.builder().name("CRIPTO").build())).isTrue();
        assertThat(AssetLogos.typeHasLogo(AssetType.builder().name("TESOURO_DIRETO").build())).isFalse();
    }
}
