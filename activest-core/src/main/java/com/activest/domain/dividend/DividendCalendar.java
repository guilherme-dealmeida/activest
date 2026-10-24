package com.activest.domain.dividend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * O "hoje" dos proventos: a data no fuso da B3 (America/Sao_Paulo).
 *
 * <p>Decide o que ja foi recebido e o que e a receber
 * ({@link Dividend#isReceived}). Nunca a data do banco: o PostgreSQL roda em
 * UTC, e as 21h de Sao Paulo o CURRENT_DATE ja e o dia seguinte — o provento
 * que paga amanha contaria como recebido hoje a noite.</p>
 *
 * <p>Bean proprio para os testes fixarem a data.</p>
 */
@Component
public class DividendCalendar {

    public static final ZoneId B3_ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    @Autowired
    public DividendCalendar() {
        this(Clock.system(B3_ZONE));
    }

    public DividendCalendar(Clock clock) {
        this.clock = clock;
    }

    /** Hoje em Sao Paulo, qualquer que seja o fuso do relogio recebido. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), B3_ZONE);
    }
}
