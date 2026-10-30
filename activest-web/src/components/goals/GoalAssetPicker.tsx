import { useState } from 'react';
import { AssetTypeSelect } from '@/components/forms/AssetTypeSelect';
import { TickerAutocomplete } from '@/components/forms/TickerAutocomplete';
import { AssetType } from '@/types';

interface Props {
  /** O id do ativo escolhido (params.assetId). */
  value: unknown;
  /** O ticker e o tipo gravados com a meta (rótulos que o servidor preenche), para mostrar o ativo já escolhido. */
  ticker?: string | null;
  assetTypeLabel?: string | null;
  onChange: (assetId: number | null) => void;
}

/**
 * Ativo da meta por ativo (ATV-1/2/3): o tipo e depois o ticker, pela busca do catálogo — desde a V13 o ticker é único
 * só por tipo (META é cripto e ação do exterior), então a meta guarda o id do ativo (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md).
 * Vale também ativo que ainda não está na carteira.
 */
export const GoalAssetPicker = ({ value, ticker, assetTypeLabel, onChange }: Props) => {
  const [type, setType] = useState<AssetType | null>(null);
  const [text, setText] = useState('');
  const chosen = value !== null && value !== undefined && value !== '';

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <AssetTypeSelect value={type?.id ?? null} onChange={t => { setType(t); setText(''); onChange(null); }} />
      <div>
        <TickerAutocomplete value={text} typeId={type?.id ?? null}
          onChange={(t, asset) => { setText(t); onChange(asset ? asset.id : null); }} />
        {chosen && !text && ticker && (
          <p className="text-xs text-gray-400 mt-1">
            Escolhido: <span className="text-white font-medium">{ticker}</span>
            {assetTypeLabel ? ` (${assetTypeLabel})` : ''}. Para trocar, escolha o tipo e busque o ativo.
          </p>
        )}
      </div>
    </div>
  );
};
