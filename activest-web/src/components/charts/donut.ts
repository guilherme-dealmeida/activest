/**
 * Regras de toda rosca do sistema (12/10/2026):
 *
 * - fatias e legenda do maior valor para o menor;
 * - a primeira fatia começa no topo (12 h) e as demais seguem no sentido horário, até a menor fechar a volta no topo.
 *
 * No Recharts o ângulo 0 é 3 h e cresce no sentido anti-horário: 90 é o topo, e ir de 90 a −270 dá a volta inteira
 * no sentido horário. Única exceção: a rosca principal do PieOfPieChart, girada para a fatia aberta ficar de frente
 * para a segunda rosca (a legenda dela segue a ordem decrescente).
 */
export const DONUT_START_ANGLE = 90;
export const DONUT_END_ANGLE = -270;

/** Cópia ordenada do maior valor para o menor (empate mantém a ordem recebida). */
export const byValueDesc = <T extends { value: number }>(items: T[]): T[] =>
  [...items].sort((a, b) => b.value - a.value);
