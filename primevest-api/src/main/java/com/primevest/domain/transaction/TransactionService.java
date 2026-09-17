package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetTerms;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.dividend.DividendRecalculator;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeNoteRepository;
import com.primevest.domain.portfolio.AppreciationUpdater;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.user.UserRepository;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final AssetRepository assetRepository;
    private final FixedIncomeNoteRepository fixedIncomeNoteRepository;
    private final UserRepository userRepository;
    private final DividendRecalculator dividendRecalculator;
    private final PositionCalculator positionCalculator;
    private final AppreciationUpdater appreciationUpdater;

    /**
     * Gets user transactions.
     */
    public List<TransactionResponseDTO> getTransactions(UUID userId) {
        List<Transaction> transactions = transactionRepository.findAllTransactionsByUserId(userId);
        return transactions.stream()
            .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed()
                .thenComparing(Comparator.comparing(Transaction::getCreatedAt).reversed()))
            .map(this::toResponseDTO)
            .collect(Collectors.toList());
    }

    /**
     * Gets transaction by ID.
     */
    public TransactionResponseDTO getTransactionById(UUID userId, UUID txId) {
        return toResponseDTO(findOwnedTransaction(userId, txId));
    }

    /**
     * Cria a transacao. Cotas, preco medio e custo saem das transacoes na
     * leitura (PositionCalculator); o que e gravado e depende deles e regravado
     * em {@link #afterPositionChange}.
     */
    @Transactional
    public TransactionResponseDTO createTransaction(UUID userId, TransactionCreateDTO dto) {
        // O catálogo é a única origem de ativos. Ticker que não está lá não
        // vira ativo novo: vira erro. Antes, um erro de digitação criava um
        // ativo permanente e global, indistinguível de um cadastro legítimo.
        Asset asset = assetRepository.findByTicker(dto.ticker())
            .orElseThrow(() -> new BusinessException(
                "Ativo não encontrado no catálogo: " + dto.ticker()
                + ". Selecione um ativo existente na busca."));

        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
            .orElseThrow(() -> new BusinessException("Portfolio não encontrado"));

        // O papel de renda fixa. Nulo para renda variavel.
        FixedIncomeNote note = resolveNote(userId, asset, dto);

        // Com papel, a posicao e do papel: dois CDBs de bancos diferentes sao
        // duas linhas na carteira, cada uma com sua taxa. Sem papel, continua
        // sendo uma linha por ativo — e a busca precisa ser outra, porque em
        // SQL nada casa com note_id nulo.
        PortfolioAsset pa = (note == null
                ? portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolio.getId(), asset.getId())
                : portfolioAssetRepository.findByPortfolioIdAndNoteId(portfolio.getId(), note.getId()))
            .orElseGet(() -> {
                PortfolioAsset newPa = PortfolioAsset.builder()
                    .portfolio(portfolio)
                    .asset(asset)
                    .note(note)
                    .appreciation(BigDecimal.ZERO)
                    .isReservation(dto.isReservation() != null && dto.isReservation())
                    .build();
                return portfolioAssetRepository.save(newPa);
            });

        Valores valores = resolveValores(asset, dto);
        Transaction tx = Transaction.builder()
            .portfolioAsset(pa)
            .note(note)
            .type(dto.type())
            .shares(valores.shares())
            .pricePerShare(valores.pricePerShare())
            .totalAmount(valores.totalAmount())
            .brokerageFee(dto.brokerageFee() != null ? dto.brokerageFee() : BigDecimal.ZERO)
            .transactionDate(dto.transactionDate())
            .build();

        tx = transactionRepository.save(tx);
        afterPositionChange(pa);
        return toResponseDTO(tx);
    }


    /**
     * Edita a transacao e regrava o que depende da posicao.
     * Only allows editing: shares, pricePerShare, brokerageFee, transactionDate.
     * Does NOT allow changing: type, ticker/asset.
     */
    @Transactional
    public TransactionResponseDTO updateTransaction(UUID userId, UUID txId, TransactionCreateDTO dto) {
        Transaction tx = findOwnedTransaction(userId, txId);

        // Only update allowed fields (quantity/price or total, fee, date)
        Valores valores = resolveValores(tx.getPortfolioAsset().getAsset(), dto);
        tx.setShares(valores.shares());
        tx.setPricePerShare(valores.pricePerShare());
        tx.setTotalAmount(valores.totalAmount());
        tx.setBrokerageFee(dto.brokerageFee() != null ? dto.brokerageFee() : BigDecimal.ZERO);
        tx.setTransactionDate(dto.transactionDate());

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

        tx = transactionRepository.save(tx);
        afterPositionChange(tx.getPortfolioAsset());
        return toResponseDTO(tx);
    }

    /**
     * Exclui a transacao e regrava o que depende da posicao.
     */
    @Transactional
    public void deleteTransaction(UUID userId, UUID txId) {
        Transaction tx = findOwnedTransaction(userId, txId);
        PortfolioAsset pa = tx.getPortfolioAsset();
        transactionRepository.delete(tx);
        transactionRepository.flush();
        afterPositionChange(pa);
    }

    /**
     * Regrava o que e gravado e depende das cotas ou do custo da posicao.
     *
     * <p>Cotas, preco medio e custo nao sao gravados (V28). Mas dois valores
     * gravados dependem deles:</p>
     * <ul>
     *   <li>dividends.total_amount — cotas na data-com: um lancamento
     *   retroativo muda o total de um provento antigo;</li>
     *   <li>portfolio_assets.appreciation, nas posicoes com cotacao — cotas x
     *   cotacao - custo.</li>
     * </ul>
     */
    private void afterPositionChange(PortfolioAsset pa) {
        dividendRecalculator.recalculate(pa, positionCalculator.chronologicalTransactions(pa));
        appreciationUpdater.refresh(pa);
    }

    private boolean isValuedByTotal(Asset asset) {
        return asset != null && asset.getAssetType() != null && asset.getAssetType().isValuedByTotal();
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
     * <p>Venda sem noteId e recusada de proposito. Criar um papel novo numa
     * venda produziria uma posicao negativa de um titulo que nunca foi
     * comprado — o erro e mais barato que o dado errado.</p>
     */
    private FixedIncomeNote resolveNote(UUID userId, Asset asset, TransactionCreateDTO dto) {
        boolean rendaFixa = asset.getAssetType() != null && asset.getAssetType().isFixedIncome();

        if (!rendaFixa) {
            if (dto.noteId() != null) {
                throw new BusinessException(
                    "Titulo informado para um ativo que nao e de renda fixa: " + asset.getTicker());
            }
            return null;
        }

        if (dto.noteId() != null) {
            // Escopado pelo dono: o id vem do cliente, e sem o filtro por
            // usuario daria para lancar transacao sobre o papel de outro.
            FixedIncomeNote existente = fixedIncomeNoteRepository
                .findByIdAndUserId(dto.noteId(), userId)
                .orElseThrow(() -> new BusinessException("Titulo nao encontrado"));

            if (!existente.getAsset().getId().equals(asset.getId())) {
                throw new BusinessException(
                    "O titulo selecionado pertence a outro ativo: " + existente.getAsset().getTicker());
            }
            return existente;
        }

        if (dto.type() == TransactionType.SELL) {
            throw new BusinessException(
                "Venda de renda fixa precisa apontar qual titulo esta sendo vendido.");
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
                "Remuneracao " + rateType + " precisa de um indice (CDI, IPCA, ...).");
        }
        if (rateType == null && indexer != null) {
            throw new BusinessException("Informe como o titulo remunera antes de escolher o indice.");
        }

        note.setRateType(rateType);
        note.setIndexer(indexer);
        note.setIndexerRate(dto.indexerRate());
    }

    private TransactionResponseDTO toResponseDTO(Transaction tx) {
        PortfolioAsset pa = tx.getPortfolioAsset();
        String ticker = pa != null && pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        String assetName = pa != null && pa.getAsset() != null ? pa.getAsset().getName() : "";
        var assetType = pa != null && pa.getAsset() != null ? pa.getAsset().getAssetType() : null;
        Long assetTypeId = assetType != null ? assetType.getId() : null;
        String assetTypeName = assetType != null
                ? (assetType.getDescription() != null ? assetType.getDescription() : assetType.getName()) : "";

        // O titulo vai junto: a edicao reabre o formulario com ele, e sem esses
        // campos salvar por cima apagaria emissor, vencimento e taxa.
        FixedIncomeNote note = tx.getNote();

        return new TransactionResponseDTO(
            tx.getId(), ticker, assetName, assetTypeId, assetTypeName, tx.getType(),
            tx.getShares(), tx.getPricePerShare(), tx.getTotalAmount(),
            tx.getBrokerageFee(), tx.getTransactionDate(), tx.getCreatedAt(),
            note != null ? note.getId()           : null,
            note != null ? note.getIssuer()       : null,
            note != null ? note.getMaturityDate() : null,
            note != null ? note.getRateType()     : null,
            note != null ? note.getIndexer()      : null,
            note != null ? note.getIndexerRate()  : null
        );
    }
}
