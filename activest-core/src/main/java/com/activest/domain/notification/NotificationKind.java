package com.activest.domain.notification;

import static com.activest.domain.notification.NotificationAudience.ADMIN;
import static com.activest.domain.notification.NotificationAudience.USER;
import static com.activest.domain.notification.NotificationCategory.*;
import static com.activest.domain.notification.NotificationSeverity.ATENCAO;
import static com.activest.domain.notification.NotificationSeverity.INFO;

/**
 * Os tipos de mensagem (docs/planos/PLANO_NOTIFICACOES.md, seção 4): público, categoria, gravidade e natureza
 * ({@code pending}: nasce NAO_RESOLVIDA; senão é informativa, sem status).
 */
public enum NotificationKind {

    // --- usuário
    PROVENTO_PAGO(USER, PROVENTOS, INFO, false),
    EVENTO_APLICADO(USER, EVENTOS, INFO, false),
    BONIFICACAO_SEM_CUSTO(USER, EVENTOS, ATENCAO, true),
    /** Titulo vencido em aberto: lance a liquidacao (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 6). */
    RF_VENCIDO(USER, RENDA_FIXA, ATENCAO, true),
    // As tres abaixo eram da liquidacao automatica (revogada pela secao 6 do PLANO_VENDA_RENDA_FIXA): nao sao mais
    // emitidas; ficam pelas mensagens ja gravadas.
    RF_LIQUIDADA(USER, RENDA_FIXA, INFO, false),
    RF_LIQUIDACAO_SEM_VALOR(USER, RENDA_FIXA, ATENCAO, true),
    RF_LIQUIDACAO_DIVERGENTE(USER, RENDA_FIXA, ATENCAO, true),
    /**
     * Compra de Tesouro importada da B3 cuja corretora nao casou com o cadastro: o titulo ficou com a distribuidora
     * "Instituicao nao listada" (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, L21).
     */
    RF_DISTRIBUIDORA_NAO_LISTADA(USER, RENDA_FIXA, ATENCAO, true),
    PRECO_ABAIXO_IDEAL(USER, PRECO_TETO, INFO, false),
    PRECO_TETO_PADRAO_MUDOU(USER, PRECO_TETO, INFO, true),
    INTEGRACAO_IMPORTOU(USER, INTEGRACOES, INFO, false),
    INTEGRACAO_AGUARDANDO(USER, INTEGRACOES, ATENCAO, true),
    INTEGRACAO_DIVERGENCIA(USER, INTEGRACOES, ATENCAO, true),
    INTEGRACAO_CONEXAO(USER, INTEGRACOES, ATENCAO, true),
    INTEGRACAO_FALHOU(USER, INTEGRACOES, ATENCAO, false),
    POSICAO_TICKER(USER, CARTEIRA, ATENCAO, true),
    // Metas (docs/planos/PLANO_METAS.md, D20): concluida e prazo vencido sao informativas; fora do objetivo, atrasada
    // e sem dados sao pendencias que se resolvem sozinhas quando a condicao volta.
    META_CONCLUIDA(USER, METAS, INFO, false),
    META_FORA_DO_OBJETIVO(USER, METAS, ATENCAO, true),
    META_ATRASADA(USER, METAS, ATENCAO, true),
    META_PRAZO_VENCIDO(USER, METAS, INFO, false),
    META_SEM_DADOS(USER, METAS, ATENCAO, true),

    // --- administrador
    CATALOGO_TICKER_NOVO(ADMIN, SISTEMA, INFO, true),
    CATALOGO_TICKER_SUMIDO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_TICKER_RENOMEADO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_SEM_COTACAO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_INDICE_ATRASADO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_ROTULO_DESCONHECIDO(ADMIN, SISTEMA, ATENCAO, true),
    CATALOGO_TESOURO_SEM_TITULO(ADMIN, SISTEMA, ATENCAO, true),
    ROTINA_FALHOU(ADMIN, SISTEMA, ATENCAO, false),
    FONTE_INDISPONIVEL(ADMIN, SISTEMA, ATENCAO, false),
    /**
     * A fonte respondeu, mas sem dado novo ha dias uteis demais (docs/planos/PLANO_FONTE_TESOURO.md, D3): pendencia que
     * se resolve sozinha quando a fonte volta a publicar.
     */
    FONTE_DESATUALIZADA(ADMIN, SISTEMA, ATENCAO, true);

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
