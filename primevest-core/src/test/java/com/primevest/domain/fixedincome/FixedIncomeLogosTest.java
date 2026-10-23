package com.primevest.domain.fixedincome;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Adendo A do docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md: logo por raiz de CNPJ em carga/data/logos/rf. */
class FixedIncomeLogosTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Indexa a subpasta rf por raiz de CNPJ; raiz invalida nunca vira nome de arquivo")
    void index() throws Exception {
        Path rf = Files.createDirectories(dir.resolve("rf"));
        Files.writeString(rf.resolve("30680829.svg"), "<svg/>");
        Files.writeString(rf.resolve("00000000.png"), "x");
        Files.writeString(dir.resolve("PETR4.svg"), "<svg/>");
        FixedIncomeLogos logos = new FixedIncomeLogos(dir.toString());

        assertThat(logos.reindex()).isEqualTo(2);
        assertThat(logos.hasLogo("30680829")).isTrue();
        assertThat(logos.hasLogo("00000000")).isTrue();
        assertThat(logos.hasLogo("18236120")).isFalse();
        assertThat(logos.find("30680829")).get()
            .satisfies(f -> assertThat(f.mediaType().toString()).isEqualTo("image/svg+xml"));
        // o ticker do catalogo fica na pasta de cima, nao aqui
        assertThat(logos.hasLogo("PETR4")).isFalse();
        assertThat(logos.find("../PETR4")).isEmpty();
    }
}
