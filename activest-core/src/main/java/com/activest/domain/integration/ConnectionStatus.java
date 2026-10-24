package com.activest.domain.integration;

/**
 * Situacao da conexao (V25 e V40, ck_integration_connections_status).
 *
 * <ul>
 *   <li><b>ACTIVE</b> — credencial valida e so de leitura.</li>
 *   <li><b>PENDING_AUTHORIZATION</b> — a fonte reconhece o investidor, mas ele ainda
 *   nao autorizou o Activest (B3: opt-in na Area do Investidor; V40).</li>
 *   <li><b>INVALID_CREDENTIALS</b> — a fonte recusou a chave.</li>
 *   <li><b>PERMISSION_TOO_BROAD</b> — a chave negocia, saca ou transfere: a
 *   sincronizacao fica bloqueada ate o usuario trocar a chave (6.4).</li>
 *   <li><b>REVOKED</b> — o usuario desconectou; a credencial ja foi apagada.</li>
 * </ul>
 */
public enum ConnectionStatus {
    ACTIVE,
    PENDING_AUTHORIZATION,
    INVALID_CREDENTIALS,
    PERMISSION_TOO_BROAD,
    REVOKED
}
