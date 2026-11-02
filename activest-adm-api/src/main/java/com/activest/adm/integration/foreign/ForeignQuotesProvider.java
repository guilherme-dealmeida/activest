package com.activest.adm.integration.foreign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fonte de preço diário dos ativos do exterior (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, D9): um pregão inteiro
 * por pedido, com o ticker já na grafia do catálogo (D5). A primeira implementação é o Massive grouped daily
 * ({@code MassiveClient}); trocar de fonte não mexe na rotina.
 */
public interface ForeignQuotesProvider {

    /** Nome da fonte para os logs e as mensagens ao administrador. */
    String sourceName();

    /** O pregão de {@code date}, inteiro. */
    DayQuotes day(LocalDate date);

    enum Status {
        /** Pregão com cotações. */
        OK,
        /** A fonte ainda não liberou o dia (o plano gratuito libera depois da meia-noite de Nova York): rodada seguinte. */
        NOT_RELEASED,
        /** Resposta sem nenhum papel: dia sem pregão nos EUA (feriado, fim de semana) — I4. */
        NO_SESSION
    }

    /** Um papel no pregão; {@code ticker} na grafia do catálogo ({@code ABR-E}, {@code BRK.B}). */
    record Quote(String ticker, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
    }

    /**
     * @param fromDisk o pregão veio do arquivo guardado (I1), sem ir à rede
     */
    record DayQuotes(LocalDate date, Status status, List<Quote> quotes, boolean fromDisk) {

        public static DayQuotes of(LocalDate date, Status status) {
            return new DayQuotes(date, status, List.of(), false);
        }
    }

    /** A fonte recusou a chave (HTTP 401): repetir não adianta; a rodada para e o administrador é avisado (I5). */
    class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    /** Data fora do que o plano da fonte entrega (403 num dia passado — o gratuito tem 2 anos): erro de entrada (I5). */
    class OutOfRangeException extends RuntimeException {
        public OutOfRangeException(String message) {
            super(message);
        }
    }

    /** A fonte não respondeu (rede, 5xx, 429 depois das novas tentativas, resposta fora do formato). */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
