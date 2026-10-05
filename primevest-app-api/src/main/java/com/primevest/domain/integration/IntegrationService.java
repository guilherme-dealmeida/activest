package com.primevest.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.integration.dto.ConfirmImportDTO;
import com.primevest.domain.integration.dto.ExclusionDTO;
import com.primevest.domain.integration.pipeline.ImportRules;
import com.primevest.domain.integration.source.MovementJson;
import com.primevest.domain.transaction.ImportOrigin;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionRepository;
import com.primevest.domain.transaction.TransactionService;
import com.primevest.domain.integration.credential.ApiKeyCredentials;
import com.primevest.domain.integration.credential.InvestorDocument;
import com.primevest.domain.integration.credential.SourceCredentials;
import com.primevest.domain.integration.credential.CredentialCipher;
import com.primevest.domain.integration.credential.CredentialVault;
import com.primevest.domain.integration.dto.ConnectionCreateDTO;
import com.primevest.domain.integration.dto.ConnectionResponseDTO;
import com.primevest.domain.integration.dto.ImportPreviewDTO;
import com.primevest.domain.integration.pipeline.ImportPipeline;
import com.primevest.domain.integration.pipeline.ImportPreview;
import com.primevest.domain.integration.pipeline.PreviewItem;
import com.primevest.domain.integration.pipeline.PreviewOutcome;
import com.primevest.domain.integration.pipeline.ProposedTransaction;
import com.primevest.domain.integration.source.CredentialCheck;
import com.primevest.domain.integration.source.FetchResult;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.SourceConnector;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conexoes do usuario com corretoras: conectar, listar, desconectar e
 * pre-visualizar a importacao (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.7).
 *
 * <p>Tudo escopado pelo dono: conexao de outro usuario responde "nao
 * encontrada", como no resto do projeto.</p>
 *
 * <p>As chamadas a corretora acontecem FORA de transacao de banco — uma
 * consulta que leva segundos (ou minutos, na primeira busca) nao pode segurar
 * conexao do pool. Por isso o {@link TransactionTemplate} em vez de
 * {@code @Transactional} no metodo inteiro.</p>
 */
@Slf4j
@Service
public class IntegrationService {

    private final IntegrationConnectionRepository connections;
    private final IntegrationCredentialRepository credentials;
    private final IntegrationCursorRepository cursors;
    private final IntegrationRunRepository runs;
    private final IntegrationRunItemRepository runItems;
    private final IntegrationCryptoExclusionRepository exclusions;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final AssetRepository assets;
    private final MovementJson movementJson;
    private final ObjectMapper objectMapper;
    private final UserRepository users;
    private final CredentialVault vault;
    private final CredentialCipher cipher;
    private final ImportPipeline pipeline;
    private final PositionCalculator positionCalculator;
    private final ProventosConferencia proventosConferencia;
    private final Map<IntegrationProvider, SourceConnector> connectors = new EnumMap<>(IntegrationProvider.class);
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;

    /** CPF do perfil: a conexao com a B3 usa o do cadastro do usuario (docs/planos/PLANO_CADASTRO_USUARIO.md, D20). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.domain.user.UserCpfService userCpfService;

    @org.springframework.beans.factory.annotation.Value("${primevest.integrations.stale-minutes:30}")
    long staleMinutes = 30;

    @org.springframework.beans.factory.annotation.Value("${primevest.integrations.auto-confirm-scheduled:true}")
    boolean autoConfirmScheduled = true;

    public IntegrationService(IntegrationConnectionRepository connections,
                              IntegrationCredentialRepository credentials,
                              IntegrationCursorRepository cursors,
                              IntegrationRunRepository runs,
                              IntegrationRunItemRepository runItems,
                              IntegrationCryptoExclusionRepository exclusions,
                              TransactionRepository transactions,
                              TransactionService transactionService,
                              AssetRepository assets,
                              MovementJson movementJson,
                              ObjectMapper objectMapper,
                              UserRepository users,
                              CredentialVault vault,
                              CredentialCipher cipher,
                              ImportPipeline pipeline,
                              PositionCalculator positionCalculator,
                              ProventosConferencia proventosConferencia,
                              List<SourceConnector> connectorList,
                              PlatformTransactionManager transactionManager) {
        this.connections = connections;
        this.credentials = credentials;
        this.cursors = cursors;
        this.runs = runs;
        this.runItems = runItems;
        this.exclusions = exclusions;
        this.transactions = transactions;
        this.transactionService = transactionService;
        this.assets = assets;
        this.movementJson = movementJson;
        this.objectMapper = objectMapper;
        this.users = users;
        this.vault = vault;
        this.cipher = cipher;
        this.pipeline = pipeline;
        this.positionCalculator = positionCalculator;
        this.proventosConferencia = proventosConferencia;
        connectorList.forEach(c -> connectors.put(c.provider(), c));
        this.tx = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** As fontes ligadas no servidor, na ordem do enum (F6 da B3). */
    public List<com.primevest.domain.integration.dto.SourceDTO> sources() {
        return connectors.values().stream()
            .map(c -> new com.primevest.domain.integration.dto.SourceDTO(c.provider(), switch (c.provider()) {
                case BINANCE -> "API_KEY";
                case B3 -> "DOCUMENT";
            }, c.oncePerDay()))
            .toList();
    }

    public List<ConnectionResponseDTO> list(UUID userId) {
        return readOnly.execute(s -> connections.findAllOwned(userId).stream().map(this::toDto).toList());
    }

    /**
     * Confere a chave na corretora ANTES de gravar qualquer coisa: chave
     * recusada ou com permissao demais nao chega ao banco (P2).
     */
    public ConnectionResponseDTO connect(UUID userId, ConnectionCreateDTO dto) {
        if (!cipher.isConfigured()) {
            throw new BusinessException("Integrações indisponíveis: o servidor está sem a chave-mestra das "
                + "credenciais (PRIMEVEST_CREDENTIALS_KEY).");
        }
        String label = dto.label().strip();
        if (Boolean.TRUE.equals(readOnly.execute(s -> connections.existsByUserIdAndLabelIgnoreCase(userId, label)))) {
            throw new BusinessException("Você já tem uma conexão chamada \"" + label + "\".");
        }
        SourceConnector connector = connector(dto.provider());
        SourceCredentials credencial = dto.provider() == IntegrationProvider.B3
            ? documentoDoPerfil(userId, dto.documentNumber())
            : credenciais(dto);
        documentoLivre(dto.provider(), credencial);

        CredentialCheck check = connector.check(credencial);
        if (!check.accepted() || !check.readOnly()) {
            throw new BusinessException(check.problem());
        }
        // B3: o CPF vale, mas o investidor ainda nao autorizou o PrimeVest na Area do
        // Investidor. A conexao nasce aguardando; a proxima sincronizacao confere de novo (G8).
        ConnectionStatus inicial = check.awaitingAuthorization()
            ? ConnectionStatus.PENDING_AUTHORIZATION : ConnectionStatus.ACTIVE;

        return tx.execute(s -> {
            User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            IntegrationConnection conexao = connections.save(IntegrationConnection.builder()
                .user(user)
                .provider(dto.provider())
                .label(label)
                .status(inicial)
                .consentAcceptedAt(LocalDateTime.now())
                .lastError(check.awaitingAuthorization() ? check.problem() : null)
                .build());
            IntegrationCredential gravada = vault.store(conexao, credencial);
            gravada.setPermissions(check.permissions());
            gravada.setValidatedAt(check.awaitingAuthorization() ? null : LocalDateTime.now());
            log.info("Conexao {} criada ({}, {}), credencial ****{}", conexao.getId(), dto.provider(), inicial,
                gravada.getFingerprint());
            return toDto(conexao, gravada);
        });
    }

    /**
     * Desconectar apaga a credencial na hora e marca a conexao como REVOKED.
     * A linha fica: as transacoes importadas continuam apontando para ela, e
     * o que fazer com elas e escolha do usuario (6.4, F4/F6).
     */
    public void disconnect(UUID userId, UUID connectionId) {
        tx.executeWithoutResult(s -> {
            IntegrationConnection conexao = owned(userId, connectionId);
            vault.erase(connectionId);
            conexao.setStatus(ConnectionStatus.REVOKED);
            conexao.setLastError(null);
        });
    }

    static final String REVOGADA_NA_FONTE = "A autorização do PrimeVest foi revogada na fonte. A conexão foi "
        + "encerrada e a credencial apagada; para voltar a importar, conecte de novo.";

    /** Revogacao feita na fonte: apaga a credencial e encerra a conexao, como o desconectar. */
    private void revogadaNaFonte(UUID connectionId) {
        tx.executeWithoutResult(s -> connections.findById(connectionId).ifPresent(c -> {
            vault.erase(connectionId);
            c.setStatus(ConnectionStatus.REVOKED);
            c.setLastError(REVOGADA_NA_FONTE);
        }));
        log.info("Conexao {} revogada na fonte; credencial apagada", connectionId);
    }

    /**
     * Armadilha A1 da B3: a fonte so diz "este CPF autorizou o PrimeVest". Um CPF fica em uma
     * conexao viva so — senao outro usuario que o informasse leria os dados do dono. A credencial
     * e cifrada, entao a comparacao decifra as conexoes vivas da mesma fonte.
     */
    private void documentoLivre(IntegrationProvider provider, SourceCredentials credencial) {
        if (!(credencial instanceof InvestorDocument documento)) {
            return;
        }
        boolean emUso = Boolean.TRUE.equals(readOnly.execute(s -> connections
            .findByProviderAndStatusNot(provider, ConnectionStatus.REVOKED).stream()
            .anyMatch(c -> {
                try {
                    return documento.equals(vault.read(c.getId()));
                } catch (RuntimeException e) {
                    return false;
                }
            })));
        if (emUso) {
            throw new BusinessException("Este CPF já tem uma conexão com a " + provider + " no PrimeVest. "
                + "Use a conexão existente ou desconecte-a antes.");
        }
    }

    /**
     * Pede uma sincronizacao (F5): cria uma execucao QUEUED e responde na hora.
     * O {@link IntegrationWorker} busca na corretora em segundo plano; a tela
     * acompanha por {@code GET .../runs/{runId}}. Se ja houver uma na fila ou
     * rodando para esta conexao, devolve essa.
     */
    public ImportPreviewDTO preview(UUID userId, UUID connectionId) {
        UUID runId = tx.execute(s -> {
            IntegrationConnection c = owned(userId, connectionId);
            if (c.getStatus() == ConnectionStatus.REVOKED) {
                throw new BusinessException("Esta conexão foi desconectada. Conecte de novo para importar.");
            }
            boolean naFila = !runs.findByConnectionIdAndStatusIn(c.getId(),
                List.of(RunStatus.QUEUED, RunStatus.RUNNING)).isEmpty();
            if (!naFila && connector(c.getProvider()).oncePerDay() && buscouHoje(c.getId())) {
                throw new BusinessException("Esta fonte permite uma consulta por dia para o mesmo investidor, e a "
                    + "de hoje já foi feita. Tente de novo amanhã.");
            }
            return enfileirar(c, RunTrigger.MANUAL).getId();
        });
        return run(userId, connectionId, runId);
    }

    private IntegrationRun enfileirar(IntegrationConnection c, RunTrigger trigger) {
        return runs.findByConnectionIdAndStatusIn(c.getId(), List.of(RunStatus.QUEUED, RunStatus.RUNNING))
            .stream().findFirst()
            .orElseGet(() -> runs.save(IntegrationRun.builder()
                .connection(c)
                .triggerType(trigger)
                .status(RunStatus.QUEUED)
                .build()));
    }

    /**
     * Rotina diaria: enfileira as conexoes ativas que nao tem nada pendente. A
     * que tem pre-visualizacao aguardando o usuario fica de fora — a decisao
     * dele vem antes de uma busca nova. A que aguarda a autorizacao do investidor
     * (B3) entra tambem: e a checagem que a leva a ACTIVE quando ele autorizar (G8).
     *
     * @return quantas execucoes entraram na fila
     */
    public int enqueueScheduled() {
        Integer n = tx.execute(s -> {
            int total = 0;
            for (IntegrationConnection c : connections.findByStatusIn(
                    List.of(ConnectionStatus.ACTIVE, ConnectionStatus.PENDING_AUTHORIZATION))) {
                boolean pendente = !runs.findByConnectionIdAndStatusIn(c.getId(),
                    List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.AWAITING_CONFIRMATION)).isEmpty();
                SourceConnector connector = connectors.get(c.getProvider());
                boolean limiteDoDia = connector != null && connector.oncePerDay() && buscouHoje(c.getId());
                if (connector != null && !pendente && !limiteDoDia) {
                    enfileirar(c, RunTrigger.SCHEDULED);
                    total++;
                }
            }
            return total;
        });
        return n == null ? 0 : n;
    }

    /**
     * Worker: pega a proxima execucao da fila e a executa.
     *
     * @return false se a fila estava vazia
     */
    public boolean processNext() {
        recuperarPerdidas(LocalDateTime.now().minusMinutes(staleMinutes));
        UUID runId = tx.execute(s -> runs.lockNextQueued().map(id -> {
            IntegrationRun r = runs.findById(id).orElseThrow();
            r.setStatus(RunStatus.RUNNING);
            r.setStartedAt(LocalDateTime.now());
            return id;
        }).orElse(null));
        if (runId == null) {
            return false;
        }
        executar(runId);
        return true;
    }

    /**
     * Execucao RUNNING que ficou para tras (a API caiu no meio da busca) vira
     * FAILED — senao a conexao nunca mais sairia da fila.
     */
    public void recuperarPerdidas(LocalDateTime iniciadasAntesDe) {
        tx.executeWithoutResult(s -> runs.findByStatusAndStartedAtBefore(RunStatus.RUNNING, iniciadasAntesDe)
            .forEach(r -> {
                r.setStatus(RunStatus.FAILED);
                r.setFinishedAt(LocalDateTime.now());
                r.setError("A busca foi interrompida (a API reiniciou no meio dela). Peça uma nova sincronização.");
            }));
    }

    private record Contexto(UUID connectionId, UUID userId, IntegrationProvider provider, RunTrigger trigger,
                            ConnectionStatus status) {}

    /** O corpo da sincronizacao: fora de transacao, porque a corretora pode levar minutos. */
    void executar(UUID runId) {
        Contexto ctx = readOnly.execute(s -> {
            IntegrationRun r = runs.findById(runId).orElseThrow();
            IntegrationConnection c = r.getConnection();
            return new Contexto(c.getId(), c.getUser().getId(), c.getProvider(), r.getTriggerType(), c.getStatus());
        });
        UUID connectionId = ctx.connectionId();
        boolean semDuplicatas;
        try {
            if (ctx.status() == ConnectionStatus.REVOKED) {
                throw new BusinessException("Esta conexão foi desconectada.");
            }
            SourceConnector connector = connector(ctx.provider());
            SourceCredentials credencial = vault.read(connectionId);

            // P2: a permissao e conferida de novo a cada busca.
            CredentialCheck check = connector.check(credencial);
            if (!check.accepted()) {
                marcar(connectionId, ConnectionStatus.INVALID_CREDENTIALS, check.problem());
                throw new BusinessException(check.problem());
            }
            if (!check.readOnly()) {
                marcar(connectionId, ConnectionStatus.PERMISSION_TOO_BROAD, check.problem());
                throw new BusinessException(check.problem());
            }
            if (check.awaitingAuthorization()) {
                if (ctx.status() == ConnectionStatus.ACTIVE) {
                    // Ja estava autorizada e a fonte diz que nao esta mais: o investidor revogou
                    // la (B3: Aplicativos e Sites). Mesmo efeito de desconectar (B3, 6.9 e C5).
                    revogadaNaFonte(connectionId);
                    throw new BusinessException(REVOGADA_NA_FONTE);
                }
                marcar(connectionId, ConnectionStatus.PENDING_AUTHORIZATION, check.problem());
                throw new BusinessException(check.problem());
            }

            Map<String, String> posicoes = readOnly.execute(s -> cursors.findByIdConnectionId(connectionId).stream()
                .collect(Collectors.toMap(c -> c.getId().getStream(), IntegrationCursor::getCursorValue)));
            FetchResult resultado = connector.fetch(credencial, posicoes);
            ImportPreview preview = pipeline.preview(connectionId, ctx.userId(), resultado.movements(),
                connector.rules());
            semDuplicatas = preview.count(PreviewOutcome.POSSIBLE_DUPLICATE) == 0;
            // C3: provento informado pela fonte x proventos da carteira; o resultado vai no motivo.
            Map<String, String> conferidos = proventosConferencia.conferir(ctx.userId(), preview.items().stream()
                .filter(i -> i.outcome() == PreviewOutcome.UNMAPPED).map(PreviewItem::movement).toList());

            tx.executeWithoutResult(s -> {
                // Vale a mais recente: uma pre-visualizacao anterior aguardando e cancelada.
                runs.findByConnectionIdAndStatus(connectionId, RunStatus.AWAITING_CONFIRMATION)
                    .forEach(r -> r.setStatus(RunStatus.CANCELLED));
                IntegrationConnection c = connections.getReferenceById(connectionId);
                if (c.getStatus() != ConnectionStatus.REVOKED) {
                    c.setStatus(ConnectionStatus.ACTIVE);
                }
                c.setLastError(null);
                IntegrationRun run = runs.findById(runId).orElseThrow();
                run.setStatus(RunStatus.AWAITING_CONFIRMATION);
                run.setFinishedAt(LocalDateTime.now());
                run.setImportedCount(0);
                run.setExistingCount((int) preview.count(PreviewOutcome.EXISTING));
                run.setUnmappedCount((int) preview.count(PreviewOutcome.UNMAPPED));
                run.setDuplicateCount((int) preview.count(PreviewOutcome.POSSIBLE_DUPLICATE));
                run.setNextCursors(json(resultado.cursors()));
                run.setBalances(json(resultado.balances()));
                run.setFiatDeposited(preview.fiatDeposited());
                run.setError(null);
                preview.items().stream()
                    .sorted(Comparator.comparing((PreviewItem i) -> i.movement().executedAt()))
                    .forEach(i -> runItems.save(IntegrationRunItem.builder()
                        .run(run)
                        .externalId(i.externalId())
                        .outcome(RunItemOutcome.valueOf(i.outcome().name()))
                        .reason(conferidos.getOrDefault(i.externalId(), i.reason()))
                        .raw(movementJson.write(i.movement()))
                        .duplicateOf(i.duplicateOf() == null ? null : i.duplicateOf().transactionId())
                        .build()));
            });
        } catch (RuntimeException e) {
            log.warn("Sincronizacao {} da conexao {} falhou: {}", runId, connectionId, e.getMessage());
            tx.executeWithoutResult(s -> {
                runs.findById(runId).ifPresent(r -> {
                    r.setStatus(RunStatus.FAILED);
                    r.setFinishedAt(LocalDateTime.now());
                    r.setError(e.getMessage());
                });
                connections.findById(connectionId).ifPresent(c -> c.setLastError(e.getMessage()));
            });
            return;
        }

        // Rotina diaria: confirma sozinha quando nao ha nada para o usuario decidir e
        // ele ja confirmou uma importacao desta conexao antes (a primeira e sempre dele, P5).
        if (ctx.trigger() == RunTrigger.SCHEDULED && autoConfirmScheduled && semDuplicatas
                && Boolean.TRUE.equals(readOnly.execute(s -> runs.existsByConnectionIdAndStatus(connectionId, RunStatus.SUCCEEDED)))) {
            try {
                confirm(ctx.userId(), connectionId, runId, new ConfirmImportDTO(null));
            } catch (RuntimeException e) {
                log.warn("Confirmacao automatica da execucao {} falhou; fica aguardando o usuario: {}", runId, e.getMessage());
            }
        }
    }

    /** A execucao como foi guardada — sem ir a corretora. */
    public ImportPreviewDTO run(UUID userId, UUID connectionId, UUID runId) {
        return readOnly.execute(s -> toDto(ownedRun(userId, connectionId, runId)));
    }

    /**
     * Confirma a execucao: grava as operacoes pelo TransactionService (as
     * mesmas regras do lancamento manual, P6), resolve as duplicatas como o
     * usuario decidiu e so entao avanca os cursores. Tudo ou nada: se uma
     * operacao for recusada, nada e gravado e a execucao segue aguardando,
     * com o motivo.
     */
    public ImportPreviewDTO confirm(UUID userId, UUID connectionId, UUID runId, ConfirmImportDTO decisoes) {
        ConfirmImportDTO d = decisoes == null ? new ConfirmImportDTO(null) : decisoes;
        try {
            tx.executeWithoutResult(s -> confirmar(userId, connectionId, runId, d));
        } catch (RuntimeException e) {
            tx.executeWithoutResult(s -> runs.findById(runId).ifPresent(r -> r.setError(e.getMessage())));
            throw e;
        }
        return run(userId, connectionId, runId);
    }

    private void confirmar(UUID userId, UUID connectionId, UUID runId, ConfirmImportDTO decisoes) {
        IntegrationRun run = ownedRun(userId, connectionId, runId);
        IntegrationConnection conexao = run.getConnection();
        if (conexao.getStatus() == ConnectionStatus.REVOKED) {
            throw new BusinessException("Esta conexão foi desconectada. Conecte de novo para importar.");
        }
        if (run.getStatus() != RunStatus.AWAITING_CONFIRMATION) {
            throw new BusinessException("Esta pré-visualização não está mais aguardando confirmação ("
                + run.getStatus() + "). Gere uma nova.");
        }
        List<IntegrationRunItem> itens = runItems.findByRunIdOrderByCreatedAt(runId);

        List<String> semDecisao = itens.stream()
            .filter(i -> i.getOutcome() == RunItemOutcome.POSSIBLE_DUPLICATE)
            .map(IntegrationRunItem::getExternalId)
            .filter(id -> !decisoes.duplicates().containsKey(id))
            .toList();
        if (!semDecisao.isEmpty()) {
            throw new BusinessException("Decida o que fazer com as possíveis duplicatas antes de confirmar: "
                + String.join(", ", semDecisao) + ".");
        }

        List<String> candidatos = itens.stream()
            .filter(i -> i.getOutcome() == RunItemOutcome.NEW || i.getOutcome() == RunItemOutcome.POSSIBLE_DUPLICATE)
            .map(IntegrationRunItem::getExternalId).toList();
        Set<String> jaImportados = candidatos.isEmpty() ? Set.of()
            : transactions.findImportedIds(connectionId, candidatos);
        Set<String> excluidos = candidatos.isEmpty() ? Set.of()
            : exclusions.findExcludedIds(connectionId, candidatos);

        int importados = 0;
        for (IntegrationRunItem item : itens) {
            boolean duplicata = item.getOutcome() == RunItemOutcome.POSSIBLE_DUPLICATE;
            if (item.getOutcome() != RunItemOutcome.NEW && !duplicata) {
                continue;
            }
            ConfirmImportDTO.DuplicateDecision decisao = duplicata
                ? decisoes.duplicates().get(item.getExternalId()) : null;
            if (decisao == ConfirmImportDTO.DuplicateDecision.SKIP
                    || jaImportados.contains(item.getExternalId()) || excluidos.contains(item.getExternalId())) {
                item.setResolution(RunItemResolution.SKIPPED);
                continue;
            }
            ImportedMovement m = movementJson.read(item.getRaw());
            ImportRules.Result r = connector(conexao.getProvider()).rules().apply(m, assets::findByTicker);
            if (!r.mapped()) {
                throw new BusinessException(contexto(m, null) + r.reason());
            }
            ProposedTransaction p = r.transaction();
            UUID criada;
            try {
                criada = transactionService.importTransaction(userId, p, new ImportOrigin(conexao,
                    m.externalId(), p.executedAt(), item.getRaw())).id();
                // Substituir: importa primeiro e apaga o manual depois, para a posicao
                // nunca ficar sem a compra que sustenta uma venda posterior.
                if (decisao == ConfirmImportDTO.DuplicateDecision.REPLACE && item.getDuplicateOf() != null) {
                    transactionService.deleteTransaction(userId, item.getDuplicateOf());
                    // O banco ja zerou a referencia (ON DELETE SET NULL); a entidade em memoria
                    // ainda a tem, e regrava-la no flush violaria a FK. REPLACED guarda o fato.
                    item.setDuplicateOf(null);
                }
            } catch (BusinessException e) {
                throw new BusinessException(contexto(m, p) + e.getMessage());
            }
            item.setTransactionId(criada);
            item.setResolution(decisao == ConfirmImportDTO.DuplicateDecision.REPLACE ? RunItemResolution.REPLACED
                : decisao == ConfirmImportDTO.DuplicateDecision.KEEP_BOTH ? RunItemResolution.KEPT_BOTH
                : RunItemResolution.IMPORTED);
            importados++;
        }

        // C3: com as operacoes ja na carteira, os proventos dela existem — a conferencia roda de novo.
        if (importados > 0) {
            List<IntegrationRunItem> naoImportados = itens.stream()
                .filter(i -> i.getOutcome() == RunItemOutcome.UNMAPPED).toList();
            Map<String, String> conferidos = proventosConferencia.conferir(userId,
                naoImportados.stream().map(i -> movementJson.read(i.getRaw())).toList());
            naoImportados.forEach(i -> {
                String motivo = conferidos.get(i.getExternalId());
                if (motivo != null) {
                    i.setReason(motivo);
                }
            });
        }

        // So agora os cursores avancam: o que foi visto e confirmado nao volta.
        lerCursores(run.getNextCursors()).forEach((stream, valor) ->
            cursors.save(new IntegrationCursor(new IntegrationCursorId(connectionId, stream), valor, null)));

        run.setStatus(RunStatus.SUCCEEDED);
        run.setImportedCount(importados);
        run.setFinishedAt(LocalDateTime.now());
        run.setError(null);
        conexao.setLastSyncAt(LocalDateTime.now());
        conexao.setLastError(null);
    }

    /** Historico de sincronizacoes da conexao, da mais recente para a mais antiga. */
    public List<com.primevest.domain.integration.dto.RunSummaryDTO> runs(UUID userId, UUID connectionId) {
        return readOnly.execute(s -> {
            owned(userId, connectionId);
            return runs.findByConnectionIdOrderByCreatedAtDesc(connectionId).stream()
                .map(r -> new com.primevest.domain.integration.dto.RunSummaryDTO(r.getId(), r.getStatus(),
                    r.getTriggerType(), r.getCreatedAt(), r.getFinishedAt(), r.getImportedCount(),
                    r.getExistingCount(), r.getUnmappedCount(), r.getDuplicateCount(), r.getError()))
                .toList();
        });
    }

    /** Descarta a pre-visualizacao sem gravar nada. */
    public void cancel(UUID userId, UUID connectionId, UUID runId) {
        tx.executeWithoutResult(s -> {
            IntegrationRun run = ownedRun(userId, connectionId, runId);
            if (run.getStatus() != RunStatus.AWAITING_CONFIRMATION) {
                throw new BusinessException("Esta pré-visualização não está aguardando confirmação.");
            }
            run.setStatus(RunStatus.CANCELLED);
        });
    }

    /** Operacoes importadas que o usuario excluiu (regra E2). */
    public List<ExclusionDTO> exclusions(UUID userId, UUID connectionId) {
        return readOnly.execute(s -> {
            owned(userId, connectionId);
            return exclusions.findByConnectionIdOrderByExcludedAtDesc(connectionId).stream().map(e -> {
                ImportedMovement m = lerMovimento(e.getSourceSnapshot());
                return new ExclusionDTO(e.getId(), e.getExternalId(), e.getAsset().getTicker(), e.getExcludedAt(),
                    m == null ? null : m.executedAt(), m == null ? null : m.side(),
                    m == null ? null : m.grossQuantity(), m == null ? null : m.quoteAmount());
            }).toList();
        });
    }

    /**
     * Regra E4 para excluida: importa a operacao de volta AGORA, a partir do
     * movimento guardado na exclusao, e apaga o registro. Nao espera a proxima
     * busca — com os cursores ja adiante, a corretora nao a devolveria de novo.
     */
    public com.primevest.domain.transaction.dto.TransactionResponseDTO restoreExclusion(
            UUID userId, UUID connectionId, UUID exclusionId) {
        return tx.execute(s -> {
            IntegrationConnection conexao = owned(userId, connectionId);
            IntegrationCryptoExclusion e = exclusions.findById(exclusionId)
                .filter(x -> x.getConnection().getId().equals(connectionId))
                .orElseThrow(() -> new ResourceNotFoundException("Exclusão não encontrada"));
            ImportedMovement m = movementJson.read(e.getSourceSnapshot());
            ImportRules.Result r = connector(conexao.getProvider()).rules().apply(m, assets::findByTicker);
            if (!r.mapped()) {
                throw new BusinessException(contexto(m, null) + r.reason());
            }
            exclusions.delete(e);
            exclusions.flush();
            return transactionService.importTransaction(userId, r.transaction(),
                new ImportOrigin(conexao, m.externalId(), r.transaction().executedAt(), e.getSourceSnapshot()));
        });
    }

    // ------------------------------------------------------------------ apoio

    private IntegrationConnection owned(UUID userId, UUID connectionId) {
        return connections.findOwnedById(connectionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conexão não encontrada"));
    }

    /** As regras da fonte; nulo se o conector dela nao estiver registrado (tela sem proposta). */
    private ImportRules regrasDe(IntegrationProvider provider) {
        SourceConnector c = connectors.get(provider);
        return c == null ? null : c.rules();
    }

    /**
     * B3 (D20): o CPF e o do perfil do usuario — a tela o mostra preenchido e travado. Sem CPF no
     * perfil nao conecta; um CPF diferente no pedido e recusado. Como o CPF e unico entre usuarios,
     * ninguem conecta o CPF de outra pessoa (armadilha A1, PENDENCIAS 16.9).
     */
    private SourceCredentials documentoDoPerfil(UUID userId, String informado) {
        String doPerfil = readOnly.execute(s -> users.findById(userId)
            .flatMap(userCpfService::read).orElse(null));
        if (doPerfil == null) {
            throw new BusinessException("Cadastre o seu CPF em Configurações antes de conectar a B3.");
        }
        if (informado != null && !informado.isBlank()
                && !doPerfil.equals(informado.replaceAll("\\D", ""))) {
            throw new BusinessException("A conexão com a B3 usa o CPF do seu cadastro. Para usar outro, "
                + "altere-o em Configurações.");
        }
        return new InvestorDocument(doPerfil);
    }

    /** Credencial de cada fonte, a partir do pedido de conexao (G9). */
    private static SourceCredentials credenciais(ConnectionCreateDTO dto) {
        try {
            return switch (dto.provider()) {
                case BINANCE -> new ApiKeyCredentials(dto.apiKey(), dto.apiSecret());
                case B3 -> new InvestorDocument(dto.documentNumber());
            };
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    /**
     * Ja houve busca na fonte hoje (dia de Sao Paulo)? Conta a execucao que chegou a
     * consultar — aguardando, confirmada ou descartada. A que falhou nao conta: pode
     * ter parado antes da fonte (ex.: autorizacao pendente). G12.
     */
    private boolean buscouHoje(UUID connectionId) {
        LocalDateTime inicioDoDia = LocalDate.now(SAO_PAULO).atStartOfDay(SAO_PAULO)
            .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return runs.existsByConnectionIdAndStatusInAndStartedAtGreaterThanEqual(connectionId,
            List.of(RunStatus.AWAITING_CONFIRMATION, RunStatus.SUCCEEDED, RunStatus.CANCELLED), inicioDoDia);
    }

    private SourceConnector connector(IntegrationProvider provider) {
        SourceConnector c = connectors.get(provider);
        if (c == null) {
            throw new BusinessException("Fonte ainda não suportada: " + provider);
        }
        return c;
    }

    private void marcar(UUID connectionId, ConnectionStatus status, String erro) {
        tx.executeWithoutResult(s -> connections.findById(connectionId).ifPresent(c -> {
            c.setStatus(status);
            c.setLastError(erro);
            if (status != ConnectionStatus.ACTIVE) {
                credentials.findById(connectionId).ifPresent(cred -> cred.setValidatedAt(null));
            }
        }));
    }

    private ConnectionResponseDTO toDto(IntegrationConnection c) {
        return toDto(c, credentials.findById(c.getId()).orElse(null));
    }

    private ConnectionResponseDTO toDto(IntegrationConnection c, IntegrationCredential cred) {
        IntegrationRun ultima = c.getId() == null ? null
            : runs.findFirstByConnectionIdOrderByCreatedAtDesc(c.getId()).orElse(null);
        return new ConnectionResponseDTO(c.getId(), c.getProvider(), c.getLabel(), c.getStatus(),
            cred != null ? cred.getFingerprint() : null,
            cred != null ? cred.getValidatedAt() : null,
            c.getLastSyncAt(), c.getLastError(), c.getCreatedAt(),
            ultima != null ? ultima.getId() : null, ultima != null ? ultima.getStatus() : null,
            c.getId() == null ? 0 : runs.findFirstByConnectionIdAndStatusOrderByCreatedAtDesc(c.getId(), RunStatus.SUCCEEDED)
                .map(r -> divergencias(r).size()).orElse(0),
            c.getStatus() == ConnectionStatus.PENDING_AUTHORIZATION && connectors.containsKey(c.getProvider())
                ? connectors.get(c.getProvider()).authorizationUrl() : null);
    }

    private IntegrationRun ownedRun(UUID userId, UUID connectionId, UUID runId) {
        owned(userId, connectionId);
        return runs.findById(runId)
            .filter(r -> r.getConnection().getId().equals(connectionId))
            .orElseThrow(() -> new ResourceNotFoundException("Pré-visualização não encontrada"));
    }

    private ImportPreviewDTO toDto(IntegrationRun run) {
        List<IntegrationRunItem> itens = runItems.findByRunIdOrderByCreatedAt(run.getId());
        ImportRules regras = regrasDe(run.getConnection().getProvider());
        List<ImportPreviewDTO.Item> dtos = itens.stream().map(i -> toItem(i, regras)).toList();
        return new ImportPreviewDTO(run.getConnection().getId(), run.getConnection().getLabel(), run.getId(),
            run.getCreatedAt(), run.getFinishedAt(), run.getStatus(), run.getTriggerType(),
            contar(itens, RunItemOutcome.NEW), contar(itens, RunItemOutcome.EXISTING),
            contar(itens, RunItemOutcome.EXCLUDED), contar(itens, RunItemOutcome.UNMAPPED),
            contar(itens, RunItemOutcome.POSSIBLE_DUPLICATE),
            run.getFiatDeposited(), run.getError(), divergencias(run), dtos);
    }

    /**
     * Regra E3: depois de confirmada, compara a posicao do usuario no PrimeVest com
     * o saldo que a fonte informou na busca, ativo a ativo. Quais ativos entram e o
     * que e dinheiro (nao posicao) sao das regras da fonte (G6).
     */
    private List<ImportPreviewDTO.Divergence> divergencias(IntegrationRun run) {
        ImportRules regras = regrasDe(run.getConnection().getProvider());
        if (run.getStatus() != RunStatus.SUCCEEDED || run.getBalances() == null || regras == null) {
            return List.of();
        }
        Map<String, BigDecimal> corretora = new java.util.TreeMap<>(lerSaldos(run.getBalances()));
        regras.cashBalances().forEach(corretora::remove);
        if (corretora.isEmpty() && !regras.emptyBalanceMeansNoPosition()) {
            return List.of();
        }
        // Cotas na data da busca, com os eventos do papel que o catalogo aplica (desdobramento,
        // bonificacao) — como na carteira (B3, 6.7). Cripto nao tem evento: e a soma das operacoes.
        LocalDate data = (run.getFinishedAt() != null ? run.getFinishedAt() : run.getCreatedAt()).toLocalDate();
        Map<String, List<Transaction>> porAtivo = new java.util.HashMap<>();
        Map<String, com.primevest.domain.asset.Asset> ativoPorTicker = new java.util.HashMap<>();
        for (Transaction t : transactions.findAllTransactionsByUserId(run.getConnection().getUser().getId())) {
            var asset = t.getPortfolioAsset().getAsset();
            if (t.getShares() == null || !regras.tracksPosition(asset)) {
                continue;
            }
            porAtivo.computeIfAbsent(asset.getTicker(), k -> new java.util.ArrayList<>()).add(t);
            ativoPorTicker.putIfAbsent(asset.getTicker(), asset);
        }
        Map<String, BigDecimal> primevest = new java.util.HashMap<>();
        porAtivo.forEach((ticker, operacoes) -> primevest.put(ticker, positionCalculator.sharesOn(
            PositionCalculator.chronological(operacoes), positionCalculator.stockEventsOf(ativoPorTicker.get(ticker)),
            data)));
        java.util.TreeSet<String> ativos = new java.util.TreeSet<>(corretora.keySet());
        ativos.addAll(primevest.keySet());
        List<ImportPreviewDTO.Divergence> out = new java.util.ArrayList<>();
        for (String ativo : ativos) {
            BigDecimal naCorretora = corretora.getOrDefault(ativo, BigDecimal.ZERO);
            BigDecimal noPrimevest = primevest.getOrDefault(ativo, BigDecimal.ZERO);
            BigDecimal diferenca = naCorretora.subtract(noPrimevest);
            if (diferenca.signum() != 0) {
                out.add(new ImportPreviewDTO.Divergence(ativo, noPrimevest.stripTrailingZeros(),
                    naCorretora.stripTrailingZeros(), diferenca.stripTrailingZeros()));
            }
        }
        return out;
    }

    private Map<String, BigDecimal> lerSaldos(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, BigDecimal>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static long contar(List<IntegrationRunItem> itens, RunItemOutcome o) {
        return itens.stream().filter(i -> i.getOutcome() == o).count();
    }

    private ImportPreviewDTO.Item toItem(IntegrationRunItem i, ImportRules regras) {
        ImportedMovement m = movementJson.read(i.getRaw());
        ImportPreviewDTO.Proposed proposta = null;
        if (regras != null
                && (i.getOutcome() == RunItemOutcome.NEW || i.getOutcome() == RunItemOutcome.POSSIBLE_DUPLICATE)) {
            ImportRules.Result r = regras.apply(m, assets::findByTicker);
            if (r.mapped()) {
                ProposedTransaction t = r.transaction();
                proposta = new ImportPreviewDTO.Proposed(t.ticker(), t.type(), t.shares(), t.pricePerShare(),
                    t.totalAmount(), t.brokerageFee(), t.transactionDate(), t.roundingDifference());
            }
        }
        ImportPreviewDTO.Duplicate duplicata = i.getDuplicateOf() == null ? null
            : transactions.findById(i.getDuplicateOf())
                .map(t -> new ImportPreviewDTO.Duplicate(t.getId(), t.getTransactionDate(), t.getShares(),
                    t.getPricePerShare()))
                .orElse(null);
        return new ImportPreviewDTO.Item(m.externalId(), m.kind(), PreviewOutcome.valueOf(
                i.getOutcome() == RunItemOutcome.FAILED ? "UNMAPPED" : i.getOutcome().name()),
            i.getReason(), m.executedAt(), m.asset(), m.side(), m.grossQuantity(), m.quoteAsset(),
            m.quoteAmount(), m.feeAmount(), m.feeAsset(), proposta, duplicata, i.getResolution(),
            i.getTransactionId());
    }

    private static final java.time.format.DateTimeFormatter DATA = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** "Operação de 14/11/2025 (BTC, compra): " — para o erro apontar qual linha falhou. */
    private static String contexto(ImportedMovement m, ProposedTransaction p) {
        String data = p != null ? p.transactionDate().format(DATA) : m.executedAt().toString();
        String lado = m.side() == null ? "" : m.side() == com.primevest.domain.integration.source.MovementSide.BUY
            ? ", compra" : ", venda";
        return "Operação de " + data + " (" + m.asset() + lado + "): ";
    }

    private String json(Map<String, ?> mapa) {
        try {
            return objectMapper.writeValueAsString(mapa);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> lerCursores(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Cursores gravados em formato inesperado.", e);
        }
    }

    private ImportedMovement lerMovimento(String json) {
        try {
            return json == null ? null : movementJson.read(json);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
