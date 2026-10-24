/**
 * Copia para a área de transferência. O `navigator.clipboard` só existe em contexto seguro (https ou localhost);
 * acessando por IP da rede (http://192.168…) cai no `execCommand('copy')` com um campo temporário.
 */
export const copyToClipboard = async (text: string): Promise<boolean> => {
  try {
    if (window.isSecureContext && navigator.clipboard) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch { /* tenta o fallback */ }
  try {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(area);
    return ok;
  } catch {
    return false;
  }
};
