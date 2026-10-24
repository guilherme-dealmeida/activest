/**
 * Padrões da foto de perfil (adendo de 05/10/2026). O arquivo original pode ser maior; o que vai para a
 * API é o recorte JPEG 512×512 do AvatarCropModal (a API aceita até 2 MB e confere tudo de novo).
 */
export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const AVATAR_MAX_MB = 5;
export const AVATAR_MIN_PX = 128;

/** Confere o arquivo escolhido antes de abrir o recorte. Devolve a mensagem de erro, ou null. */
export const validateAvatarFile = (file: File): string | null => {
  if (!AVATAR_TYPES.includes(file.type)) return 'Formato não aceito: use JPEG, PNG ou WebP.';
  if (file.size > AVATAR_MAX_MB * 1024 * 1024) return `A imagem passa de ${AVATAR_MAX_MB} MB.`;
  return null;
};
