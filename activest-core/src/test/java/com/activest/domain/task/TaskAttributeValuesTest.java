package com.activest.domain.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskAttributeValuesTest {

    @Test
    @DisplayName("Vazio sempre serve; cada tipo recusa o valor fora do formato")
    void validationByType() {
        assertThat(TaskAttributeValues.validate(TaskValueType.YEAR, "  ")).isNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.YEAR, "2010")).isNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.YEAR, "10")).isNotNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.DATE, "2026-10-22")).isNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.DATE, "22/10/2026")).isNotNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.BOOLEAN, "true")).isNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.BOOLEAN, "sim")).isNotNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.INTEGER, "365")).isNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.INTEGER, "1,5")).isNotNull();
        assertThat(TaskAttributeValues.validate(TaskValueType.TEXT, "XPML11;28757546000100")).isNull();
    }

    @Test
    @DisplayName("Leitura tipada: vazio vira nulo (ou lista vazia), lista apara os itens")
    void read() {
        assertThat(TaskAttributeValues.asYear(" 2010 ")).isEqualTo(2010);
        assertThat(TaskAttributeValues.asYear(null)).isNull();
        assertThat(TaskAttributeValues.asDate("2026-10-01")).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(TaskAttributeValues.asBoolean("")).isNull();
        assertThat(TaskAttributeValues.asBoolean("true")).isTrue();
        assertThat(TaskAttributeValues.asList("CDI, IPCA,,IBOV ")).isEqualTo(List.of("CDI", "IPCA", "IBOV"));
        assertThat(TaskAttributeValues.asList(" ")).isEmpty();
    }

    @Test
    @DisplayName("Regra de nome: maiúsculo, sem acento, sem espaço, palavras separadas por _")
    void names() {
        assertThat(TaskNames.isValid("B3_DAILY_QUOTES_RELOAD")).isTrue();
        assertThat(TaskNames.isValid("b3_daily")).isFalse();
        assertThat(TaskNames.isValid("COTAÇÃO")).isFalse();
        assertThat(TaskNames.isValid("A B")).isFalse();
        assertThat(TaskNames.isValid("_A")).isFalse();
        assertThat(TaskNames.isValid("A__B")).isFalse();
        assertThat(TaskNames.isValid("A".repeat(81))).isFalse();
    }
}
