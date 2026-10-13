package com.primevest.domain.transaction;

import com.primevest.domain.integration.IntegrationCryptoExclusion;
import com.primevest.domain.integration.IntegrationCryptoExclusionRepository;
import com.primevest.domain.integration.pipeline.ImportRules;
import com.primevest.domain.integration.pipeline.ImportRulesRegistry;
import com.primevest.domain.integration.pipeline.ProposedTransaction;
import com.primevest.domain.integration.source.MovementJson;
import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetLogos;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetTerms;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.dividend.DividendRecalculator;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeNoteRepository;
import com.primevest.domain.fixedincome.FixedIncomeSettlement;
import com.primevest.domain.portfolio.AppreciationUpdater;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.Position;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.portfolio.PositionEvents;
import com.primevest.domain.corporateaction.StockEventStep;
import com.primevest.domain.portfolio.PositionGuard;
import com.primevest.domain.transaction.TreasuryAllocator.SaleIntent;
import com.primevest.domain.transaction.dto.NoteOptionDTO;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.user.UserRepository;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Criacao, edicao e exclusao de transacao — e as regras de venda.
 *
 * <p>Tres tipos de ativo se comportam de forma diferente na venda, e o servico
 * separa os tres caminhos:</p>
 * <ul>
 *   <li><b>renda variavel</b> — saldo por cota, por posicao;</li>
 *   <li><b>Renda Fixa</b> — saldo por valor, por titulo: a venda e um resgate,
 *   e e ela que corrige a valorizacao (ver {@link FixedIncomeSettlement});</li>
 *   <li><b>Tesouro Direto</b> — saldo por cota, mas do ATIVO: a venda e
 *   repartida entre os titulos mais antigos (ver {@link TreasuryAllocator}).</li>
 * </ul>
 *
 * <p>As validacoes rodam sobre o estado ja alterado, e nao sobre o pedido: o
 * lancamento e gravado, a posicao inteira e reavaliada e o que nao fecha volta
 * atras pela transacao de banco. E o que faz criar, editar e excluir passarem
 * exatamente pelo mesmo teste — excluir uma compra que sustentava uma venda
 * falha pelo mesmo caminho que lancar a venda sem ter a compra.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransactionService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final AssetRepository assetRepository;
    private final FixedIncomeNoteRepository fixedIncomeNoteRepository;
    private final UserRepository userRepository;
    private final DividendRecalculator dividendRecalculator;
    private final PositionCalculator positionCalculator;
    private final AppreciationUpdater appreciationUpdater;
    private final PositionGuard positionGuard;
    private final FixedIncomeSettlement fixedIncomeSettlement;
    private final com.primevest.domain.fixedincome.FixedIncomeValuation fixedIncomeValuation;
    private final TreasuryAllocator treasuryAllocator;
    /** Avisa quem depende da posicao (proventos sob demanda) depois do commit. */
    private final ApplicationEventPublisher eventPublisher;
    /** Exclusao de importada vira exclusao registrada (regra E2 do docs/planos/PLANO_CRIPTO_BINANCE.md). */
    private final IntegrationCryptoExclusionRepository cryptoExclusionRepository;
    /** Movimento importado em JSON, para o "restaurar" (regra E4). */
    private final MovementJson movementJson;
    private final ImportRulesRegistry importRules;
    /** Logo do ativo na coluna ATIVO da listagem, como na carteira. */
    private final AssetLogos assetLogos;

    /**
     * Gets user transactions.
     */
    public List<TransactionResponseDTO> getTransactions(UUID userId) {
        List<Transaction> transactions = transactionRepository.findAllTransactionsByUserId(userId);
        Map<UUID, GroupInfo> groups = groupsOf(transactions);
        return transactions.stream()
            .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed()
                .thenComparing(Comparator.comparing(Transaction::getCreatedAt).reversed()))
            .map(tx -> toResponseDTO(tx, groups))
            .collect(Collectors.toList());
    }

    /**
     * Os titulos de renda fixa do usuario num ativo, para o seletor da Nova
     * Transacao (compra e venda). Todos, e nao so os abertos: lancamento
     * retroativo existe. Titulo sem posicao (sem nenhuma transacao) fica de fora.
     */
    public List<NoteOptionDTO> getNotes(UUID userId, String ticker) {
        Asset asset = ticker == null ? null : assetRepository.findByTicker(ticker.trim().toUpperCase()).orElse(null);
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (asset == null || portfolio == null) {
            return List.of();
        }
        LocalDate hoje = LocalDate.now();
        return fixedIncomeNoteRepository.findByUserIdAndAssetId(userId, asset.getId()).stream()
            .flatMap(note -> portfolioAssetRepository.findByPortfolioIdAndNoteId(portfolio.getId(), note.getId())
                .map(pa -> new NoteOptionDTO(note.getId(), note.getIssuer(), note.getMaturityDate(),
                    note.getRateType(), note.getIndexer(), note.getIndexerRate(), pa.isSettled(),
                    note.getMaturityDate() != null && !note.getMaturityDate().isAfter(hoje)))
                .stream())
            .sorted(Comparator.comparing(NoteOptionDTO::maturityDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(NoteOptionDTO::issuer, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * Gets transaction by ID.
     */
    public TransactionResponseDTO getTransactionById(UUID userId, UUID txId) {
        return toResponseDTO(findOwnedTransaction(userId, txId));
    }

    /**
     * Cria a transacao e reavalia a posicao inteira.
     *
     * <p>Cotas, preco medio e custo saem das transacoes na leitura
     * (PositionCalculator); o que e gravado e depende delas — proventos,
     * valorizacao e liquidacao — e regravado depois, junto com as validacoes.</p>
     */
    @Transactional
    public TransactionResponseDTO createTransaction(UUID userId, TransactionCreateDTO dto) {
        TransactionResponseDTO r = create(userId, dto, null);
        publicarEscrita(userId, dto.ticker());
        return r;
    }

    /** Liquidacao automatica: confere, depois do commit, se o titulo ja vencido precisa ser liquidado. */
    private void publicarEscrita(UUID userId, String ticker) {
        if (SystemWrites.active() || ticker == null) {
            return;
        }
        assetRepository.findByTicker(ticker)
            .ifPresent(a -> eventPublisher.publishEvent(new PositionWrittenEvent(userId, a.getId())));
    }

    /**
     * Grava uma operacao importada de uma integracao (docs/planos/PLANO_CRIPTO_BINANCE.md,
     * F4) pelas MESMAS regras do lancamento manual (P6): catalogo, regra A,
     * reavaliacao da posicao, proventos e valorizacao. Quais ativos cada fonte importa e das
     * regras dela (cripto na Binance; renda variavel listada na B3); aqui so fica de fora o que
     * a proposta nao sabe descrever — renda fixa, Tesouro e ativo de preco manual, que pedem
     * taxa, indice ou valor total (docs/planos/PLANO_INTEGRACAO_B3.md, G13).
     */
    @Transactional
    public TransactionResponseDTO importTransaction(UUID userId, ProposedTransaction proposta, ImportOrigin origem) {
        Asset asset = assetRepository.findByTicker(proposta.ticker())
            .orElseThrow(() -> new BusinessException("Ativo não encontrado no catálogo: " + proposta.ticker()));
        AssetType tipo = asset.getAssetType();
        boolean aceito = tipo != null
            && (tipo.isTreasury() || tipo.getPriceSource() != com.primevest.domain.asset.PriceSource.MANUAL);
        if (!aceito) {
            throw new BusinessException("Importação aceita cripto, renda variável listada e Tesouro Direto: "
                + proposta.ticker());
        }
        if (tipo.isTreasury() && proposta.type() == TransactionType.SELL) {
            // A venda de Tesouro e repartida entre os titulos (FIFO) e as linhas sao refeitas a cada
            // realocacao: a origem da fonte nao sobreviveria. Lancada pela tela (PLANO_INTEGRACAO_B3, F7).
            throw new BusinessException("Venda de Tesouro Direto não é importada: lance pela tela de transações.");
        }
        TransactionCreateDTO dto = new TransactionCreateDTO(proposta.ticker(), proposta.type(), proposta.shares(),
            proposta.pricePerShare(), null, proposta.brokerageFee(), proposta.transactionDate(), false, null,
            null, null, null, null, null, null);
        return create(userId, dto, origem);
    }

    private TransactionResponseDTO create(UUID userId, TransactionCreateDTO dto, ImportOrigin origem) {
        // O catálogo é a única origem de ativos. Ticker que não está lá não
        // vira ativo novo: vira erro. Antes, um erro de digitação criava um
        // ativo permanente e global, indistinguível de um cadastro legítimo.
        Asset asset = assetRepository.findByTicker(dto.ticker())
            .orElseThrow(() -> new BusinessException(
                "Ativo não encontrado no catálogo: " + dto.ticker()
                + ". Selecione um ativo existente na busca."));

        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
            .orElseThrow(() -> new BusinessException("Carteira não encontrada"));

        validateSettlementFlag(asset, dto);

        if (isTreasury(asset)) {
            return dto.type() == TransactionType.SELL
                ? createTreasurySale(portfolio, asset, dto)
                : createTreasuryPurchase(userId, portfolio, asset, dto, origem);
        }

        // O papel de renda fixa. Nulo para renda variavel.
        FixedIncomeNote note = resolveNote(userId, asset, dto);
        // Aporte num titulo que ja existe: a lista da tela traz todos (lancamento
        // retroativo existe), entao a data e quem diz se o titulo ainda rendia.
        if (dto.noteId() != null && dto.type() == TransactionType.BUY) {
            validateBuyBeforeMaturity(note, dto.transactionDate());
        }

        // Com papel, a posicao e do papel: dois CDBs de bancos diferentes sao
        // duas linhas na carteira, cada uma com sua taxa. Sem papel, continua
        // sendo uma linha por ativo — e a busca precisa ser outra, porque em
        // SQL nada casa com note_id nulo.
        PortfolioAsset pa = (note == null
                ? portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolio.getId(), asset.getId())
                : portfolioAssetRepository.findByPortfolioIdAndNoteId(portfolio.getId(), note.getId()))
            .orElse(null);

        if (pa == null) {
            // Regra A: a venda nao abre posicao. Antes, vender um ativo fora da
            // carteira criava a posicao e gravava um lancamento que o calculo
            // simplesmente ignorava.
            positionGuard.validateSellHasPosition(dto.type() != TransactionType.SELL, asset.getTicker());
            pa = portfolioAssetRepository.save(PortfolioAsset.builder()
                .portfolio(portfolio)
                .asset(asset)
                .note(note)
                .appreciation(BigDecimal.ZERO)
                .isReservation(dto.isReservation() != null && dto.isReservation())
                .build());
        } else if (dto.type() == TransactionType.SELL) {
            positionGuard.validateSellHasPosition(hasPurchase(positionCalculator.chronologicalTransactions(pa)),
                label(pa));
        }

        Valores valores = resolveValores(asset, dto);
        Transaction tx = Transaction.builder()
            .portfolioAsset(pa)
            .note(note)
            .type(dto.type())
            .shares(valores.shares())
            .pricePerShare(valores.pricePerShare())
            .totalAmount(valores.totalAmount())
            .brokerageFee(fee(dto))
            .transactionDate(dto.transactionDate())
            .fullSettlement(isFullSettlement(asset, dto))
            .origin(origem != null ? TransactionOrigin.AUTO
                : SystemWrites.active() ? TransactionOrigin.SISTEMA : TransactionOrigin.MANUAL)
            .connection(origem == null ? null : origem.connection())
            .externalId(origem == null ? null : origem.externalId())
            .executedAt(origem == null ? null : origem.executedAt())
            .sourceSnapshot(origem == null ? null : origem.sourceSnapshot())
            .build();

        tx = transactionRepository.save(tx);
        transactionRepository.flush();
        afterPositionChange(pa, dto.type() == TransactionType.SELL);
        return toResponseDTO(tx);
    }

    /**
     * Regra E4: volta a transacao importada aos valores da fonte e tira a marca
     * de editada. As regras de conversao sao reaplicadas sobre o movimento
     * guardado — o resultado e o mesmo da importacao.
     */
    @Transactional
    public TransactionResponseDTO restoreFromSource(UUID userId, UUID txId) {
        Transaction tx = findOwnedTransaction(userId, txId);
        if (tx.getOrigin() != TransactionOrigin.AUTO || tx.getSourceSnapshot() == null) {
            throw new BusinessException("Só transações importadas podem ser restauradas da corretora.");
        }
        ImportRules.Result r = importRules.forExternalId(tx.getExternalId())
            .apply(movementJson.read(tx.getSourceSnapshot()), assetRepository::findByTicker);
        if (!r.mapped()) {
            throw new BusinessException("Não foi possível restaurar: " + r.reason());
        }
        ProposedTransaction p = r.transaction();
        tx.setShares(p.shares());
        tx.setPricePerShare(p.pricePerShare());
        tx.setTotalAmount(p.shares().multiply(p.pricePerShare()));
        tx.setBrokerageFee(p.brokerageFee());
        tx.setTransactionDate(p.transactionDate());
        tx.setEditedAt(null);
        PortfolioAsset pa = tx.getPortfolioAsset();
        tx = transactionRepository.save(tx);
        transactionRepository.flush();
        afterPositionChange(pa, tx.getType() == TransactionType.SELL);
        return toResponseDTO(tx);
    }

    /**
     * Edita a transacao e reavalia a posicao inteira.
     *
     * <p>Only allows editing: shares, pricePerShare, brokerageFee,
     * transactionDate (e a marca de liquidacao total, na Renda Fixa). Does NOT
     * allow changing: type, ticker/asset.</p>
     *
     * <p>No Tesouro Direto o que se edita e a VENDA, nao a linha: a requisicao
     * traz a quantidade total que o usuario vendeu, e a reparticao entre
     * titulos e refeita a partir dela.</p>
     */
    @Transactional
    public TransactionResponseDTO updateTransaction(UUID userId, UUID txId, TransactionCreateDTO dto) {
        Transaction tx = findOwnedTransaction(userId, txId);
        PortfolioAsset pa = tx.getPortfolioAsset();
        Asset asset = pa.getAsset();
        Portfolio portfolio = pa.getPortfolio();

        validateSettlementFlag(asset, dto);

        if (isTreasury(asset)) {
            TransactionResponseDTO r = tx.getType() == TransactionType.SELL
                ? updateTreasurySale(portfolio, asset, tx, dto)
                : updateTreasuryPurchase(portfolio, asset, tx, dto);
            publicarEscrita(userId, asset.getTicker());
            return r;
        }

        // Only update allowed fields (quantity/price or total, fee, date)
        Valores valores = resolveValores(asset, dto);
        tx.setShares(valores.shares());
        tx.setPricePerShare(valores.pricePerShare());
        tx.setTotalAmount(valores.totalAmount());
        tx.setBrokerageFee(fee(dto));
        tx.setTransactionDate(dto.transactionDate());
        tx.setFullSettlement(isFullSettlement(asset, dto));
        // Regra E1: importada editada ganha a marca, e a sincronizacao nao mexe mais nela.
        if (tx.getOrigin() == TransactionOrigin.AUTO) {
            tx.setEditedAt(java.time.LocalDateTime.now());
        }
        // A venda automatica editada pelo usuario: a rotina nao a refaz mais (secao 6).
        if (tx.getOrigin() == TransactionOrigin.SISTEMA && !SystemWrites.active()) {
            tx.setEditedAt(java.time.LocalDateTime.now());
        }

        // Emissor, vencimento e taxa sao do papel, nao da movimentacao: editar
        // a transacao de um titulo edita o titulo.
        //
        // O hasNoteData nao e zelo: o modal de edicao hoje so mostra
        // quantidade, preco, data e taxas, e manda o resto nulo. Sem a guarda,
        // corrigir a quantidade de um CDB apagaria o emissor, o vencimento e a
        // taxa contratada dele.
        if (tx.getNote() != null && hasNoteData(dto)) {
            applyNoteFields(tx.getNote(), dto);
            fixedIncomeNoteRepository.save(tx.getNote());
        }
        if (tx.getNote() != null && tx.getType() == TransactionType.BUY) {
            validateBuyBeforeMaturity(tx.getNote(), tx.getTransactionDate());
        }

        boolean venda = tx.getType() == TransactionType.SELL;
        tx = transactionRepository.save(tx);
        transactionRepository.flush();
        afterPositionChange(pa, venda);
        publicarEscrita(userId, asset.getTicker());
        return toResponseDTO(tx);
    }

    /**
     * Exclui a transacao e reavalia a posicao inteira.
     *
     * <p>No Tesouro Direto, excluir uma linha de venda exclui a VENDA: as
     * partes sao a mesma operacao do usuario, e apagar uma delas deixaria a
     * outra sem sentido.</p>
     *
     * <p><b>Regra K</b> — posicao sem transacao deixa de existir, qualquer que
     * seja o tipo de ativo. Excluida a ultima transacao, a posicao sai junto,
     * com o titulo de renda fixa dela (ver {@link #removeIfEmpty}).</p>
     */
    @Transactional
    public void deleteTransaction(UUID userId, UUID txId) {
        Transaction tx = findOwnedTransaction(userId, txId);
        PortfolioAsset pa = tx.getPortfolioAsset();
        Asset asset = pa.getAsset();
        Portfolio portfolio = pa.getPortfolio();

        if (isTreasury(asset)) {
            if (tx.getType() == TransactionType.SELL) {
                deleteTreasurySale(portfolio, asset, tx);
            } else {
                deleteTreasuryPurchase(portfolio, asset, tx, pa);
            }
            return;
        }

        registrarExclusaoSeImportada(tx, asset);
        transactionRepository.delete(tx);
        transactionRepository.flush();
        if (removeIfEmpty(pa)) {
            return;
        }
        // Exclusao nunca corrige valorizacao para cima: o resgate que provava o
        // valor deixou de existir, ou a compra que sustentava o resgate saiu.
        afterPositionChange(pa, false);
    }

    /**
     * Regra E2: excluir uma importada registra a exclusao, para a proxima
     * sincronizacao nao recria-la. Restaurar e apagar esse registro.
     */
    private void registrarExclusaoSeImportada(Transaction tx, Asset asset) {
        if (tx.getOrigin() != TransactionOrigin.AUTO || tx.getConnection() == null || tx.getExternalId() == null) {
            return;
        }
        cryptoExclusionRepository.save(IntegrationCryptoExclusion.builder()
            .connection(tx.getConnection())
            .externalId(tx.getExternalId())
            .asset(asset)
            .sourceSnapshot(tx.getSourceSnapshot())
            .build());
    }

    /**
     * Regra K: posicao sem nenhuma transacao e apagada, com o titulo dela.
     *
     * <p>Tudo o que a posicao guarda deriva das transacoes: custo, cotas,
     * liquidacao, valorizacao e o total de cada provento. Sem transacao nao
     * sobra nada verdadeiro nela — sobrava, por exemplo, a valorizacao de uma
     * venda excluida, contando como patrimonio numa carteira vazia.</p>
     *
     * <p>Os proventos saem junto (cascade da entidade e ON DELETE CASCADE no
     * banco). Nao ha o que preservar: sem cotas em nenhuma data-com, o
     * DividendRecalculator ja os apagaria.</p>
     *
     * <p>O titulo de renda fixa (fixed_income_notes) e o papel DAQUELA
     * aplicacao: sem a posicao ele nao pertence a mais nada. Sai depois dela,
     * por causa da FK portfolio_assets.note_id.</p>
     *
     * @return true se a posicao foi apagada
     */
    private boolean removeIfEmpty(PortfolioAsset pa) {
        if (!transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId()).isEmpty()) {
            return false;
        }
        FixedIncomeNote note = pa.getNote();
        portfolioAssetRepository.delete(pa);
        portfolioAssetRepository.flush();
        if (note != null) {
            fixedIncomeNoteRepository.delete(note);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Tesouro Direto
    // ------------------------------------------------------------------

    /**
     * Compra de Tesouro: sempre um titulo novo.
     *
     * <p>A taxa e do dia, entao duas compras do mesmo papel em datas diferentes
     * sao dois titulos com rentabilidades diferentes. Comprar de novo no MESMO
     * dia, porem, nao e um titulo novo — e a mesma aplicacao com mais dinheiro
     * (regra H).</p>
     */
    private TransactionResponseDTO createTreasuryPurchase(UUID userId, Portfolio portfolio,
                                                          Asset asset, TransactionCreateDTO dto, ImportOrigin origem) {
        if (dto.noteId() != null) {
            throw new BusinessException(
                "Cada compra de Tesouro Direto é um título novo: não se lança sobre um título existente.");
        }

        List<Transaction> current = transactionRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId());
        validateOnePurchasePerDate(current, asset, dto.transactionDate(), null);

        FixedIncomeNote note = FixedIncomeNote.builder()
            .user(userRepository.getReferenceById(userId))
            .asset(asset)
            .purchaseDate(dto.transactionDate())
            .build();
        applyNoteFields(note, dto);
        note = fixedIncomeNoteRepository.save(note);

        PortfolioAsset pa = portfolioAssetRepository.save(PortfolioAsset.builder()
            .portfolio(portfolio)
            .asset(asset)
            .note(note)
            .appreciation(BigDecimal.ZERO)
            .isReservation(dto.isReservation() != null && dto.isReservation())
            .build());

        Valores valores = resolveValores(asset, dto);
        Transaction tx = transactionRepository.save(Transaction.builder()
            .portfolioAsset(pa)
            .note(note)
            .type(TransactionType.BUY)
            .shares(valores.shares())
            .pricePerShare(valores.pricePerShare())
            .totalAmount(valores.totalAmount())
            .brokerageFee(fee(dto))
            .transactionDate(dto.transactionDate())
            // Compra de Tesouro importada (B3, F7): uma linha so, que a realocacao das vendas nao refaz.
            .origin(origem == null ? TransactionOrigin.MANUAL : TransactionOrigin.AUTO)
            .connection(origem == null ? null : origem.connection())
            .externalId(origem == null ? null : origem.externalId())
            .executedAt(origem == null ? null : origem.executedAt())
            .sourceSnapshot(origem == null ? null : origem.sourceSnapshot())
            .build());
        transactionRepository.flush();

        // Regra 4.2: a compra pode ser retroativa e passar a ser o titulo mais
        // antigo. Quando isso acontece, as vendas ja lancadas mudam de titulo.
        reallocate(portfolio, asset, null, null);
        return toResponseDTO(tx);
    }

    /**
     * Venda de Tesouro: do ativo, repartida entre os titulos mais antigos.
     *
     * <p>O titulo nao e informado (regra C, na forma que vale para o Tesouro):
     * quem escolhe e o FIFO.</p>
     */
    private TransactionResponseDTO createTreasurySale(Portfolio portfolio, Asset asset,
                                                      TransactionCreateDTO dto) {
        if (dto.noteId() != null) {
            throw new BusinessException(
                "A venda de Tesouro Direto é do ativo: o título sai do mais antigo para o mais novo,"
                + " e não se escolhe na tela.");
        }
        if (dto.shares() == null || dto.pricePerShare() == null) {
            throw new BusinessException("Informe a quantidade e o preço unitário.");
        }

        List<Transaction> current = transactionRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId());
        positionGuard.validateSellHasPosition(hasPurchase(current), asset.getTicker());

        SaleIntent nova = new SaleIntent(UUID.randomUUID(), dto.transactionDate(), dto.shares(),
            dto.pricePerShare(), fee(dto), LocalDateTime.now(),
            SystemWrites.active() ? TransactionOrigin.SISTEMA : TransactionOrigin.MANUAL, null);

        List<Transaction> rows = reallocate(portfolio, asset, nova, null).get(nova.groupId());
        return toResponseDTO(rows.get(0));
    }

    /** Edicao da venda inteira: a quantidade informada e a do grupo. */
    private TransactionResponseDTO updateTreasurySale(Portfolio portfolio, Asset asset,
                                                      Transaction tx, TransactionCreateDTO dto) {
        if (dto.shares() == null || dto.pricePerShare() == null) {
            throw new BusinessException("Informe a quantidade e o preço unitário.");
        }
        UUID groupId = groupKey(tx);
        // Liquidacao automatica editada pelo usuario continua SISTEMA, com a marca de editada (secao 6).
        boolean sistema = tx.getOrigin() == TransactionOrigin.SISTEMA;
        SaleIntent alterada = new SaleIntent(groupId, dto.transactionDate(), dto.shares(),
            dto.pricePerShare(), fee(dto), tx.getCreatedAt(),
            sistema ? TransactionOrigin.SISTEMA : TransactionOrigin.MANUAL,
            !sistema ? null : SystemWrites.active() ? tx.getEditedAt() : LocalDateTime.now());

        List<Transaction> rows = reallocate(portfolio, asset, alterada, null).get(groupId);
        return toResponseDTO(rows.get(0));
    }

    /** Edicao da compra: muda o titulo, e com ele a ordem do FIFO. */
    private TransactionResponseDTO updateTreasuryPurchase(Portfolio portfolio, Asset asset,
                                                          Transaction tx, TransactionCreateDTO dto) {
        List<Transaction> current = transactionRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId());
        validateOnePurchasePerDate(current, asset, dto.transactionDate(), tx.getId());

        Valores valores = resolveValores(asset, dto);
        tx.setShares(valores.shares());
        tx.setPricePerShare(valores.pricePerShare());
        tx.setTotalAmount(valores.totalAmount());
        tx.setBrokerageFee(fee(dto));
        tx.setTransactionDate(dto.transactionDate());

        FixedIncomeNote note = tx.getNote();
        if (note != null) {
            // A data da compra E a identidade do titulo no Tesouro: e ela que
            // ordena o FIFO e a que a regra H torna unica.
            note.setPurchaseDate(dto.transactionDate());
            if (hasNoteData(dto)) {
                applyNoteFields(note, dto);
            }
            fixedIncomeNoteRepository.save(note);
        }

        tx = transactionRepository.save(tx);
        transactionRepository.flush();
        reallocate(portfolio, asset, null, null);
        return toResponseDTO(tx);
    }

    /** Exclusao da venda inteira: as partes sao a mesma operacao. */
    private void deleteTreasurySale(Portfolio portfolio, Asset asset, Transaction tx) {
        reallocate(portfolio, asset, null, groupKey(tx));
    }

    /**
     * Exclusao da compra: o titulo deixa de existir e as vendas que saiam dele
     * sao redistribuidas. Se nao houver de onde tira-las, a exclusao falha.
     */
    private void deleteTreasuryPurchase(Portfolio portfolio, Asset asset, Transaction tx, PortfolioAsset pa) {
        transactionRepository.delete(tx);
        transactionRepository.flush();
        reallocate(portfolio, asset, null, null);

        // O titulo era aquela compra. Sem ela, nao sobrou titulo nenhum: deixar
        // a posicao vazia manteria a data ocupada para a regra H. Regra K: sai
        // mesmo que tenha tido provento.
        removeIfEmpty(pa);
    }

    /**
     * Refaz a reparticao das vendas do ativo e reavalia todos os titulos dele.
     *
     * @param alterada venda a incluir ou a substituir; nulo quando a mudanca
     *                 foi numa compra
     * @param removida grupo de venda a excluir; nulo quando nada foi excluido
     */
    private Map<UUID, List<Transaction>> reallocate(Portfolio portfolio, Asset asset,
                                                    SaleIntent alterada, UUID removida) {
        List<Transaction> current = transactionRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId());

        List<SaleIntent> sales = new ArrayList<>(treasuryAllocator.salesOf(current));
        if (removida != null) {
            sales.removeIf(sale -> sale.groupId().equals(removida));
        }
        if (alterada != null) {
            sales.removeIf(sale -> sale.groupId().equals(alterada.groupId()));
            sales.add(alterada);
        }

        Map<UUID, List<Transaction>> rows = treasuryAllocator.apply(portfolio, asset, current, sales);
        refreshTreasuryPositions(portfolio, asset);
        return rows;
    }

    /** Proventos, valorizacao e regra I em cada titulo do ativo. */
    private void refreshTreasuryPositions(Portfolio portfolio, Asset asset) {
        for (PortfolioAsset pa : portfolioAssetRepository
                .findAllByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())) {
            List<Transaction> chronological = positionCalculator.chronologicalTransactions(pa);
            Position position = positionCalculator.calculate(false, chronological);
            // A liquidacao do titulo ja foi gravada pelo alocador, a partir das
            // cotas que sobraram dele.
            positionGuard.validateApplied(position, pa.isSettled(), !chronological.isEmpty(), label(pa));
            dividendRecalculator.recalculate(pa, chronological);
            appreciationUpdater.refresh(pa);
        }
    }

    /**
     * Regra H: um ativo do Tesouro nao tem dois titulos comprados no mesmo dia.
     *
     * <p>Alem de tornar o FIFO deterministico, ela evita a duplicata que nao
     * quer dizer nada: comprar o mesmo papel duas vezes no mesmo dia e uma
     * aplicacao so, com mais dinheiro.</p>
     */
    /**
     * Compra de renda fixa so ate a vespera do vencimento: no dia do vencimento
     * o titulo ja esta vencido (a mesma regra do selo "Vencido" da carteira) e
     * nao rende mais nada. A venda nao passa por aqui — resgatar um titulo
     * vencido e justamente o que encerra a aplicacao.
     */
    private void validateBuyBeforeMaturity(FixedIncomeNote note, LocalDate date) {
        LocalDate vencimento = note != null ? note.getMaturityDate() : null;
        if (vencimento != null && date != null && !vencimento.isAfter(date)) {
            throw new BusinessException("Título vencido para a data de transação escolhida");
        }
    }

    private void validateOnePurchasePerDate(List<Transaction> current, Asset asset,
                                            LocalDate date, UUID ignoreTxId) {
        boolean existe = current.stream()
            .filter(tx -> tx.getType() == TransactionType.BUY)
            .filter(tx -> ignoreTxId == null || !tx.getId().equals(ignoreTxId))
            .anyMatch(tx -> tx.getTransactionDate().equals(date));

        if (existe) {
            throw new BusinessException(
                "Já existe um título de " + asset.getTicker() + " comprado em " + date.format(DATA)
                + ". Para aplicar mais nessa data, altere a quantidade da compra que já existe.");
        }
    }

    // ------------------------------------------------------------------
    // Reavaliacao de uma posicao
    // ------------------------------------------------------------------

    /**
     * Reavalia a posicao depois de qualquer escrita nela.
     *
     * <p>Nesta ordem, porque uma coisa depende da outra:</p>
     * <ol>
     *   <li><b>saldo</b> — regra B nos tipos por cota, regras E, F e G na Renda
     *   Fixa (que tambem regravam valorizacao e liquidacao, mas so corrigem a
     *   valorizacao quando a escrita foi numa venda);</li>
     *   <li><b>liquidacao por cota</b> — posicao zerada liquida, posicao que
     *   voltou a ter cotas reabre (regras 3.2 e J);</li>
     *   <li><b>regra I</b> — posicao em aberto tem valor aplicado;</li>
     *   <li><b>proventos</b> — dividends.total_amount depende das cotas na
     *   data-com: um lancamento retroativo muda o total de um provento antigo;</li>
     *   <li><b>valorizacao de mercado</b> — cotas x cotacao - custo, nas
     *   posicoes com cotacao.</li>
     * </ol>
     */
    private void afterPositionChange(PortfolioAsset pa, boolean vendaLancada) {
        List<Transaction> chronological = positionCalculator.chronologicalTransactions(pa);
        boolean valuedByTotal = isValuedByTotal(pa.getAsset());
        String label = label(pa);

        List<StockEventStep> stockEvents = valuedByTotal ? List.of() : positionCalculator.stockEventsOf(pa.getAsset());
        if (valuedByTotal) {
            fixedIncomeSettlement.recompute(pa, chronological, vendaLancada);
            // a curva contratada (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R4) depois das regras da escrita
            fixedIncomeValuation.apply(pa, chronological, fixedIncomeValuation.series());
        } else {
            // Regra B com os desdobramentos, grupamentos e bonificacoes do
            // papel no meio da linha do tempo: o saldo que sobra e o de hoje.
            BigDecimal saldo = positionGuard.validateShareLedger(chronological, stockEvents, label);
            pa.setSettled(!chronological.isEmpty() && saldo.signum() <= 0);
        }

        // Regra I sobre as transacoes e os eventos societarios (a venda de 200
        // depois de um 1:2 so fecha com o evento), mas SEM as devolucoes de
        // capital: uma amortizacao que zere o custo nao pode recusar uma
        // edicao valida.
        Position position = stockEvents.isEmpty()
            ? positionCalculator.calculate(valuedByTotal, chronological)
            : positionCalculator.calculate(valuedByTotal, chronological, PositionEvents.ofStockEvents(stockEvents));
        positionGuard.validateApplied(position, pa.isSettled(), !chronological.isEmpty(), label);
        portfolioAssetRepository.save(pa);

        dividendRecalculator.recalculate(pa, chronological);
        appreciationUpdater.refresh(pa);
        // Compra retroativa pode precisar do historico longo de proventos: a
        // busca externa roda depois do commit, fora desta transacao.
        eventPublisher.publishEvent(new PositionChangedEvent(pa.getAsset().getId()));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private boolean isTreasury(Asset asset) {
        AssetType type = asset != null ? asset.getAssetType() : null;
        return type != null && type.isTreasury();
    }

    private boolean isValuedByTotal(Asset asset) {
        AssetType type = asset != null ? asset.getAssetType() : null;
        return type != null && type.isValuedByTotal();
    }

    private boolean hasPurchase(Collection<Transaction> transactions) {
        return transactions.stream().anyMatch(tx -> tx.getType() == TransactionType.BUY);
    }

    private BigDecimal fee(TransactionCreateDTO dto) {
        return dto.brokerageFee() != null ? dto.brokerageFee() : BigDecimal.ZERO;
    }

    /** A marca de liquidacao total so existe em venda de Renda Fixa. */
    private boolean isFullSettlement(Asset asset, TransactionCreateDTO dto) {
        return Boolean.TRUE.equals(dto.isFullSettlement())
            && dto.type() == TransactionType.SELL
            && isValuedByTotal(asset);
    }

    private void validateSettlementFlag(Asset asset, TransactionCreateDTO dto) {
        if (Boolean.TRUE.equals(dto.isFullSettlement())
                && (dto.type() != TransactionType.SELL || !isValuedByTotal(asset))) {
            throw new BusinessException(
                "Liquidação total só existe em venda de Renda Fixa.");
        }
    }

    private UUID groupKey(Transaction tx) {
        return tx.getSaleGroupId() != null ? tx.getSaleGroupId() : tx.getId();
    }

    private String label(PortfolioAsset pa) {
        String ticker = pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        String titulo = pa.getNote() != null ? pa.getNote().getLabel() : "";
        return titulo != null && !titulo.isBlank() ? ticker + " (" + titulo + ")" : ticker;
    }

    /** Quantidade, preco unitario e total que a transacao vai gravar. */
    private record Valores(BigDecimal shares, BigDecimal pricePerShare, BigDecimal totalAmount) {}

    /**
     * Decide como a transacao e valorada a partir do tipo do ativo.
     *
     * <p>Renda Fixa: o total vem do usuario, e quantidade e preco sao nulos.
     * Demais tipos: quantidade e preco sao obrigatorios e o total e calculado —
     * o que vier em totalAmount e ignorado, para que nao existam duas fontes
     * para o mesmo numero.</p>
     *
     * <p>Quantidade ou preco numa transacao de Renda Fixa sao recusados em vez
     * de descartados: descartar em silencio esconderia um cliente desatualizado
     * mandando o formulario antigo.</p>
     */
    private Valores resolveValores(Asset asset, TransactionCreateDTO dto) {
        if (isValuedByTotal(asset)) {
            if (dto.shares() != null || dto.pricePerShare() != null) {
                throw new BusinessException(
                    "Renda Fixa é lançada pelo valor total, sem quantidade nem preço unitário.");
            }
            if (dto.totalAmount() == null) {
                throw new BusinessException("Informe o valor da transação.");
            }
            return new Valores(null, null, dto.totalAmount());
        }

        if (dto.shares() == null || dto.pricePerShare() == null) {
            throw new BusinessException("Informe a quantidade e o preço unitário.");
        }
        return new Valores(dto.shares(), dto.pricePerShare(), dto.shares().multiply(dto.pricePerShare()));
    }

    /** O tipo de remuneracao por extenso, para a mensagem sair legivel. */
    private String remuneracao(RateType rateType) {
        if (rateType == null) {
            return "";
        }
        return switch (rateType) {
            case PREFIXADO -> "prefixada";
            case POS_FIXADO -> "pós-fixada";
            case HIBRIDO -> "híbrida";
        };
    }

    /**
     * Carrega a transacao garantindo que ela e do usuario.
     *
     * Transacao de outro dono responde "nao encontrada" de proposito: dizer
     * "sem permissao" confirmaria que o id existe, e um id que existe e o que
     * um atacante procura. Mesmo criterio de GoalService.findOwnedGoal.
     */
    private Transaction findOwnedTransaction(UUID userId, UUID txId) {
        return transactionRepository.findOwnedById(userId, txId)
            .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
    }

    /**
     * A requisicao trouxe algum dado de titulo? Distingue "o usuario limpou o
     * emissor" de "esta tela nem tem esses campos".
     */
    private boolean hasNoteData(TransactionCreateDTO dto) {
        return dto.issuer() != null
            || dto.maturityDate() != null
            || dto.rateType() != null
            || dto.indexer() != null
            || dto.indexerRate() != null;
    }

    /**
     * Resolve o papel de renda fixa da movimentacao.
     *
     * <p>Tres caminhos: renda variavel nao tem papel; um noteId aponta para um
     * papel que ja existe (mais de um lancamento sobre a mesma aplicacao); e a
     * ausencia de noteId numa compra significa papel novo, que nasce junto com
     * a transacao.</p>
     *
     * <p>Venda sem noteId e recusada de proposito (regra C). Criar um papel novo
     * numa venda produziria uma posicao negativa de um titulo que nunca foi
     * comprado — o erro e mais barato que o dado errado.</p>
     *
     * <p>O Tesouro Direto nao passa por aqui: la o titulo nasce de cada compra
     * e a venda escolhe os titulos por FIFO.</p>
     */
    private FixedIncomeNote resolveNote(UUID userId, Asset asset, TransactionCreateDTO dto) {
        boolean rendaFixa = asset.getAssetType() != null && asset.getAssetType().isFixedIncome();

        if (!rendaFixa) {
            if (dto.noteId() != null) {
                throw new BusinessException(
                    "Título informado para um ativo que não é de renda fixa: " + asset.getTicker());
            }
            return null;
        }

        if (dto.noteId() != null) {
            // Escopado pelo dono: o id vem do cliente, e sem o filtro por
            // usuario daria para lancar transacao sobre o papel de outro.
            FixedIncomeNote existente = fixedIncomeNoteRepository
                .findByIdAndUserId(dto.noteId(), userId)
                .orElseThrow(() -> new BusinessException("Título não encontrado"));

            if (!existente.getAsset().getId().equals(asset.getId())) {
                throw new BusinessException(
                    "O título selecionado pertence a outro ativo: " + existente.getAsset().getTicker());
            }
            return existente;
        }

        if (dto.type() == TransactionType.SELL) {
            throw new BusinessException(
                "Venda de renda fixa precisa apontar qual título está sendo vendido.");
        }

        FixedIncomeNote nova = FixedIncomeNote.builder()
            .user(userRepository.getReferenceById(userId))
            .asset(asset)
            .build();
        applyNoteFields(nova, dto);
        return fixedIncomeNoteRepository.save(nova);
    }

    /**
     * Copia os dados do titulo do DTO para o papel, caindo nos parametros
     * publicos do ativo quando o DTO se cala.
     *
     * <p>A queda existe para o Tesouro Direto: vencimento e forma de remuneracao
     * de "Tesouro IPCA+ 2029" sao do papel e ja estao em asset_terms, entao o
     * usuario so digita a taxa do dia. Num CDB nao ha o que herdar e tudo vem
     * do formulario.</p>
     *
     * <p>Copia, e nao referencia: o que vale para o calculo e o que foi
     * contratado, e uma correcao futura no catalogo nao deve reescrever uma
     * posicao ja registrada.</p>
     */
    private void applyNoteFields(FixedIncomeNote note, TransactionCreateDTO dto) {
        AssetTerms terms = note.getAsset() != null ? note.getAsset().getTerms() : null;

        note.setIssuer(dto.issuer());
        note.setMaturityDate(dto.maturityDate() != null ? dto.maturityDate()
            : (terms != null ? terms.getMaturityDate() : null));

        RateType rateType = dto.rateType() != null ? dto.rateType()
            : (terms != null ? terms.getRateType() : null);
        Indexer indexer = dto.indexer() != null ? dto.indexer()
            : (terms != null ? terms.getIndexer() : null);

        // Prefixado nao tem indice. Sem esta limpeza, trocar o tipo no
        // formulario deixaria o indice antigo para tras e o banco recusaria a
        // linha inteira com uma violacao de CHECK, que nao diz nada ao usuario.
        if (rateType == RateType.PREFIXADO) {
            indexer = null;
        }
        if ((rateType == RateType.POS_FIXADO || rateType == RateType.HIBRIDO) && indexer == null) {
            throw new BusinessException(
                "Remuneração " + remuneracao(rateType) + " precisa de um índice (CDI, IPCA, ...).");
        }
        if (rateType == null && indexer != null) {
            throw new BusinessException("Informe como o título remunera antes de escolher o índice.");
        }

        note.setRateType(rateType);
        note.setIndexer(indexer);
        note.setIndexerRate(dto.indexerRate());
    }

    // ------------------------------------------------------------------
    // Resposta
    // ------------------------------------------------------------------

    /** A venda do usuario, somada de volta a partir das partes gravadas. */
    private record GroupInfo(List<UUID> ids, BigDecimal shares, BigDecimal fee) {}

    private Map<UUID, GroupInfo> groupsOf(Collection<Transaction> transactions) {
        Map<UUID, List<Transaction>> byGroup = transactions.stream()
            .filter(tx -> tx.getSaleGroupId() != null)
            .collect(Collectors.groupingBy(Transaction::getSaleGroupId));

        Map<UUID, GroupInfo> groups = new HashMap<>();
        byGroup.forEach((groupId, rows) -> {
            List<Transaction> ordered = rows.stream()
                .sorted(Comparator.comparing(Transaction::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(tx -> tx.getId().toString()))
                .toList();
            BigDecimal shares = BigDecimal.ZERO;
            BigDecimal fee = BigDecimal.ZERO;
            for (Transaction row : ordered) {
                shares = shares.add(row.getShares() != null ? row.getShares() : BigDecimal.ZERO);
                fee = fee.add(row.getBrokerageFee() != null ? row.getBrokerageFee() : BigDecimal.ZERO);
            }
            groups.put(groupId, new GroupInfo(ordered.stream().map(Transaction::getId).toList(), shares, fee));
        });
        return groups;
    }

    private TransactionResponseDTO toResponseDTO(Transaction tx) {
        Map<UUID, GroupInfo> groups = tx.getSaleGroupId() != null
            ? groupsOf(transactionRepository.findBySaleGroupId(tx.getSaleGroupId()))
            : Map.of();
        return toResponseDTO(tx, groups);
    }

    private TransactionResponseDTO toResponseDTO(Transaction tx, Map<UUID, GroupInfo> groups) {
        PortfolioAsset pa = tx.getPortfolioAsset();
        Asset asset = pa != null ? pa.getAsset() : null;
        String ticker = pa != null && pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        String assetName = pa != null && pa.getAsset() != null ? pa.getAsset().getName() : "";
        var assetType = pa != null && pa.getAsset() != null ? pa.getAsset().getAssetType() : null;
        Long assetTypeId = assetType != null ? assetType.getId() : null;
        String assetTypeName = assetType != null
                ? (assetType.getDescription() != null ? assetType.getDescription() : assetType.getName()) : "";

        // Categoria do ativo, para o filtro da listagem. getDisplayName pela
        // mesma convencao do tipo: a tela mostra o rotulo, nunca a chave.
        var assetCategory = pa != null && pa.getAsset() != null ? pa.getAsset().getAssetCategory() : null;
        Long assetCategoryId = assetCategory != null ? assetCategory.getId() : null;
        String assetCategoryName = assetCategory != null ? assetCategory.getDisplayName() : "";

        // O titulo vai junto: a edicao reabre o formulario com ele, e sem esses
        // campos salvar por cima apagaria emissor, vencimento e taxa.
        FixedIncomeNote note = tx.getNote();

        GroupInfo group = tx.getSaleGroupId() != null ? groups.get(tx.getSaleGroupId()) : null;
        Integer part = group != null ? group.ids().indexOf(tx.getId()) + 1 : null;
        Integer parts = group != null ? group.ids().size() : null;

        return new TransactionResponseDTO(
            tx.getId(),
            pa != null ? pa.getId() : null,
            ticker, assetName, assetTypeId, assetTypeName,
            assetCategoryId, assetCategoryName, tx.getType(),
            tx.getShares(), tx.getPricePerShare(), tx.getTotalAmount(),
            tx.getBrokerageFee(), tx.getTransactionDate(), tx.getCreatedAt(),
            tx.getSaleGroupId(), part, parts,
            group != null ? group.shares() : tx.getShares(),
            group != null ? group.fee()    : tx.getBrokerageFee(),
            tx.isFullSettlement(),
            assetType != null && assetType.isCrypto(),
            tx.getOrigin(),
            tx.getEditedAt(),
            tx.getConnection() != null ? tx.getConnection().getLabel() : null,
            tx.getConnection() != null ? tx.getConnection().getProvider().name() : null,
            note != null ? note.getId()           : null,
            note != null ? note.getIssuer()       : null,
            note != null ? note.getMaturityDate() : null,
            note != null ? note.getRateType()     : null,
            note != null ? note.getIndexer()      : null,
            note != null ? note.getIndexerRate()  : null,
            asset != null ? assetLogos.logoFlag(asset) : null,
            asset != null && asset.getIssuer() != null ? asset.getIssuer().getId() : null
        );
    }
}
