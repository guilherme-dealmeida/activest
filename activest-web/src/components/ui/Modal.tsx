import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { createPortal } from 'react-dom';
import { InfoTooltip } from './InfoTooltip';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  /** Explicação no (i) ao lado do título. */
  titleInfo?: React.ReactNode;
  /** Parágrafo curto abaixo do título (no cabeçalho fixo). */
  description?: React.ReactNode;
  /** lg e xl alargam o painel para formulários de duas colunas. */
  size?: 'md' | 'lg' | 'xl' | '2xl';
  /**
   * Barra de ações fixa no rodapé, FORA da área que rola.
   *
   * Tentar isso com sticky bottom-0 dentro do corpo não funciona: o elemento
   * gruda pela sua caixa de margem, então o conteúdo continua passando por
   * baixo dele na faixa do padding — os botões ficam flutuando sobre campos
   * visíveis. Como irmão do container de scroll, isso é impossível por
   * construção.
   *
   * Botões de submit aqui precisam de form="<id do form>", já que ficam fora
   * do <form> que está em children.
   */
  footer?: React.ReactNode;
  children: React.ReactNode;
}

const WIDTHS = {
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
  // transação com o quadro do título em duas colunas (TransactionFormModal)
  '2xl': 'max-w-6xl',
} as const;

/**
 * O painel é limitado a 85vh e rola por dentro.
 *
 * Antes ele não tinha altura máxima nenhuma: com items-center, conteúdo maior
 * que a tela vazava pelas duas pontas — sumia o cabeçalho em cima e o botão
 * Salvar embaixo. E como o modal trava o scroll do body ao abrir, não havia
 * como alcançar o que tinha vazado.
 */
export const Modal = ({ isOpen, onClose, title, titleInfo, description, size = 'md', footer, children }: ModalProps) => {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return createPortal(
    // O p-4 impede o painel de encostar na borda da tela em telas pequenas.
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="fixed inset-0 bg-background/80 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      <div
        className={`relative z-50 flex max-h-[85vh] w-full ${WIDTHS[size]} flex-col overflow-hidden rounded-xl bg-surface border border-surface-light shadow-lg animate-in fade-in zoom-in-95 duration-200`}
      >
        {/* Cabeçalho e rodapé são shrink-0: só o miolo cede espaço. */}
        <div className={`flex shrink-0 justify-between gap-4 border-b border-surface-light px-6 py-4 ${description ? 'items-start' : 'items-center'}`}>
          <div className="min-w-0">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-white">
              {title}
              {titleInfo && <InfoTooltip content={titleInfo} />}
            </h2>
            {description && <p className="mt-1 text-sm text-gray-400">{description}</p>}
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-gray-400 hover:bg-surface-light hover:text-white transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {children}
        </div>

        {footer && (
          <div className="shrink-0 border-t border-surface-light bg-surface px-6 py-4">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body
  );
};
