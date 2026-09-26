import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Eye, EyeOff } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { integrationsApi } from '@/api/integrations';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { IntegrationConnection } from '@/types';

const FORM_ID = 'connect-binance-form';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onConnected: (c: IntegrationConnection) => void;
}

/**
 * Conectar uma conta da Binance (PLANO_CRIPTO_BINANCE.md, 6.4 e 6.7).
 *
 * Chave e segredo vivem só no estado deste formulário: não vão para o
 * localStorage, e fechar o modal limpa tudo. A API confere a chave na Binance
 * antes de gravar — recusada ou com permissão demais, o motivo sai no toast e
 * o modal fica aberto para corrigir.
 */
export const ConnectBinanceModal = ({ isOpen, onClose, onConnected }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [label, setLabel] = useState('Binance');
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [showSecret, setShowSecret] = useState(false);
  const [consent, setConsent] = useState(false);

  const limpar = () => {
    setLabel('Binance');
    setApiKey('');
    setApiSecret('');
    setShowSecret(false);
    setConsent(false);
  };

  const fechar = () => {
    limpar();
    onClose();
  };

  const mutation = useMutation({
    mutationFn: () => integrationsApi.connect({
      provider: 'BINANCE', label: label.trim(), apiKey: apiKey.trim(), apiSecret: apiSecret.trim(), consentAccepted: consent,
    }),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      showToast(`Conta "${c.label}" conectada. Chave conferida: só leitura.`, 'success');
      limpar();
      onConnected(c);
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível conectar a conta.', 'error', 8000),
  });

  const podeSalvar = label.trim() && apiKey.trim() && apiSecret.trim() && consent && !mutation.isPending;

  return (
    <Modal
      isOpen={isOpen}
      onClose={fechar}
      title="Conectar Binance"
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" type="button" onClick={fechar}>Cancelar</Button>
          <Button type="submit" form={FORM_ID} disabled={!podeSalvar} isLoading={mutation.isPending}>
            Conectar
          </Button>
        </div>
      }
    >
      <form
        id={FORM_ID}
        className="space-y-5"
        onSubmit={(e) => { e.preventDefault(); if (podeSalvar) mutation.mutate(); }}
        autoComplete="off"
      >
        <div className="rounded-md border border-surface-light bg-surface-light/40 p-4 text-sm text-gray-300 space-y-2">
          <p className="font-medium text-white">Como criar a chave na Binance</p>
          <ol className="list-decimal pl-5 space-y-1">
            <li>No app ou site da Binance, abra <span className="text-white">Perfil › Gerenciamento de API</span> e crie uma chave.</li>
            <li>Deixe marcada <span className="text-white">apenas “Enable Reading”</span>. O PrimeVest recusa chave que negocia, saca ou transfere.</li>
            <li>Restringir a chave a um IP é opcional e mais seguro.</li>
            <li>Copie a chave (API Key) e o segredo (Secret Key) para os campos abaixo.</li>
          </ol>
        </div>

        <Input label="Apelido" value={label} maxLength={100} onChange={(e) => setLabel(e.target.value)}
          hint="Para você reconhecer a conta, ex.: “Binance principal”." required />
        <Input label="Chave de API (API Key)" value={apiKey} onChange={(e) => setApiKey(e.target.value)}
          spellCheck={false} required />
        <div className="relative">
          <Input label="Segredo (Secret Key)" type={showSecret ? 'text' : 'password'} value={apiSecret}
            onChange={(e) => setApiSecret(e.target.value)} spellCheck={false} autoComplete="new-password"
            hint="Guardado cifrado. Nunca é mostrado de novo." required />
          <button type="button" onClick={() => setShowSecret(v => !v)}
            className="absolute right-3 top-9 text-gray-400 hover:text-white"
            title={showSecret ? 'Ocultar' : 'Mostrar'} aria-label={showSecret ? 'Ocultar segredo' : 'Mostrar segredo'}>
            {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>

        <label className="flex items-start gap-3 text-sm text-gray-300 cursor-pointer">
          <input type="checkbox" className="mt-1 accent-accent" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
          <span>
            Autorizo o PrimeVest a <span className="text-white">ler</span> na minha conta da Binance os saldos, negociações,
            conversões, compras e depósitos, para importar minhas operações de cripto. A leitura continua até eu
            desconectar; ao desconectar, a chave é apagada na hora.
          </span>
        </label>
      </form>
    </Modal>
  );
};
