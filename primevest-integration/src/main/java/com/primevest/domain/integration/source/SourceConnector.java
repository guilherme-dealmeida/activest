package com.primevest.domain.integration.source;

import com.primevest.domain.integration.IntegrationProvider;
import com.primevest.domain.integration.credential.SourceCredentials;
import com.primevest.domain.integration.pipeline.ImportRules;

import java.util.Map;

/**
 * O contrato de toda fonte externa (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.2;
 * generalizado em docs/planos/PLANO_INTEGRACAO_B3.md, G1–G12).
 *
 * <p>Cada fonte e uma implementacao; a Binance e a primeira. O conector so sabe
 * falar com a sua fonte: autentica, pagina, respeita os limites, agrupa o que a
 * fonte entrega em pedacos (R1, B1) e monta o {@link ImportedMovement} com um
 * {@code externalId} estavel (R5, B5). As regras que transformam movimento em
 * transacao ficam no {@link ImportRules} que ele indica — nunca no conector.</p>
 */
public interface SourceConnector {

    IntegrationProvider provider();

    /**
     * Confere a credencial na fonte: se e aceita, se e so de leitura e, nas fontes
     * com autorizacao do investidor, se ela ja foi dada. Chamado ao conectar e
     * antes de cada sincronizacao (P2).
     */
    CredentialCheck check(SourceCredentials credentials);

    /**
     * Busca o que houve desde os cursores informados (vazio = historico inteiro).
     */
    FetchResult fetch(SourceCredentials credentials, Map<String, String> cursors);

    /** As regras de importacao desta fonte (G3). */
    ImportRules rules();

    /**
     * A fonte so aceita uma consulta por dia para o mesmo investidor (B3: "consultar
     * cada CPF uma vez por dia", G12). Com {@code true}, sincronizacao manual e
     * rotina diaria respeitam o limite.
     */
    default boolean oncePerDay() {
        return false;
    }

    /**
     * Onde o investidor autoriza o PrimeVest na fonte (B3: o link de opt-in do Pacote de
     * Acesso), para a tela mostrar enquanto a conexao aguarda; nulo nas fontes sem esse passo.
     */
    default String authorizationUrl() {
        return null;
    }

    /** A credencial no tipo que esta fonte usa, ou erro claro se vier outra. */
    static <T extends SourceCredentials> T as(Class<T> type, SourceCredentials credentials) {
        if (!type.isInstance(credentials)) {
            throw new IllegalArgumentException("Credencial do tipo errado para a fonte: esperado "
                + type.getSimpleName() + ".");
        }
        return type.cast(credentials);
    }
}
