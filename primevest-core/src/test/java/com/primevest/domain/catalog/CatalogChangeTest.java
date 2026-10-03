package com.primevest.domain.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogChangeTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("\"Catalogo mudou\" vai e volta em JSON pelo Redis sem perder origem nem ativos")
    void json() throws Exception {
        CatalogChange change = new CatalogChange(CatalogChange.Origin.PROVENTOS, Set.of(10L, 20L));

        String texto = json.writeValueAsString(change);
        CatalogChange lido = json.readValue(texto, CatalogChange.class);

        assertThat(lido).isEqualTo(change);
        assertThat(texto).contains("\"origin\":\"PROVENTOS\"");
    }

    @Test
    @DisplayName("Sem ativos: conjunto vazio, nunca nulo")
    void semAtivos() {
        assertThat(new CatalogChange(CatalogChange.Origin.FECHAMENTO, null).assetIds()).isEmpty();
    }
}
