import { useState, useEffect, ReactNode, useCallback } from 'react';
import { FontSizeContext } from './FontSizeContext';
import { tokens } from '@/api/tokens';
import { usersApi } from '@/api/users';
import { FontSize, SMALL_FONT_CLASS, fontSizeToUser } from '@/utils/fontScale';

const STORAGE_KEY = 'activest-font-size';

/**
 * Tamanho da tela (docs/planos/PLANO_CUSTOMIZAR_TAMANHOS.md), no molde do tema: é do usuário, no servidor;
 * o AuthProvider aplica o dele ao carregá-lo e trocar aqui grava nele. O localStorage é só cache — pinta a
 * tela de login e o primeiro desenho antes do usuário chegar.
 */
export const FontSizeProvider = ({ children }: { children: ReactNode }) => {
  const [fontSize, setFontSizeState] = useState<FontSize>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'small' ? 'small' : 'large';
    } catch {
      return 'large';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, fontSize);
    } catch {
      // sem armazenamento, o tamanho vale para esta visita
    }
    document.documentElement.classList.toggle(SMALL_FONT_CLASS, fontSize === 'small');
  }, [fontSize]);

  const setFontSize = useCallback((size: FontSize) => setFontSizeState(size), []);

  const toggleFontSize = () => {
    const next: FontSize = fontSize === 'small' ? 'large' : 'small';
    setFontSizeState(next);
    if (tokens.access()) {
      // Falhar aqui não desfaz a troca na tela: o tamanho só não acompanha o usuário em outro navegador.
      usersApi.preferences({ fontSize: fontSizeToUser(next) }).catch(() => {});
    }
  };

  return (
    <FontSizeContext.Provider value={{ fontSize, toggleFontSize, setFontSize, isSmall: fontSize === 'small' }}>
      {children}
    </FontSizeContext.Provider>
  );
};
