package com.activest.domain.integration;

import com.activest.domain.integration.dto.ImportPreviewDTO;
import com.activest.domain.notification.NotificationDraft;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * As mensagens das integrações (docs/planos/PLANO_NOTIFICACOES.md, 5.8): uma por execução. A execução manual só gera
 * mensagem em falha e em problema de conexão (o usuário está olhando a tela); as agendadas geram todas. O marcador do
 * menu continua igual — os mesmos fatos, nos dois lugares.
 */
@Component
@RequiredArgsConstructor
class IntegrationNotifier {

    private static final String INTEGRATIONS_LINK = "/integrations";

    private final NotificationService notifications;

    record Connection(UUID id, UUID userId, IntegrationProvider provider, String label) {
        String displayName() {
            return label + " (" + sourceName(provider) + ")";
        }
    }

    static String sourceName(IntegrationProvider p) {
        return com.activest.domain.integration.source.SourceDescriptors.of(p).name();
    }

    private static String linkRun(Connection c, UUID runId) {
        return "/integrations/" + c.id() + "/runs/" + runId;
    }

    /** A busca falhou por motivo que não é a conexão (fonte fora, erro na leitura...). */
    void failed(Connection c, UUID runId, String error) {
        notifications.notify(NotificationDraft.of(NotificationKind.INTEGRACAO_FALHOU).user(c.userId())
            .title(c.label() + " — a sincronização falhou")
            .body("A busca de operações na " + sourceName(c.provider()) + " não terminou: " + error
                + "\nA próxima sincronização tenta de novo; você também pode pedir uma pela tela de Integrações.")
            .ref("INTEGRATION_RUN", runId).key(runId.toString())
            .link("Ver execução", linkRun(c, runId))
            .build());
    }

    /** Chave recusada, permissão demais ou autorização pendente: pendência até a conexão voltar a ACTIVE. */
    void connectionProblem(Connection c, ConnectionStatus status, String problem) {
        String title = switch (status) {
            case INVALID_CREDENTIALS -> c.label() + " — a credencial foi recusada";
            case PERMISSION_TOO_BROAD -> c.label() + " — a chave tem permissão demais";
            case PENDING_AUTHORIZATION -> c.label() + " — aguardando a sua autorização";
            default -> c.label() + " — a conexão pede atenção";
        };
        String whatToDo = switch (status) {
            case PENDING_AUTHORIZATION -> "Autorize o Activest na " + sourceName(c.provider())
                + "; a sincronização seguinte confere de novo.";
            case PERMISSION_TOO_BROAD -> "Gere uma chave só de leitura e conecte de novo.";
            default -> "Confira a chave na " + sourceName(c.provider()) + " e conecte de novo.";
        };
        notifications.notify(NotificationDraft.of(NotificationKind.INTEGRACAO_CONEXAO).user(c.userId())
            .title(title)
            .body((problem == null ? "" : problem + "\n") + whatToDo)
            .ref("CONNECTION", c.id()).key(c.id() + ":" + status.name())
            .link("Ver integrações", INTEGRATIONS_LINK)
            .build());
    }

    /** A busca deu certo: a conexão está boa — fecha qualquer problema de conexão aberto. */
    void connectionOk(Connection c) {
        for (ConnectionStatus s : List.of(ConnectionStatus.INVALID_CREDENTIALS, ConnectionStatus.PERMISSION_TOO_BROAD,
                ConnectionStatus.PENDING_AUTHORIZATION)) {
            notifications.resolve(NotificationKind.INTEGRACAO_CONEXAO, c.userId(), c.id() + ":" + s.name());
        }
    }

    /** Execução agendada confirmada sozinha, com operações novas na carteira. */
    void imported(Connection c, UUID runId, int importedCount) {
        notifications.notify(NotificationDraft.of(NotificationKind.INTEGRACAO_IMPORTOU).user(c.userId())
            .title(c.label() + " — " + importedCount + (importedCount == 1 ? " operação importada" : " operações importadas"))
            .body("A sincronização diária da " + sourceName(c.provider()) + " trouxe " + importedCount
                + (importedCount == 1 ? " operação nova, que já está" : " operações novas, que já estão") + " na carteira.")
            .ref("INTEGRATION_RUN", runId).key(runId.toString())
            .link("Ver execução", linkRun(c, runId))
            .build());
    }

    /** Execução agendada parou esperando a decisão do usuário (possível duplicata ou primeira importação). */
    void awaiting(Connection c, UUID runId, int duplicates) {
        notifications.notify(NotificationDraft.of(NotificationKind.INTEGRACAO_AGUARDANDO).user(c.userId())
            .title(c.label() + " — importação aguardando a sua confirmação")
            .body("A sincronização diária da " + sourceName(c.provider()) + " encontrou operações que dependem de você"
                + (duplicates > 0 ? " (" + duplicates + (duplicates == 1 ? " possível duplicata)" : " possíveis duplicatas)")
                : "") + ". Confira e confirme (ou descarte) a pré-visualização.")
            .ref("INTEGRATION_RUN", runId).key(runId.toString())
            .link("Ver execução", linkRun(c, runId))
            .build());
    }

    /** A execução saiu de "aguardando": confirmada, descartada ou substituída por uma mais nova (D6). */
    void decided(UUID userId, UUID runId) {
        notifications.resolve(NotificationKind.INTEGRACAO_AGUARDANDO, userId, runId.toString());
    }

    /**
     * Divergência entre a carteira e o saldo da fonte depois de uma execução concluída: pendência por conexão. Sem
     * divergência, fecha a que estiver aberta. {@code shouldNotify} falso (execução manual) só fecha.
     */
    void divergences(Connection c, UUID runId, List<ImportPreviewDTO.Divergence> divergenceList, boolean shouldNotify) {
        if (divergenceList.isEmpty()) {
            notifications.resolve(NotificationKind.INTEGRACAO_DIVERGENCIA, c.userId(), c.id().toString());
            return;
        }
        if (!shouldNotify) {
            return;
        }
        NotificationDraft.Builder b = NotificationDraft.of(NotificationKind.INTEGRACAO_DIVERGENCIA).user(c.userId())
            .title(c.label() + " — " + divergenceList.size() + (divergenceList.size() == 1 ? " ativo diverge" : " ativos divergem")
                + " da " + sourceName(c.provider()))
            .body("A carteira do Activest não bate com o saldo da " + sourceName(c.provider())
                + " nestes ativos. Pode faltar uma operação que a fonte não informa, ou sobrar um lançamento.")
            .ref("CONNECTION", c.id()).key(c.id().toString())
            .link("Ver execução", linkRun(c, runId));
        divergenceList.forEach(d -> b.line(d.asset() + ": Activest " + plain(d.activest()) + " · " + sourceName(c.provider()) + " "
            + plain(d.exchange())));
        notifications.notify(b.build());
    }

    /** Desconectada (aqui ou na fonte): nada da conexão segue pendente. */
    void disconnected(UUID userId, UUID connectionId) {
        for (ConnectionStatus s : List.of(ConnectionStatus.INVALID_CREDENTIALS, ConnectionStatus.PERMISSION_TOO_BROAD,
                ConnectionStatus.PENDING_AUTHORIZATION)) {
            notifications.resolve(NotificationKind.INTEGRACAO_CONEXAO, userId, connectionId + ":" + s.name());
        }
        notifications.resolve(NotificationKind.INTEGRACAO_DIVERGENCIA, userId, connectionId.toString());
    }

    private static String plain(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
