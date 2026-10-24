package com.activest.adm.integration.b3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** P1 do docs/planos/PLANO_CARGA_INICIAL.md: os 12 meses da B3 acumulam em disco e a data de pagamento nao se perde. */
class B3SupplementStoreTest {

    private static B3Supplement.CashDividend cash(String dataCom, String payment, String value) {
        return new B3Supplement.CashDividend("BRPETRACNPR6", payment, value, "Anual/2025", "11/12/2025",
                "BRPETRACNPR6", "DIVIDENDO", dataCom, null);
    }

    private static B3Supplement sup(B3Supplement.CashDividend... c) {
        return new B3Supplement("PETROBRAS", "PETR", "9512", List.of(c), List.of());
    }

    @Test
    @DisplayName("O que saiu da janela fica; o que voltou na resposta substitui (com a data corrigida)")
    void accumulates() {
        B3Supplement previous = sup(cash("22/12/2024", "20/02/2025", "0,47"), cash("22/06/2025", "20/08/2025", "0,30000"));
        B3Supplement created = sup(cash("22/06/2025", "21/08/2025", "0,3"), cash("22/12/2025", "20/03/2026", "0,29642144000"));

        B3Supplement r = B3SupplementStore.merge(previous, created);

        assertThat(r.cash()).extracting(B3Supplement.CashDividend::lastDatePrior, B3Supplement.CashDividend::paymentDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("22/12/2024", "20/02/2025"),
                        org.assertj.core.groups.Tuple.tuple("22/06/2025", "21/08/2025"),
                        org.assertj.core.groups.Tuple.tuple("22/12/2025", "20/03/2026"));
        assertThat(r.codeCVM()).isEqualTo("9512");
    }

    @Test
    @DisplayName("Parcelas iguais que so mudam no pagamento entram juntas, sem virar duplicata")
    void installments() {
        B3Supplement previous = sup(cash("25/03/2025", null, "0,0945"), cash("25/03/2025", null, "0,0945"));
        B3Supplement created = sup(cash("25/03/2025", "30/06/2025", "0,0945"), cash("25/03/2025", "30/12/2025", "0,0945"));

        assertThat(B3SupplementStore.merge(previous, created).cash()).extracting(B3Supplement.CashDividend::paymentDate)
                .containsExactly("30/06/2025", "30/12/2025");
    }

    @Test
    @DisplayName("Grava em carga/data/b3/proventos-12m/{acoes|fundos}; sem resposta da B3 devolve o arquivo")
    void disk(@TempDir Path dir) {
        B3SupplementStore store = new B3SupplementStore(new ObjectMapper(), dir);

        store.merge(false, "PETR", sup(cash("22/12/2025", "20/03/2026", "0,29")));

        assertThat(Files.exists(dir.resolve("acoes").resolve("PETR.json"))).isTrue();
        assertThat(store.merge(false, "PETR", null).cash()).hasSize(1);
        assertThat(store.merge(true, "PETR", null)).isNull();
    }
}
