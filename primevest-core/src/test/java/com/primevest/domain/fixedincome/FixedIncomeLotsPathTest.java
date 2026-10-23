package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.fixedincome.FixedIncomeLots.DayValue;
import com.primevest.domain.fixedincome.FixedIncomeLots.Move;
import com.primevest.domain.fixedincome.FixedIncomeLots.Path;
import com.primevest.domain.fixedincome.FixedIncomeLots.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/planos/PLANO_RENTABILIDADE.md, 4.2: o caminho diario numa passada da o mesmo que {@link FixedIncomeLots#run}
 * com cada dia como data de referencia — bruto e liquido.
 */
class FixedIncomeLotsPathTest {

    private static final FixedIncomeCurve.Terms PRE_10 =
            new FixedIncomeCurve.Terms(RateType.PREFIXADO, null, new BigDecimal("10"), null);

    @Test
    @DisplayName("Caminho = run dia a dia (prefixado, com venda parcial pelo liquido no meio)")
    void pathMatchesRun() {
        LocalDate start = LocalDate.of(2026, 3, 2);
        LocalDate end = LocalDate.of(2026, 5, 29);
        List<Move> moves = List.of(
                Move.buy("c1", start, new BigDecimal("10000")),
                Move.buy("c2", LocalDate.of(2026, 3, 20), new BigDecimal("5000")),
                Move.sellNet("v1", LocalDate.of(2026, 4, 15), new BigDecimal("3000"), false));
        FixedIncomeTaxTable taxes = FixedIncomeLotsTest.v52();

        Path path = FixedIncomeLots.path(PRE_10, moves, start, end, null, null, taxes, 1L, SaleRule.PROPORCIONAL);

        assertThat(path.status()).isEqualTo(FixedIncomeCurve.Status.OK);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            LocalDate ref = d;
            List<Move> until = moves.stream().filter(m -> !m.date().isAfter(ref)).toList();
            Result r = FixedIncomeLots.run(PRE_10, until, d, null, null, taxes, 1L, SaleRule.PROPORCIONAL);
            DayValue v = path.days().get(d);
            assertThat(v.gross()).as("bruto em %s", d).isEqualByComparingTo(r.balance());
            assertThat(v.net()).as("liquido em %s", d).isEqualByComparingTo(r.estimatedNet());
        }
    }

    @Test
    @DisplayName("TR sem curva: caminho UNSUPPORTED (o valor nao serve de preco)")
    void withoutCurve() {
        FixedIncomeCurve.Terms tr = new FixedIncomeCurve.Terms(RateType.POS_FIXADO, Indexer.TR, new BigDecimal("100"),
                null);
        LocalDate start = LocalDate.of(2026, 3, 2);
        Path path = FixedIncomeLots.path(tr, List.of(Move.buy("c1", start, new BigDecimal("1000"))), start,
                start.plusDays(10), null, null, null, 1L, SaleRule.PROPORCIONAL);
        assertThat(path.status()).isEqualTo(FixedIncomeCurve.Status.UNSUPPORTED);
        assertThat(path.days()).isNotEmpty();
    }
}
