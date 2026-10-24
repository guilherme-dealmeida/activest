import React, { ReactNode, useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { clsx } from 'clsx';

interface PopoverProps {
  /** Conteúdo do botão que abre (normalmente um ícone). */
  trigger: ReactNode;
  /** Rótulo do botão (leitor de tela e dica no mouse). */
  label: string;
  /** Título opcional no topo do painel. */
  title?: ReactNode;
  children: ReactNode;
  /** Largura do painel em px. */
  width?: number;
  className?: string;
}

const GAP = 8;
const EDGE = 8;

/**
 * Popover (17/10/2026): painel que abre ancorado num ícone, por clique, e fica aberto até clicar fora, apertar Esc ou
 * clicar no ícone de novo. Nem modal (não cobre a tela) nem o balão do (i) (`InfoTooltip`, que é só texto e abre no
 * mouse): aqui cabe conteúdo de verdade, como a prévia da venda no modal de transação.
 *
 * <p>Os mesmos cuidados do `InfoTooltip`: o painel vai num portal no body com posição fixa (o miolo do Modal rola e
 * recortaria um filho absoluto), o clique no ícone não propaga e o botão é type="button" (dentro de &lt;form&gt;,
 * botão sem tipo é submit).</p>
 */
export const Popover = ({ trigger, label, title, children, width = 420, className }: PopoverProps) => {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const panelId = useId();

  const close = useCallback(() => {
    setOpen(false);
    setPos(null);
  }, []);

  const place = useCallback(() => {
    if (!buttonRef.current || !panelRef.current) return;
    const anchor = buttonRef.current.getBoundingClientRect();
    const panel = panelRef.current.getBoundingClientRect();
    const fitsBelow = anchor.bottom + GAP + panel.height <= window.innerHeight - EDGE;
    const top = fitsBelow || anchor.top - GAP - panel.height < EDGE
      ? Math.max(EDGE, Math.min(anchor.bottom + GAP, window.innerHeight - panel.height - EDGE))
      : anchor.top - GAP - panel.height;
    const centered = anchor.left + anchor.width / 2 - panel.width / 2;
    const left = Math.min(Math.max(EDGE, centered), window.innerWidth - panel.width - EDGE);
    setPos({ top, left });
  }, []);

  // Mede depois de o painel existir; mede de novo quando o conteúdo muda de tamanho (a prévia recalcula).
  useLayoutEffect(() => {
    if (!open) return;
    place();
    if (typeof ResizeObserver === 'undefined' || !panelRef.current) return;
    const ro = new ResizeObserver(() => place());
    ro.observe(panelRef.current);
    return () => ro.disconnect();
  }, [open, place]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        // Esc fecha só o popover, não o modal que está embaixo.
        e.stopPropagation();
        close();
      }
    };
    const onPointer = (e: PointerEvent) => {
      const target = e.target as Node;
      if (buttonRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      close();
    };
    // Rolar fora do painel o leva junto com o ícone; rolar dentro dele não muda nada.
    const onScroll = (e: Event) => {
      if (panelRef.current?.contains(e.target as Node)) return;
      place();
    };
    window.addEventListener('keydown', onKey, true);
    window.addEventListener('pointerdown', onPointer);
    window.addEventListener('scroll', onScroll, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('keydown', onKey, true);
      window.removeEventListener('pointerdown', onPointer);
      window.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', place);
    };
  }, [open, close, place]);

  const toggle = (e: React.SyntheticEvent) => {
    e.stopPropagation();
    e.preventDefault();
    if (open) close(); else setOpen(true);
  };

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-label={label}
        title={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={toggle}
        onMouseDown={(e) => e.stopPropagation()}
        className={clsx(
          'inline-flex shrink-0 items-center justify-center rounded-md p-1 align-middle transition-colors',
          'text-accent hover:bg-accent/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent',
          open && 'bg-accent/10',
          className
        )}
      >
        {trigger}
      </button>
      {open && createPortal(
        <div
          ref={panelRef}
          id={panelId}
          role="dialog"
          aria-label={typeof title === 'string' ? title : label}
          style={{
            position: 'fixed',
            top: pos?.top ?? 0,
            left: pos?.left ?? 0,
            width: `min(${width}px, calc(100vw - ${EDGE * 2}px))`,
            maxHeight: `calc(100vh - ${EDGE * 2}px)`,
            visibility: pos ? 'visible' : 'hidden',
          }}
          className="z-[60] overflow-y-auto rounded-lg border border-white/10 bg-surface p-4 text-left text-sm font-normal normal-case tracking-normal text-gray-300 shadow-xl"
        >
          {title && <div className="mb-2 font-medium text-gray-200">{title}</div>}
          {children}
        </div>,
        document.body
      )}
    </>
  );
};
