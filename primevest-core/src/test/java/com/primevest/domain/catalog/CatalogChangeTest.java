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

        String text = json.writeValueAsString(change);
        CatalogChange read = json.readValue(text, CatalogChange.class);

        assertThat(read).isEqualTo(change);
        assertThat(text).contains("\"origin\":\"PROVENTOS\"");
    }

    @Test
    @DisplayName("Indices que mudaram (R5 da renda fixa) vao e voltam; mensagem antiga, sem indexCodes, ainda e lida")
    void indices() throws Exception {
        CatalogChange change = CatalogChange.indices(Set.of("CDI", "IPCA"));

        CatalogChange read = json.readValue(json.writeValueAsString(change), CatalogChange.class);
        assertThat(read).isEqualTo(change);
        assertThat(read.assetIds()).isEmpty();

        CatalogChange old = json.readValue("{\"origin\":\"FECHAMENTO\",\"assetIds\":[1]}", CatalogChange.class);
        assertThat(old.indexCodes()).isEmpty();
        assertThat(old.assetIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("Sem ativos: conjunto vazio, nunca nulo")
    void withoutAssets() {
        assertThat(new CatalogChange(CatalogChange.Origin.FECHAMENTO, null).assetIds()).isEmpty();
    }
}
