package com.primevest.adm.domain.notice;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.notification.NotificationDraft;
import com.primevest.domain.notification.NotificationKind;
import com.primevest.domain.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Mensagens de administrador da adm-api (docs/planos/PLANO_NOTIFICACOES.md, 5.7 e D1): a adm-api grava direto na
 * tabela de notificações, pelo serviço do core — mensagem de sistema não é dado de usuário, e o Redis pub/sub não
 * garante entrega. Onde havia só {@code log.warn/error}, passa a haver log <b>e</b> mensagem.
 *
 * <p>Falha de rotina e fonte fora do ar: no máximo uma por rotina (e fonte) por dia (R10) — a fonte instável não
 * enche a caixa.</p>
 */
@Component
@RequiredArgsConstructor
public class AdminNotices {

    private final NotificationService notifications;

    /** Uma rotina (agendada ou sob demanda) terminou com erro. */
    public void routineFailed(String routine, String error) {
        notifications.notify(NotificationDraft.of(NotificationKind.ROTINA_FALHOU)
            .title("Rotina \"" + routine + "\" falhou")
            .body("A rotina terminou com erro: " + error + "\nDetalhes no log da adm-api. A próxima rodada tenta de "
                + "novo; para rodar agora, use o endpoint de administrador da rotina.")
            .ref("ROUTINE", routine).key(routine + ":" + NotificationService.today())
            .build());
    }

    /** Uma fonte externa não respondeu (ou recusou) durante a rotina. */
    public void sourceUnavailable(String routine, String source, String error) {
        notifications.notify(NotificationDraft.of(NotificationKind.FONTE_INDISPONIVEL)
            .title(source + " indisponível — " + routine)
            .body("A rotina \"" + routine + "\" não conseguiu falar com " + source + ": " + error
                + "\nO que faltou fica para a próxima rodada. Mais de uma falha no mesmo dia não gera outra mensagem.")
            .ref("ROUTINE", routine).key(routine + ":" + source + ":" + NotificationService.today())
            .build());
    }

    /**
     * A fonte respondeu, mas o dado mais recente é antigo (docs/planos/PLANO_FONTE_TESOURO.md, D3). Pendência: não
     * repete enquanto aberta e se resolve com {@link #sourceCurrent}.
     */
    public void sourceStale(String routine, String source, LocalDate lastDate, int businessDays) {
        String since = lastDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        notifications.notify(NotificationDraft.of(NotificationKind.FONTE_DESATUALIZADA)
            .title(source + " sem dado novo desde " + since + " — " + routine)
            .body("A rotina \"" + routine + "\" falou com " + source + ", mas o dado mais recente ainda é de " + since
                + " (" + businessDays + " dias úteis sem publicação). Não é falha do PrimeVest: a fonte não publicou. "
                + "A rotina segue rodando, e esta mensagem se resolve sozinha quando o dado novo chegar.")
            .ref("ROUTINE", routine).key(routine + ":" + source)
            .build());
    }

    /** A fonte voltou a publicar: resolve a pendência de {@link #sourceStale}. */
    public void sourceCurrent(String routine, String source) {
        notifications.resolve(NotificationKind.FONTE_DESATUALIZADA, null, routine + ":" + source);
    }

    /** Pendência de catálogo ligada a um ativo (o fan-out para quem tem posição usa ref ASSET). */
    public void assetPending(NotificationKind kind, Asset asset, String key, String title, String body) {
        notifications.notify(NotificationDraft.of(kind)
            .title(title).body(body)
            .ref("ASSET", asset == null ? null : asset.getId()).key(key)
            .data("ticker", asset == null ? key : asset.getTicker())
            .link("Ver ativo", asset == null ? "/assets" : "/assets/" + asset.getTicker())
            .build());
    }

    /** Pendência de catálogo sem ativo do catálogo (ticker novo, título do Tesouro sem cadastro, rótulo). */
    public void pending(NotificationKind kind, String key, String title, String body, Collection<String> lines) {
        NotificationDraft.Builder b = NotificationDraft.of(kind).title(title).body(body).ref("CATALOG", key).key(key);
        lines.forEach(b::line);
        notifications.notify(b.build());
    }

    /** A conferência rodou de novo: o que não apareceu mais está resolvido. */
    public void resolveAllExcept(NotificationKind kind, Collection<String> stillOpen) {
        notifications.resolveAllExcept(kind, null, stillOpen);
    }

    public void resolve(NotificationKind kind, String key) {
        notifications.resolve(kind, null, key);
    }
}
