package com.primevest.domain.notification;

import static com.primevest.domain.notification.NotificationAudience.ADMIN;
import static com.primevest.domain.notification.NotificationAudience.USER;
import static com.primevest.domain.notification.NotificationCategory.*;
import static com.primevest.domain.notification.NotificationSeverity.ATENCAO;
import static com.primevest.domain.notification.NotificationSeverity.INFO;

/**
 * Os tipos de mensagem (docs/planos/PLANO_NOTIFICACOES.md, seção 4): público, categoria, gravidade e natureza
 * ({@code pending}: nasce NAO_RESOLVIDA; senão é informativa, sem status).
 */
public enum NotificationKind {

    // --- usuário
    PROVENTO_PAGO(USER, PROVENTOS, INFO, false),
    EVENTO_APLICADO(USER, EVENTOS, INFO, false),
    BONIFICACAO_SEM_CUSTO(USER, EVENTOS, ATENCAO, true),
    RF_LIQUIDADA(USER, RENDA_FIXA, INFO, false),
    RF_LIQUIDACAO_SEM_VALOR(USER, RENDA_FIXA, ATENCAO, true),
    RF_LIQUIDACAO_DIVERGENTE(USER, RENDA_FIXA, ATENCAO, true),
    PRECO_ABAIXO_IDEAL(USER, PRECO_TETO, INFO, false),
    PRECO_TETO_PADRAO_MUDOU(USER, PRECO_TETO, INFO, true),
    INTEGRACAO_IMPORTOU(USER, INTEGRACOES, INFO, false),
    INTEGRACAO_AGUARDANDO(USER, INTEGRACOES, ATENCAO, true),
    INTEGRACAO_DIVERGENCIA(USER, INTEGRACOES, ATENCAO, true),
    INTEGRACAO_CONEXAO(USER, INTEGRACOES, ATENCAO, true),
    INTEGRACAO_FALHOU(USER, INTEGRACOES, ATENCAO, false),
    POSICAO_TICKER(USER, CARTEIRA, ATENCAO, true),

    // --- administrador
    CATALOGO_TICKER_NOVO(ADMIN, SISTEMA, INFO, true),
    CATALOGO_TICKER_SUMIDO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_TICKER_RENOMEADO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_SEM_COTACAO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_INDICE_ATRASADO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_ROTULO_DESCONHECIDO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_TESOURO_SEM_TITULO(ADMIN, SISTEMA, ATENCAO, true),
    ROTINA_FALHOU(ADMIN, SISTEMA, ATENCAO, false),
    FONTE_INDISPONIVEL(ADMIN, SISTEMA, ATENCAO, false);

    private final NotificationAudience audience;
    private final NotificationCategory category;
    private final NotificationSeverity severity;
    private final boolean pending;

    NotificationKind(NotificationAudience audience, NotificationCategory category, NotificationSeverity severity,
                     boolean pending) {
        this.audience = audience;
        this.category = category;
        this.severity = severity;
        this.pending = pending;
    }

    public NotificationAudience audience() { return audience; }
    public NotificationCategory category() { return category; }
    public NotificationSeverity severity() { return severity; }
    public boolean pending() { return pending; }
}
