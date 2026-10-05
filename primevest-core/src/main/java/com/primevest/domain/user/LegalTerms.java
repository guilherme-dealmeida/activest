package com.primevest.domain.user;

/**
 * Versão dos Termos de Uso e da Política de Privacidade (docs/planos/PLANO_CADASTRO_USUARIO.md, D9 e D19).
 *
 * <p>O texto mora no front ({@code primevest-web/src/content/legal.ts}); aqui só a versão, que é o que o
 * aceite registra. Trocar o texto de forma relevante = trocar esta constante (e a do front): quem aceitou
 * a anterior passa a ver o modal de novo aceite e, até aceitar, a API responde 403 ({@code TERMS_REQUIRED}).</p>
 *
 * <p>A V41 grava esta mesma versão no usuário semeado.</p>
 */
public final class LegalTerms {

    /** Texto provisório (fictício) até haver o definitivo. */
    public static final String CURRENT_VERSION = "2026-10-provisorio";

    private LegalTerms() {
    }
}
