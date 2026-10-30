package com.activest.adm.domain.foreign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A curadoria da planilha (C9, docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md):
 * {@code carga/data/levantamento/internacional/curadoria.json}, gerado pelo script
 * {@code carga/scripts/levantamento/internacional_curadoria.py} a partir das colunas "Curadoria" da planilha. A tarefa
 * aplica com a origem {@code CURATION}, que nunca é sobrescrita.
 *
 * @param items por ticker da lista (formato da C3), em maiúsculas
 */
public record ForeignCuration(Path file, Map<String, Item> items) {

    /**
     * Uma linha curada. Campos nulos = sem curadoria naquele ponto.
     *
     * @param type      tipo (STOCK, REIT, ETF_INTERNACIONAL); no papel excluído pela regra, inclui com esse tipo
     * @param category  nome da categoria do tipo
     * @param newTicker ticker a gravar no lugar do da lista
     * @param exclude   tira o papel do catálogo
     */
    public record Item(String ticker, String type, String category, String newTicker, boolean exclude) {
    }

    public static ForeignCuration empty() {
        return new ForeignCuration(null, Map.of());
    }

    public Item get(String ticker) {
        return ticker == null ? null : items.get(ticker.toUpperCase(Locale.ROOT));
    }

    /** Lê o arquivo; ausente = sem curadoria. */
    public static ForeignCuration load(Path file, ObjectMapper mapper) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return new ForeignCuration(file, Map.of());
        }
        JsonNode root = mapper.readTree(file.toFile());
        Map<String, Item> items = new LinkedHashMap<>();
        for (JsonNode n : root.path("itens")) {
            String ticker = text(n, "ticker");
            if (ticker == null) {
                continue;
            }
            String t = ticker.toUpperCase(Locale.ROOT);
            items.put(t, new Item(t, upper(text(n, "tipo")), upper(text(n, "categoria")), upper(text(n, "tickerNovo")),
                    n.path("excluir").asBoolean(false)));
        }
        return new ForeignCuration(file, items);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
