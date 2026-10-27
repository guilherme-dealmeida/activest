import { useSyncExternalStore } from 'react';
import { apiClient } from '@/api/client';

/**
 * Rótulo do ativo nas telas (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, D3): o ticker, e no fundo de investimento o
 * nome curto. O ticker do fundo é o CNPJ sem pontuação — chave e endereço, nunca título.
 *
 * Os nomes dos fundos que o usuário já lançou vêm de GET /funds/labels (na entrada e depois de cada transação); a
 * busca da Nova transação acrescenta os que aparecem nela.
 */
let labels: Record<string, string> = {};
let version = 0;
const listeners = new Set<() => void>();

const notify = () => {
  version += 1;
  listeners.forEach(l => l());
};

export const setAssetLabels = (more: Record<string, string>) => {
  const fresh = Object.entries(more).filter(([k, v]) => v && labels[k] !== v);
  if (!fresh.length) return;
  labels = { ...labels, ...Object.fromEntries(fresh) };
  notify();
};

/** O texto a mostrar no lugar do ticker. */
export const assetLabel = (ticker: string | null | undefined): string =>
  ticker ? labels[ticker] ?? ticker : '';

/**
 * O texto com o ticker de cada fundo conhecido trocado pelo nome curto: para os rótulos que a API monta com o ticker
 * (Rentabilidade, Metas, filtros) e as mensagens da tela. Texto sem ticker de fundo volta igual.
 */
export const assetText = (text: string | null | undefined): string => {
  if (!text) return '';
  let out = text;
  // o mais longo antes: a subclasse (CNPJ-ID) contém o CNPJ da classe
  for (const [ticker, name] of Object.entries(labels).sort((a, b) => b[0].length - a[0].length)) {
    if (out.includes(ticker)) out = out.split(ticker).join(name);
  }
  return out;
};

/** {@link assetLabel} que redesenha o componente quando os nomes chegam. */
export const useAssetLabel = () => {
  useSyncExternalStore(
    (l) => { listeners.add(l); return () => listeners.delete(l); },
    () => version,
  );
  return assetLabel;
};

/** Carrega os nomes dos fundos do usuário. Falha silenciosa: a tela mostra o ticker. */
export const loadAssetLabels = async () => {
  try {
    const { data } = await apiClient.get<Record<string, string>>('/funds/labels');
    setAssetLabels(data ?? {});
  } catch {
    /* sem nomes: fica o ticker */
  }
};
