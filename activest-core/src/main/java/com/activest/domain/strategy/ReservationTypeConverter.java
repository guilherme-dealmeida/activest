package com.activest.domain.strategy;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link ReservationType} no banco como SMALLINT (1, 2).
 *
 * <p>Nao e autoApply: so {@code StrategyReservation.typeReservation} usa, e
 * um converter que se aplicasse sozinho a qualquer campo do tipo seria uma
 * regra escondida.</p>
 */
@Converter
public class ReservationTypeConverter implements AttributeConverter<ReservationType, Short> {

    @Override
    public Short convertToDatabaseColumn(ReservationType type) {
        return type != null ? type.code() : null;
    }

    @Override
    public ReservationType convertToEntityAttribute(Short code) {
        return code != null ? ReservationType.fromCode(code) : null;
    }
}
