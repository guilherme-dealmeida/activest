package com.primevest.adm.integration.bcb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Recortes do BCB em src/test/resources/bcb: o cadastro do IF.data de 06/2026 (grupos Nubank, XP e Sicredi, uma
 * cooperativa, o Banco Master em liquidacao, a Nu DTVM inativa e a Stone, instituicao de pagamento) e as sedes de
 * "instituicoes em funcionamento" com o site.
 */
public final class BcbFixtures {

    private BcbFixtures() {
    }

    public static String read(String name) {
        try (var in = BcbFixtures.class.getResourceAsStream("/bcb/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Fixture ausente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BcbInstitutionsClient.Registry registry() {
        return BcbInstitutionsClient.parseRegistry("202606", read("ifdata_cadastro_202606.json"));
    }

    public static Map<String, String> websites() {
        Map<String, String> out = new HashMap<>();
        BcbInstitutionsClient.parseWebsites(read("sedes_sociedades.json"), out);
        return out;
    }
}
