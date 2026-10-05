import { apiClient } from './client';
import { CodeSent, TokenResponse, User, UserUpdateRequest } from '@/types';

/** O próprio usuário (UserController, /users/me). */
export const usersApi = {
  me: async () => (await apiClient.get<User>('/users/me')).data,
  update: async (body: UserUpdateRequest) => (await apiClient.put<User>('/users/me', body)).data,
  preferences: async (theme: User['theme']) => (await apiClient.put<User>('/users/me/preferences', { theme })).data,
  /** Devolve tokens novos: a troca derruba as outras sessões. */
  changePassword: async (currentPassword: string | undefined, newPassword: string) =>
    (await apiClient.put<TokenResponse>('/users/me/password', { currentPassword, newPassword })).data,
  requestEmailChange: async (newEmail: string) => (await apiClient.post<CodeSent>('/users/me/email', { newEmail })).data,
  resendEmailChange: async (requestId: string) => (await apiClient.post<CodeSent>('/users/me/email/resend', { requestId })).data,
  confirmEmailChange: async (requestId: string, code: string) =>
    (await apiClient.post<User>('/users/me/email/confirm', { requestId, code })).data,
  linkGoogle: async (credential: string) => (await apiClient.post<User>('/users/me/google', { credential })).data,
  unlinkGoogle: async () => (await apiClient.delete<User>('/users/me/google')).data,
  acceptTerms: async () => (await apiClient.post<User>('/users/me/terms')).data,
  requestDeletion: async () => (await apiClient.post<CodeSent>('/users/me/delete-request')).data,
  resendDeletion: async (requestId: string) => (await apiClient.post<CodeSent>('/users/me/delete-request/resend', { requestId })).data,
  /** Foto de perfil: o recorte 512×512 em JPEG que o AvatarCropModal gera. */
  uploadAvatar: async (image: Blob) => {
    const form = new FormData();
    form.append('file', image, 'avatar.jpg');
    return (await apiClient.put<User>('/users/me/avatar', form, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
  },
  deleteAvatar: async () => (await apiClient.delete<User>('/users/me/avatar')).data,
  /** A imagem vem pelo cliente (com o token): <img src> não mandaria o header. */
  avatarBlob: async (version: number) =>
    (await apiClient.get<Blob>('/users/me/avatar', { params: { v: version }, responseType: 'blob' })).data,
  deleteAccount: async (requestId: string, code: string) => {
    await apiClient.delete('/users/me', { data: { requestId, code } });
  },
};
