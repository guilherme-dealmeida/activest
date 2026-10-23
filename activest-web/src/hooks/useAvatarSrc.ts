import { useQuery } from '@tanstack/react-query';
import { usersApi } from '@/api/users';
import { User } from '@/types';

/**
 * A foto do usuário: a enviada (buscada com o token, por versão — trocar a foto muda a versão e a
 * consulta), senão a do Google, senão a inicial do nome. Menu lateral e Configurações usam a mesma.
 */
export const useAvatarSrc = (user: User | null | undefined): string | undefined => {
  const version = user?.avatarVersion;
  const { data } = useQuery({
    queryKey: ['avatar', version],
    queryFn: async () => URL.createObjectURL(await usersApi.avatarBlob(version!)),
    enabled: !!version,
    staleTime: Infinity,
    gcTime: 60 * 60 * 1000,
  });
  if (version) return data;
  return user?.avatarUrl || undefined;
};

