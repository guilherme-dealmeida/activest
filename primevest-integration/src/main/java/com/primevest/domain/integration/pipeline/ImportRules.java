package com.primevest.domain.integration.pipeline;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.integration.source.ImportedMovement;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * As regras que transformam o movimento de uma fonte em transacao do PrimeVest
 * (docs/planos/PLANO_INTEGRACAO_B3.md, G3). Uma implementacao por dominio:
 * {@link CryptoImportRules} (R2–R8) e, para a B3, as regras B1–B10.
 *
 * <p>Pura: nao consulta banco nem relogio. O catalogo entra como funcao, e o
 * resultado e a transacao proposta ou o motivo de nao importar — os dois
 * legiveis na pre-visualizacao.</p>
 */
public interface ImportRules {

    /** Resultado: exatamente um dos dois e preenchido. */
    record Result(ProposedTransaction transaction, String reason) {
        public static Result ok(ProposedTransaction t) {
            return new Result(t, null);
        }

        public static Result no(String reason) {
            return new Result(null, reason);
        }

        public boolean mapped() {
            return transaction != null;
        }
    }

    Result apply(ImportedMovement movement, Function<String, Optional<Asset>> catalog);

    /**
     * O ativo cuja posicao no PrimeVest e comparada com o saldo que a fonte informa
     * (aviso de divergencia, E3; G6).
     */
    boolean tracksPosition(Asset asset);

    /** Codigos do saldo da fonte que sao dinheiro, nao posicao (Binance: BRL, USD, EUR). */
    default Set<String> cashBalances() {
        return Set.of();
    }

    /**
     * Saldo vazio vindo da fonte quer dizer "nao tem nada"? Na corretora de cripto, sim
     * (o saldo sempre vem). Na B3, nao: a Posicao pode nao ter vindo, e ai nao ha o que
     * comparar.
     */
    default boolean emptyBalanceMeansNoPosition() {
        return true;
    }
}
