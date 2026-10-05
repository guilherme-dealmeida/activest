package com.primevest.domain.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Origem do pedido, para os limites por IP. Atrás do proxy do Vite (ou de um proxy de produção) o
 * endereço da conexão é o do proxy; o {@code X-Forwarded-For}, quando existe, traz o do navegador.
 * Sem ele, todos os pedidos contam como um só IP — por isso os limites por IP são folgados.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
