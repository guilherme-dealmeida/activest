import { useQuery } from '@tanstack/react-query';
import { assetsApi } from '@/api/assets';
import { AssetType } from '@/types';
import { Select } from '@/components/ui/Select';

interface Props {
  value: number | null;
  onChange: (type: AssetType | null) => void;
  disabled?: boolean;
}

/**
 * Seleção do tipo de ativo. É o primeiro campo do cadastro de transação:
 * o tipo determina quais ativos o autocomplete busca e se os campos de
 * renda fixa aparecem.
 */
export const AssetTypeSelect = ({ value, onChange, disabled }: Props) => {
  const { data: types, isLoading } = useQuery<AssetType[]>({
    queryKey: ['assetTypes'],
    queryFn: assetsApi.getTypes
  });

  return (
    <Select
      label="Tipo de Ativo"
      value={value ?? ''}
      disabled={disabled || isLoading}
      required
      onChange={(e) => {
        const id = Number(e.target.value);
        onChange((types || []).find(t => t.id === id) ?? null);
      }}
    >
      <option value="">{isLoading ? 'Carregando...' : 'Selecione o tipo'}</option>
      {(types || []).map(t => (
        <option key={t.id} value={t.id}>{t.description || t.name}</option>
      ))}
    </Select>
  );
};
