package com.activest.domain.integration;

import com.activest.domain.integration.credential.CredentialUnavailableException;
import com.activest.exception.ErrorResponse;
import com.activest.exception.GlobalExceptionHandler;
import com.activest.integration.binance.BinanceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros das integracoes com corretoras. Estavam no {@link GlobalExceptionHandler}; com
 * ele no activest-core, ficam aqui, junto das classes que os lancam. Precedencia maior
 * que a do global: o Spring para no primeiro advice que tem handler para a excecao, e o
 * global tem o de {@code Exception}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IntegrationExceptionHandler {

    /**
     * A corretora respondeu com erro ou nao respondeu. 502: o Activest esta de
     * pe, quem falhou foi a fonte. A mensagem nunca traz chave nem assinatura.
     */
    @ExceptionHandler(BinanceException.class)
    public ResponseEntity<ErrorResponse> handleBinance(BinanceException ex) {
        return GlobalExceptionHandler.response(HttpStatus.BAD_GATEWAY, ex.getMessage(), null);
    }

    /** Chave-mestra ausente ou credencial que nao decifra: problema do servidor. */
    @ExceptionHandler(CredentialUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCredentialUnavailable(CredentialUnavailableException ex) {
        return GlobalExceptionHandler.response(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null);
    }
}
