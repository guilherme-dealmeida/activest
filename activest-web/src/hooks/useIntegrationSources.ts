import { useQuery } from '@tanstack/react-query';
import { integrationsApi } from '@/api/integrations';

/**
 * O catálogo de fontes de integração (docs/planos/PLANO_INTEGRACOES.md, 10.1): nome, logo, credencial e capacidades
 * de cada uma. Muda só com deploy, por isso fica em cache durante a sessão.
 */
export const useIntegrationSources = () => {
  const { data } = useQuery({
    queryKey: ['integrations', 'sources'],
    queryFn: integrationsApi.sources,
    staleTime: Infinity,
  });
  return data;
};
