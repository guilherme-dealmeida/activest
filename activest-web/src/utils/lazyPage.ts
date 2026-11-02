import { lazy, type ComponentType } from 'react';

/**
 * Página carregada sob demanda (docs/planos/PLANO_PERFORMANCE_TELAS.md, D3): cada tela vira um pedaço próprio do
 * pacote, baixado só quando é aberta, e não mais as 21 de uma vez na abertura do sistema.
 *
 * As páginas têm export nomeado: `lazyPage(() => import('@/pages/XPage'), 'XPage')`.
 *
 * I4: depois de um deploy novo, uma aba aberta antes pode pedir um pedaço que não existe mais e o import falha.
 * Nesse caso a página é recarregada uma vez sozinha (vem o pacote novo); se falhar de novo, o erro aparece — a marca
 * em sessionStorage evita o laço. Sem sessionStorage (navegação privada bloqueada), não recarrega.
 */
const RELOAD_KEY = 'activest-lazy-page-reload';

export const lazyPage = <M extends Record<string, unknown>, K extends keyof M>(
  load: () => Promise<M>,
  name: K,
) =>
  lazy(async () => {
    try {
      const module = await load();
      try { sessionStorage.removeItem(RELOAD_KEY); } catch { /* sem sessionStorage: nada a limpar */ }
      return { default: module[name] as unknown as ComponentType };
    } catch (error) {
      let reloaded = true;
      try {
        reloaded = sessionStorage.getItem(RELOAD_KEY) === '1';
        if (!reloaded) sessionStorage.setItem(RELOAD_KEY, '1');
      } catch { /* sem sessionStorage: não arrisca o laço */ }
      if (!reloaded) {
        window.location.reload();
        return new Promise<never>(() => {});
      }
      throw error;
    }
  });
