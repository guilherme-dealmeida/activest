package com.activest.domain.goal.dto;

import com.activest.domain.goal.engine.Evaluation;
import com.activest.domain.goal.type.FieldSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contratos da API de metas (docs/planos/PLANO_METAS.md, Anexo A 11). */
public final class GoalDTOs {

    private GoalDTOs() {
    }

    /**
     * Cadastro ou edicao de meta (e o preview). So o que o usuario decide (G1): o resto do tipo vai em {@code params};
     * campo que o tipo nao declara e recusado com 400.
     */
    public record GoalRequest(@Size(max = 120, message = "Nome com no máximo 120 caracteres") String name,
                              @NotBlank(message = "Informe o tipo da meta") String type,
                              BigDecimal targetValue, LocalDate targetDate, Map<String, Object> scope,
                              Map<String, Object> params, Boolean notifications,
                              @Size(max = 500, message = "Observações com no máximo 500 caracteres") String notes) {
    }

    public record Option(String value, String label) {
    }

    /** Um tipo do catalogo: a tela monta o formulario daqui. */
    public record GoalTypeDTO(String type, String code, String label, String category, String categoryLabel,
                              String nature, boolean projection, String description, List<FieldSpec> fields,
                              List<Option> scopes, String reservation, List<String> calculates, List<String> data,
                              List<String> limitations) {
    }

    /** Valores possiveis de cada forma de escopo/dimensao, das posicoes da carteira (abertas e encerradas). */
    public record ScopeOptionsDTO(Map<String, List<Option>> options) {
    }

    public record CategoryDTO(String value, String label) {
    }

    public record CatalogDTO(List<CategoryDTO> categories, List<GoalTypeDTO> types) {
    }

    /** A meta com a avaliacao de hoje ({@code evaluation} nula na concluida: vale o {@code finalResult}). */
    public record GoalDTO(UUID id, String name, String type, String code, String typeLabel, String category,
                          String categoryLabel, String nature, String lifecycle, BigDecimal targetValue,
                          LocalDate targetDate, Map<String, Object> scope, String scopeLabel,
                          Map<String, Object> params, boolean notifications, String notes, int revision,
                          LocalDate achievedOn, Map<String, Object> finalResult, LocalDateTime createdAt,
                          LocalDateTime updatedAt, Evaluation evaluation) {
    }

    /** Resumo da visao geral: contagens por ciclo e situacao. */
    public record SummaryDTO(int active, int paused, int completed, int archived, int onTrack, int behind,
                             int outside, int noData, int achieved, int inProgress) {
    }

    public record GoalListDTO(SummaryDTO summary, List<GoalDTO> goals) {
    }

    /** Preview: a avaliacao de uma definicao sem gravar. */
    public record PreviewDTO(String objective, Map<String, Object> scope, String scopeLabel, Evaluation evaluation) {
    }

    public record EventDTO(Long id, String kind, int revision, Map<String, Object> definition,
                           Map<String, Object> detail, LocalDateTime occurredAt) {
    }

    public record SnapshotDTO(LocalDate date, int revision, BigDecimal indicator, BigDecimal target,
                              BigDecimal progressPct, String situation, String quality) {
    }

    public record PointDTO(LocalDate date, BigDecimal value) {
    }

    public record TargetDTO(int revision, BigDecimal target, LocalDateTime from) {
    }

    /** Linha de apoio do grafico (o indice na meta de superar um indice; os proventos do mes nas metas de renda). */
    public record LineDTO(String key, String label, List<PointDTO> points) {
    }

    /**
     * Evolucao recalculada (D3) + fotografias + alvo de cada revisao.
     *
     * @param comparable a evolucao mede o mesmo que o indicador e o alvo (falso na aposentadoria com renda desejada,
     *                   em superar um indice e em proventos compram cotas — o grafico nao mistura grandezas)
     * @param extras     linhas de apoio, na mesma unidade da evolucao
     * @param fixedTarget o alvo e um numero fixo (vira linha); falso quando ele anda (o aporte medio em REN-3)
     */
    public record HistoryDTO(String unit, String seriesLabel, List<PointDTO> series, List<TargetDTO> targets,
                             List<SnapshotDTO> snapshots, String note, boolean comparable, List<LineDTO> extras,
                             boolean fixedTarget) {
    }
}
