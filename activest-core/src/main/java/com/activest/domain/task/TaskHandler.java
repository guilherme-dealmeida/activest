package com.activest.domain.task;

/**
 * A rotina de um tipo de tarefa (D24): um bean por tipo, no serviço dono ({@link TaskType#getService()}). Chamado só
 * pelo {@link TaskExecutor}, que cuida da rodada (linha no histórico, trava por tipo, status, falha).
 */
public interface TaskHandler {

    /** {@link TaskType#getType()} que este handler executa. */
    String type();

    /**
     * Faz o trabalho. Lê os atributos pelo contexto, confere o Stop entre unidades de trabalho
     * ({@link TaskRunContext#checkStop()}) e grava o resumo ({@link TaskRunContext#summary(String)}). Exceção = rodada
     * {@code FAILED}; {@link TaskSkippedException} = {@code SKIPPED}; {@link TaskStoppedException} = {@code CANCELLED}.
     */
    void run(TaskRunContext ctx);

    /** O handler confere o Stop (D8): sem isso, a tela deixa o Stop desabilitado no tipo. */
    default boolean stoppable() {
        return false;
    }

    /**
     * Subida do serviço (D31, D37, D38): as tarefas deste tipo com {@code run_on_startup}, ativas e sem rodada no dia
     * rodam com origem {@code STARTUP} — se esta consulta disser que há o que recuperar (ex.: falta o fechamento do
     * último pregão). Padrão: sempre. Consulta barata.
     */
    default boolean needsRecovery() {
        return true;
    }
}
