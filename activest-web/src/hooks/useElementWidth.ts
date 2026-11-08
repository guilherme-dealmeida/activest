import { useCallback, useEffect, useState } from 'react';

/**
 * Largura de um elemento, atualizada quando ele muda de tamanho (ResizeObserver). Os gráficos usam para saber quantos
 * marcos de calendário cabem no eixo X (docs/planos/PLANO_CALENDARIO_GRAFICOS.md). 0 até a primeira medida.
 *
 * O ref é de callback: o contêiner do gráfico às vezes só aparece depois do carregamento, e um ref de objeto lido
 * num efeito de montagem não o veria.
 */
export function useElementWidth<T extends HTMLElement = HTMLDivElement>() {
  const [el, setEl] = useState<T | null>(null);
  const [width, setWidth] = useState(0);
  const ref = useCallback((node: T | null) => setEl(node), []);
  useEffect(() => {
    if (!el || typeof ResizeObserver === 'undefined') return;
    setWidth(el.getBoundingClientRect().width);
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, [el]);
  return { ref, width };
}
