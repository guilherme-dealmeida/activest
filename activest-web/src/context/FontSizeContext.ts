import { createContext, useContext } from 'react';
import type { FontSize } from '@/utils/fontScale';

// O provider fica em FontSizeProvider.tsx (fast refresh do Vite).

export interface FontSizeContextType {
  fontSize: FontSize;
  /** Alterna Grande ↔ Pequena e, com alguém logado, grava no usuário (PLANO_CUSTOMIZAR_TAMANHOS, D4). */
  toggleFontSize: () => void;
  /** Aplica um tamanho sem gravar — o AuthProvider usa ao carregar o usuário. */
  setFontSize: (size: FontSize) => void;
  isSmall: boolean;
}

export const FontSizeContext = createContext<FontSizeContextType>({
  fontSize: 'large',
  toggleFontSize: () => {},
  setFontSize: () => {},
  isSmall: false,
});

export const useFontSize = () => useContext(FontSizeContext);
