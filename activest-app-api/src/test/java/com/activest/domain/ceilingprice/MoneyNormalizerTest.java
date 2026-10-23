package com.activest.domain.ceilingprice;

import com.activest.domain.ceilingprice.MoneyNormalizer.Currency;
import com.activest.domain.ceilingprice.MoneyNormalizer.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cenários 6 e 7 do pedido (docs/planos/PLANO_PRECO_TETO.md, 4.3). */
class MoneyNormalizerTest {

    @Test
    @DisplayName("Cenário 6: mil, milhão e bilhão viram reais em unidade")
    void units() {
        assertThat(MoneyNormalizer.toBrl(new BigDecimal("13814000"), Unit.MIL, Currency.BRL, null))
                .isEqualByComparingTo("13814000000");
        assertThat(MoneyNormalizer.toBrl(new BigDecimal("9953"), Unit.MILHAO, Currency.BRL, null))
                .isEqualByComparingTo("9953000000");
        assertThat(MoneyNormalizer.toBrl(new BigDecimal("-21.048"), Unit.BILHAO, Currency.BRL, null))
                .isEqualByComparingTo("-21048000000");
        assertThat(MoneyNormalizer.toBrl(new BigDecimal("123.456"), Unit.UNIDADE, Currency.BRL, BigDecimal.TEN))
                .isEqualByComparingTo("123.46");   // em real o câmbio é ignorado
    }

    @Test
    @DisplayName("Cenário 7: dólar × PTAX")
    void dollar() {
        assertThat(MoneyNormalizer.toBrl(new BigDecimal("2000"), Unit.MILHAO, Currency.USD, new BigDecimal("4.9892")))
                .isEqualByComparingTo("9978400000");
        assertThatThrownBy(() -> MoneyNormalizer.toBrl(BigDecimal.ONE, Unit.UNIDADE, Currency.USD, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
