package com.activest.domain.integration.pipeline;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetResolver;
import com.activest.domain.integration.IntegrationCryptoExclusionRepository;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionOrigin;
import com.activest.domain.transaction.TransactionRepository;
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
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transforma os movimentos de uma fonte na pre-visualizacao da importacao
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.2 e 6.13). Nao grava nada: a gravacao, pelo
 * TransactionService e com a deteccao de duplicata com lancamentos manuais,
 * e a F4.
 *
 * <p>Cada movimento cai em exatamente um destino:</p>
 * <ol>
 *   <li><b>EXISTING</b> — a conexao ja importou esse externalId (P4);</li>
 *   <li><b>EXCLUDED</b> — o usuario excluiu a operacao (regra E2);</li>
 *   <li><b>NEW</b> ou <b>UNMAPPED</b> — o que as {@link ImportRules} da fonte decidirem;</li>
 *   <li><b>POSSIBLE_DUPLICATE</b> — um NEW que parece com um lancamento MANUAL do
 *   usuario (6.6): mesmo ativo, mesmo tipo, data a um dia de distancia e
 *   quantidade dentro da taxa. Cada lancamento manual casa com um importado so.</li>
 * </ol>
 * <p>Depositos em reais nao viram linha: somam em {@code fiatDeposited}.</p>
 */
@Service
public class ImportPipeline {

    private final AssetResolver assetResolver;
    private final TransactionRepository transactionRepository;
    private final IntegrationCryptoExclusionRepository exclusionRepository;

    public ImportPipeline(AssetResolver assetResolver,
                          TransactionRepository transactionRepository,
                          IntegrationCryptoExclusionRepository exclusionRepository) {
        this.assetResolver = assetResolver;
        this.transactionRepository = transactionRepository;
        this.exclusionRepository = exclusionRepository;
    }

    /** Tolerancia relativa da quantidade ao comparar com lancamento manual: 0,1%. */
    private static final BigDecimal SHARES_TOLERANCE = new BigDecimal("0.001");

    /** Fonte de um dominio so: as mesmas regras para todo movimento, contexto so com o catalogo. */
    @Transactional(readOnly = true)
    public ImportPreview preview(UUID connectionId, UUID userId, List<ImportedMovement> movements,
                                 ImportRules rules) {
        return preview(connectionId, userId, movements, m -> rules,
            ImportContext.of(ticker -> java.util.Optional.empty(), (c, d) -> java.util.Optional.empty()), true);
    }

    /**
     * As regras de cada movimento ({@code rulesFor}) e o contexto delas: catalogo nos tipos da regra e cambio
     * (docs/planos/PLANO_INTEGRACOES.md, 10.2).
     */
    @Transactional(readOnly = true)
    public ImportPreview preview(UUID connectionId, UUID userId, List<ImportedMovement> movements,
                                 Function<ImportedMovement, ImportRules> rulesFor, ImportContext context) {
        return preview(connectionId, userId, movements, rulesFor, context, false);
    }

    private ImportPreview preview(UUID connectionId, UUID userId, List<ImportedMovement> movements,
                                  Function<ImportedMovement, ImportRules> rulesFor, ImportContext context,
                                  boolean legacyCatalog) {
        // Um externalId repetido no mesmo lote e o mesmo movimento: fica o primeiro.
        Map<String, ImportedMovement> unique = new LinkedHashMap<>();
        movements.stream()
            .sorted(Comparator.comparing(ImportedMovement::executedAt))
            .forEach(m -> unique.putIfAbsent(m.externalId(), m));

        BigDecimal fiatDeposited = BigDecimal.ZERO;
        List<ImportedMovement> candidates = new ArrayList<>();
        for (ImportedMovement m : unique.values()) {
            if (m.kind() == MovementKind.FIAT_DEPOSIT) {
                if (m.quoteAmount() != null) {
                    fiatDeposited = fiatDeposited.add(m.quoteAmount());
                }
            } else {
                candidates.add(m);
            }
        }

        List<String> ids = candidates.stream().map(ImportedMovement::externalId).toList();
        Set<String> existing = ids.isEmpty() ? Set.of() : transactionRepository.findImportedIds(connectionId, ids);
        Set<String> excluded = ids.isEmpty() ? Set.of() : exclusionRepository.findExcludedIds(connectionId, ids);

        Map<String, Optional<Asset>> catalog = new HashMap<>();
        List<PreviewItem> runItemList = new ArrayList<>();
        for (ImportedMovement m : candidates) {
            if (existing.contains(m.externalId())) {
                runItemList.add(new PreviewItem(m, PreviewOutcome.EXISTING, null, null));
            } else if (excluded.contains(m.externalId())) {
                runItemList.add(new PreviewItem(m, PreviewOutcome.EXCLUDED, null, null));
            } else {
                ImportRules rules = rulesFor.apply(m);
                // O ticker e procurado so nos tipos da regra (V13: META e cripto e acao do exterior)
                ImportRules.Result r = legacyCatalog
                    ? rules.apply(m, ticker -> catalog.computeIfAbsent(ticker + "|" + rules.getClass().getName(),
                        t -> assetResolver.findInTypes(ticker, rules.assetTypes())))
                    : rules.apply(m, context);
                runItemList.add(r.mapped()
                    ? new PreviewItem(m, PreviewOutcome.NEW, r.transaction(), null)
                    : new PreviewItem(m, PreviewOutcome.UNMAPPED, null, r.reason()));
            }
        }
        return new ImportPreview(connectionId, markDuplicates(userId, runItemList), fiatDeposited);
    }

    /**
     * 6.6 — o usuario pode ter digitado a mesma compra antes de conectar. O
     * importado que parece com um lancamento MANUAL vira POSSIBLE_DUPLICATE,
     * apontando para ele; a decisao e do usuario, na confirmacao.
     */
    private List<PreviewItem> markDuplicates(UUID userId, List<PreviewItem> runItemList) {
        List<PreviewItem> added = runItemList.stream().filter(i -> i.outcome() == PreviewOutcome.NEW).toList();
        if (userId == null || added.isEmpty()) {
            return runItemList;
        }
        Set<Long> assetTickers = added.stream().map(i -> i.transaction().assetId()).collect(Collectors.toSet());
        LocalDate de = added.stream().map(i -> i.transaction().transactionDate()).min(LocalDate::compareTo).orElseThrow();
        LocalDate ate = added.stream().map(i -> i.transaction().transactionDate()).max(LocalDate::compareTo).orElseThrow();
        List<Transaction> manualTransactions = new ArrayList<>(transactionRepository.findByOriginForAssets(
            userId, TransactionOrigin.MANUAL, assetTickers, de.minusDays(1), ate.plusDays(1)));
        if (manualTransactions.isEmpty()) {
            return runItemList;
        }

        List<PreviewItem> result = new ArrayList<>();
        for (PreviewItem item : runItemList) {
            if (item.outcome() != PreviewOutcome.NEW) {
                result.add(item);
                continue;
            }
            ProposedTransaction t = item.transaction();
            Transaction match = manualTransactions.stream()
                .filter(m -> looksLike(m, t))
                .min(Comparator.comparingLong((Transaction m) ->
                        Math.abs(ChronoUnit.DAYS.between(m.getTransactionDate(), t.transactionDate())))
                    .thenComparing(m -> m.getShares().subtract(t.shares()).abs()))
                .orElse(null);
            if (match == null) {
                result.add(item);
            } else {
                manualTransactions.remove(match);
                result.add(new PreviewItem(item.movement(), PreviewOutcome.POSSIBLE_DUPLICATE, t, null,
                    new PreviewItem.DuplicateCandidate(match.getId(), match.getTransactionDate(), match.getShares(),
                        match.getPricePerShare())));
            }
        }
        return result;
    }

    /** Mesmo ativo e tipo, data a ate um dia, quantidade entre a liquida e a bruta (com folga de 0,1%). */
    static boolean looksLike(Transaction manual, ProposedTransaction t) {
        if (manual.getShares() == null || manual.getPortfolioAsset() == null
                || !manual.getPortfolioAsset().getAsset().getId().equals(t.assetId())
                || manual.getType() != t.type()
                || Math.abs(ChronoUnit.DAYS.between(manual.getTransactionDate(), t.transactionDate())) > 1) {
            return false;
        }
        BigDecimal slack = t.shares().multiply(SHARES_TOLERANCE);
        BigDecimal lower = t.shares().subtract(slack);
        BigDecimal upper = t.grossQuantity().max(t.shares()).add(slack);
        return manual.getShares().compareTo(lower) >= 0 && manual.getShares().compareTo(upper) <= 0;
    }
}
