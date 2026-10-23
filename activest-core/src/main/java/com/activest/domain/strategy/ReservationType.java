package com.activest.domain.strategy;

import java.util.Arrays;

/**
 * Como a estrategia de reserva de emergencia define o seu alvo.
 *
 * <p>Gravado como numero em {@code strategy_reservations.type_reservation}
 * (1 e 2, por especificacao do escopo), e nao como VARCHAR como os outros
 * enums do projeto. O {@link ReservationTypeConverter} faz a ponte, e e o que
 * impede que 1 e 2 se espalhem pelo codigo. Na API o enum trafega pelo nome.</p>
 */
public enum ReservationType {

    /** Alvo e um percentual da carteira inteira: {@code target_percentage}. */
    PERCENTAGE((short) 1),

    /** Alvo e um valor em reais: {@code target_value}. */
    VALUE((short) 2);

    private final short code;

    ReservationType(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static ReservationType fromCode(short code) {
        return Arrays.stream(values())
            .filter(t -> t.code == code)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("type_reservation desconhecido: " + code));
    }
}
