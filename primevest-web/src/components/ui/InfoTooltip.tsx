import React, { ReactNode, useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { clsx } from 'clsx';
import { Info } from 'lucide-react';

interface InfoTooltipProps {
  /** O que o balão explica. */
  content: ReactNode;
  /** Texto lido por leitor de tela no botão. */
  label?: string;
  className?: string;
}

/** Distância entre o ícone e o balão, e margem mínima até a borda da tela. */
const GAP = 8;
const EDGE = 8;
const MAX_WIDTH = 320;

/**
 * Ícone (i) com um balão explicativo.
 *
 * Substitui os textos de instrução que ficavam soltos embaixo dos campos: a
 * explicação continua a um passo, mas a tela deixa de carregar parágrafo em
 * todo formulário.
 *
 * <p>O balão é desenhado num portal no body, com posição fixa calculada a
 * partir do ícone. Dentro do lugar onde o ícone mora ele seria cortado: o
 * miolo do Modal rola (overflow-y-auto) e as tabelas rolam na horizontal
 * (overflow-x-auto), e as duas coisas recortam qualquer filho absoluto que
 * passe da borda.</p>
 *
 * <p>O clique no ícone para ali: o (i) mora em cabeçalho de tabela que ordena
 * no clique e dentro de &lt;label&gt; de checkbox, que marcaria a caixa. Por
 * isso também é type="button" — dentro de um &lt;form&gt;, um botão sem tipo
 * é submit, e clicar no (i) salvaria a transação.</p>
 */
export const InfoTooltip = ({ content, label = 'Mais informações', className }: InfoTooltipProps) => {
  const [open, setOpen] = useState(false);
  // Aberto por clique (toque) fica aberto até clicar fora; aberto por hover
  // fecha ao sair com o mouse.
  const [pinned, setPinned] = useState(false);
  const [pos, setPos] = useState<{ top: number; left: number; placement: 'top' | 'bottom' } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const bubbleRef = useRef<HTMLDivElement>(null);
  const tooltipId = useId();

  const close = useCallback(() => {
    setOpen(false);
    setPinned(false);
    setPos(null);
  }, []);

  // Posição medida depois de o balão existir no DOM, para saber a altura dele
  // e virar para cima quando não cabe embaixo.
  useLayoutEffect(() => {
    if (!open || !buttonRef.current || !bubbleRef.current) return;
    const anchor = buttonRef.current.getBoundingClientRect();
    const bubble = bubbleRef.current.getBoundingClientRect();
    const fitsBelow = anchor.bottom + GAP + bubble.height <= window.innerHeight - EDGE;
    const placement = fitsBelow || anchor.top - GAP - bubble.height < EDGE ? 'bottom' : 'top';
    const top = placement === 'bottom' ? anchor.bottom + GAP : anchor.top - GAP - bubble.height;
    const centered = anchor.left + anchor.width / 2 - bubble.width / 2;
    const left = Math.min(Math.max(EDGE, centered), window.innerWidth - bubble.width - EDGE);
    setPos({ top, left, placement });
  }, [open, content]);

  // Enquanto aberto: Esc fecha, clique fora fecha, rolar ou redimensionar
  // fecha — o balão é fixo e ficaria para trás do ícone.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    const onPointer = (e: PointerEvent) => {
      const target = e.target as Node;
      if (buttonRef.current?.contains(target) || bubbleRef.current?.contains(target)) return;
      close();
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('pointerdown', onPointer);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('pointerdown', onPointer);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [open, close]);

  const stop = (e: React.SyntheticEvent) => {
    e.stopPropagation();
    e.preventDefault();
  };

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-label={label}
        aria-describedby={open ? tooltipId : undefined}
        aria-expanded={open}
        onClick={(e) => {
          stop(e);
          if (pinned) { close(); return; }
          setPinned(true);
          setOpen(true);
        }}
        onMouseDown={(e) => e.stopPropagation()}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => { if (!pinned) close(); }}
        onFocus={() => setOpen(true)}
        onBlur={() => { if (!pinned) close(); }}
        className={clsx(
          'inline-flex shrink-0 items-center justify-center rounded-full align-middle normal-case tracking-normal',
          'text-gray-500 hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent transition-colors',
          open && 'text-accent',
          className
        )}
      >
        <Info className="h-3.5 w-3.5" />
      </button>
      {open && createPortal(
        <div
          ref={bubbleRef}
          id={tooltipId}
          role="tooltip"
          style={{
            position: 'fixed',
            top: pos?.top ?? 0,
            left: pos?.left ?? 0,
            maxWidth: MAX_WIDTH,
            // Mede primeiro, mostra depois: sem isso o balão pisca no canto.
            visibility: pos ? 'visible' : 'hidden',
          }}
          className="z-[60] w-max rounded-lg border border-white/10 bg-surface px-3 py-2 text-left text-sm font-normal normal-case leading-snug tracking-normal text-gray-300 shadow-lg"
        >
          {content}
        </div>,
        document.body
      )}
    </>
  );
};

/**
 * Rótulo com o (i) ao lado. Os componentes de campo usam isto para não repetir
 * o alinhamento em cada um.
 */
export const LabelWithInfo = ({ children, info }: { children: ReactNode; info?: ReactNode }) =>
  info ? (
    <span className="inline-flex items-center gap-1.5">
      {children}
      <InfoTooltip content={info} />
    </span>
  ) : (
    <>{children}</>
  );
