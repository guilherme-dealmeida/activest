package com.primevest.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Traducao de excecao para resposta HTTP.
 *
 * <p>O handler de {@code Exception} no fim e uma rede: tudo que nao tem handler
 * proprio cai nele e vira 500. Por isso os casos previsiveis ganham o seu —
 * rota inexistente, metodo errado, JSON malformado e violacao de constraint
 * eram todos 500, e 500 nao diz nada a quem chamou.</p>
 *
 * <p>Excecao de um modulo so (corretora e credencial, no primevest-app-api) tem
 * handler no proprio modulo, num advice com precedencia maior que este: o Spring
 * para no primeiro advice que tem handler para a excecao, e este tem o de
 * {@code Exception}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static ResponseEntity<ErrorResponse> response(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return new ResponseEntity<>(
            new ErrorResponse(status.value(), message, LocalDateTime.now(), fieldErrors), status);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "Dados inválidos", errors);
    }

    /** Corpo que o Jackson nao consegue ler: JSON quebrado, enum inexistente, data em formato errado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return response(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido", null);
    }

    /** Arquivo acima do limite do multipart (foto de perfil: 2 MB). Sem isto virava 500. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(Exception ex) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo passa do tamanho permitido.", null);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ErrorResponse> handleParameter(Exception ex) {
        return response(HttpStatus.BAD_REQUEST, "Parâmetro inválido", null);
    }

    /**
     * Caminho sem rota. Desde o Spring Boot 3.2 isso e uma excecao como outra
     * qualquer, e sem este handler ela caia no generico e virava 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "Recurso não encontrado", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido", null);
    }

    /**
     * Rede de seguranca das constraints do banco. Nenhuma requisicao deveria
     * chegar aqui — a regra que a constraint protege tem que estar no servico,
     * com mensagem que explique o que fazer. Quando chegar, 409 ao menos diz
     * que e conflito de dado, e nao falha do servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        return response(HttpStatus.CONFLICT, "A operação conflita com um registro existente", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, "Acesso negado", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return response(HttpStatus.UNAUTHORIZED, "Falha de autenticação", null);
    }

    /**
     * Excecao que ja diz o status (ex.: 403 "Somente administrador." dos
     * endpoints de admin). Sem este handler ela caia no de {@code Exception} e
     * virava 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return response(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado", null);
    }
}
