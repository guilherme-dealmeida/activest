package com.primevest.domain.integration.pipeline;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.integration.IntegrationCryptoExclusionRepository;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionOrigin;
import com.primevest.domain.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transforma os movimentos de uma fonte na pre-visualizacao da importacao
 * (PLANO_CRIPTO_BINANCE.md, 6.2 e 6.13). Nao grava nada: a gravacao, pelo
 * TransactionService e com a deteccao de duplicata com lancamentos manuais,
 * e a F4.
 *
 * <p>Cada movimento cai em exatamente um destino:</p>
 * <ol>
 *   <li><b>EXISTING</b> — a conexao ja importou esse externalId (P4);</li>
 *   <li><b>EXCLUDED</b> — o usuario excluiu a operacao (regra E2);</li>
 *   <li><b>NEW</b> ou <b>UNMAPPED</b> — o que {@link CryptoImportRules} decidir;</li>
 *   <li><b>POSSIBLE_DUPLICATE</b> — um NEW que parece com um lancamento MANUAL do
 *   usuario (6.6): mesmo ativo, mesmo tipo, data a um dia de distancia e
 *   quantidade dentro da taxa. Cada lancamento manual casa com um importado so.</li>
 * </ol>
 * <p>Depositos em reais nao viram linha: somam em {@code fiatDeposited}.</p>
 */
@Service
public class ImportPipeline {

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final IntegrationCryptoExclusionRepository exclusionRepository;

    public ImportPipeline(AssetRepository assetRepository,
                          TransactionRepository transactionRepository,
                          IntegrationCryptoExclusionRepository exclusionRepository) {
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.exclusionRepository = exclusionRepository;
    }

    /** Tolerancia relativa da quantidade ao comparar com lancamento manual: 0,1%. */
    private static final BigDecimal SHARES_TOLERANCE = new BigDecimal("0.001");

    @Transactional(readOnly = true)
    public ImportPreview preview(UUID connectionId, UUID userId, List<ImportedMovement> movements) {
        // Um externalId repetido no mesmo lote e o mesmo movimento: fica o primeiro.
        Map<String, ImportedMovement> unicos = new LinkedHashMap<>();
        movements.stream()
            .sorted(Comparator.comparing(ImportedMovement::executedAt))
            .forEach(m -> unicos.putIfAbsent(m.externalId(), m));

        BigDecimal fiatDeposited = BigDecimal.ZERO;
        List<ImportedMovement> candidatos = new ArrayList<>();
        for (ImportedMovement m : unicos.values()) {
            if (m.kind() == MovementKind.FIAT_DEPOSIT) {
                if (m.quoteAmount() != null) {
                    fiatDeposited = fiatDeposited.add(m.quoteAmount());
                }
            } else {
                candidatos.add(m);
            }
        }

        List<String> ids = candidatos.stream().map(ImportedMovement::externalId).toList();
        Set<String> existentes = ids.isEmpty() ? Set.of() : transactionRepository.findImportedIds(connectionId, ids);
        Set<String> excluidos = ids.isEmpty() ? Set.of() : exclusionRepository.findExcludedIds(connectionId, ids);

        Map<String, Optional<Asset>> catalogo = new HashMap<>();
        List<PreviewItem> itens = new ArrayList<>();
        for (ImportedMovement m : candidatos) {
            if (existentes.contains(m.externalId())) {
                itens.add(new PreviewItem(m, PreviewOutcome.EXISTING, null, null));
            } else if (excluidos.contains(m.externalId())) {
                itens.add(new PreviewItem(m, PreviewOutcome.EXCLUDED, null, null));
            } else {
                CryptoImportRules.Result r = CryptoImportRules.map(m,
                    ticker -> catalogo.computeIfAbsent(ticker, assetRepository::findByTicker));
                itens.add(r.mapped()
                    ? new PreviewItem(m, PreviewOutcome.NEW, r.transaction(), null)
                    : new PreviewItem(m, PreviewOutcome.UNMAPPED, null, r.reason()));
            }
        }
        return new ImportPreview(connectionId, marcarDuplicatas(userId, itens), fiatDeposited);
    }

    /**
     * 6.6 — o usuario pode ter digitado a mesma compra antes de conectar. O
     * importado que parece com um lancamento MANUAL vira POSSIBLE_DUPLICATE,
     * apontando para ele; a decisao e do usuario, na confirmacao.
     */
    private List<PreviewItem> marcarDuplicatas(UUID userId, List<PreviewItem> itens) {
        List<PreviewItem> novos = itens.stream().filter(i -> i.outcome() == PreviewOutcome.NEW).toList();
        if (userId == null || novos.isEmpty()) {
            return itens;
        }
        Set<Long> ativos = novos.stream().map(i -> i.transaction().assetId()).collect(Collectors.toSet());
        LocalDate de = novos.stream().map(i -> i.transaction().transactionDate()).min(LocalDate::compareTo).orElseThrow();
        LocalDate ate = novos.stream().map(i -> i.transaction().transactionDate()).max(LocalDate::compareTo).orElseThrow();
        List<Transaction> manuais = new ArrayList<>(transactionRepository.findByOriginForAssets(
            userId, TransactionOrigin.MANUAL, ativos, de.minusDays(1), ate.plusDays(1)));
        if (manuais.isEmpty()) {
            return itens;
        }

        List<PreviewItem> resultado = new ArrayList<>();
        for (PreviewItem item : itens) {
            if (item.outcome() != PreviewOutcome.NEW) {
                resultado.add(item);
                continue;
            }
            ProposedTransaction t = item.transaction();
            Transaction par = manuais.stream()
                .filter(m -> parece(m, t))
                .min(Comparator.comparingLong((Transaction m) ->
                        Math.abs(ChronoUnit.DAYS.between(m.getTransactionDate(), t.transactionDate())))
                    .thenComparing(m -> m.getShares().subtract(t.shares()).abs()))
                .orElse(null);
            if (par == null) {
                resultado.add(item);
            } else {
                manuais.remove(par);
                resultado.add(new PreviewItem(item.movement(), PreviewOutcome.POSSIBLE_DUPLICATE, t, null,
                    new PreviewItem.DuplicateCandidate(par.getId(), par.getTransactionDate(), par.getShares(),
                        par.getPricePerShare())));
            }
        }
        return resultado;
    }

    /** Mesmo ativo e tipo, data a ate um dia, quantidade entre a liquida e a bruta (com folga de 0,1%). */
    static boolean parece(Transaction manual, ProposedTransaction t) {
        if (manual.getShares() == null || manual.getPortfolioAsset() == null
                || !manual.getPortfolioAsset().getAsset().getId().equals(t.assetId())
                || manual.getType() != t.type()
                || Math.abs(ChronoUnit.DAYS.between(manual.getTransactionDate(), t.transactionDate())) > 1) {
            return false;
        }
        BigDecimal folga = t.shares().multiply(SHARES_TOLERANCE);
        BigDecimal minimo = t.shares().subtract(folga);
        BigDecimal maximo = t.grossQuantity().max(t.shares()).add(folga);
        return manual.getShares().compareTo(minimo) >= 0 && manual.getShares().compareTo(maximo) <= 0;
    }
}
