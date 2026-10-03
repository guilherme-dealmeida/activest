package com.primevest.domain.integration;

import com.primevest.domain.dividend.DividendType;
import com.primevest.domain.dividend.ExtratoProventosImportService;
import com.primevest.domain.dividend.ExtratoProventosImportService.ItemResult;
import com.primevest.domain.dividend.ExtratoProventosImportService.Line;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proventos que a fonte informa (B3: Movimentacao e Eventos Provisionados) conferidos com os
 * proventos da carteira (docs/planos/PLANO_INTEGRACAO_B3.md, 6.8 e C3). Usa a mesma conferencia do
 * extrato de proventos ({@link ExtratoProventosImportService}): <b>so le</b>, nada vai para
 * {@code dividends} nem para o catalogo (P3). O resultado vira o motivo do item nao importado —
 * {@code integration_run_items.reason}, sem coluna nova (P2).
 *
 * <p>Roda na pre-visualizacao e de novo na confirmacao: na primeira importacao a carteira ainda nao
 * tem as posicoes, e os proventos dela so existem depois que as negociacoes entram.</p>
 */
@Slf4j
@Component
public class ProventosConferencia {

    private final ExtratoProventosImportService extrato;

    public ProventosConferencia(ExtratoProventosImportService extrato) {
        this.extrato = extrato;
    }

    /** externalId -> motivo com o resultado da conferencia, para os proventos (CASH_EVENT) entre os movimentos. */
    public Map<String, String> conferir(UUID userId, List<ImportedMovement> movimentos) {
        Map<String, ImportedMovement> proventos = new HashMap<>();
        List<Line> linhas = new ArrayList<>();
        for (ImportedMovement m : movimentos) {
            if (m.kind() != MovementKind.CASH_EVENT) {
                continue;
            }
            proventos.put(m.externalId(), m);
            linhas.add(new Line(m.externalId(), m.asset(), tipo(m.market()), m.tradeDate(), m.grossQuantity(),
                m.quoteAmount(), null, null));
        }
        if (linhas.isEmpty()) {
            return Map.of();
        }
        List<ItemResult> resultado;
        try {
            resultado = extrato.importFor(userId, new ExtratoProventosImportService.Request(linhas)).items();
        } catch (RuntimeException e) {
            log.warn("Conferencia de proventos da integracao nao rodou: {}", e.getMessage());
            return Map.of();
        }
        Map<String, String> motivos = new HashMap<>();
        for (ItemResult r : resultado) {
            ImportedMovement m = proventos.get(r.ref());
            if (m != null) {
                motivos.put(r.ref(), motivo(m, r));
            }
        }
        return motivos;
    }

    static String motivo(ImportedMovement m, ItemResult r) {
        String p = "Provento (" + (m.market() != null ? m.market() : "provento")
            + (m.asset() != null ? ", " + m.asset() : "") + ")";
        return switch (r.status()) {
            case JA_EXISTE -> p + " conferido: já está na carteira.";
            case SEM_DATA -> p + " está na carteira, mas o catálogo ainda não tem a data de pagamento.";
            case SEM_EVENTO -> p + " não está na carteira: o catálogo não tem este evento (pendência do catálogo).";
            case RECUSADO -> p + " não conferido: " + r.message();
        };
    }

    /** Tipo da B3 ('Dividendo', 'Juros Sobre Capital Próprio', 'Rendimento', ... 'a receber') no tipo da carteira. */
    static DividendType tipo(String descricao) {
        if (descricao == null) {
            return null;
        }
        String t = Normalizer.normalize(descricao.toUpperCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (t.contains("JUROS")) {
            return DividendType.JCP;
        }
        if (t.contains("DIVIDENDO")) {
            return DividendType.DIVIDENDO;
        }
        if (t.contains("RENDIMENTO")) {
            return DividendType.RENDIMENTO;
        }
        if (t.contains("AMORTIZACAO")) {
            return DividendType.AMORTIZACAO;
        }
        if (t.contains("RESTITUICAO")) {
            return DividendType.RESTITUICAO_CAPITAL;
        }
        return null;
    }
}
