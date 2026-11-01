import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { integrationsApi } from '@/api/integrations';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { IntegrationConnection, IntegrationSource } from '@/types';
import { SOURCE_HELP } from './sourceHelp';

const FORM_ID = 'connect-source-form';

interface Props {
  /** A fonte escolhida no catálogo; nulo = fechado. */
  source: IntegrationSource | null;
  onClose: () => void;
  onConnected: (c: IntegrationConnection) => void;
}

/**
 * Conectar uma fonte (docs/planos/PLANO_INTEGRACOES.md, 10.1). Um formulário por tipo de credencial:
 * - API_KEY: chave e segredo, que vivem só no estado deste formulário (fechar limpa tudo); a API confere a chave na
 *   fonte antes de gravar — recusada ou com permissão demais, o motivo sai no toast e o modal fica aberto;
 * - DOCUMENT: o CPF do perfil, mascarado e travado (sem CPF no perfil, o link para Configurações); a conexão pode
 *   nascer aguardando a autorização do investidor na fonte.
 * O passo a passo e o texto da autorização são da fonte ({@link SOURCE_HELP}).
 */
export const ConnectSourceModal = ({ source, onClose, onConnected }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const { user } = useAuth();
  const profileCpf = user?.cpfMasked;
  const [label, setLabel] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [showSecret, setShowSecret] = useState(false);
  const [consent, setConsent] = useState(false);

  const clear = (name = '') => {
    setLabel(name);
    setApiKey('');
    setApiSecret('');
    setShowSecret(false);
    setConsent(false);
  };

  // Abrir com outra fonte começa do zero, com o nome dela no apelido.
  useEffect(() => { clear(source?.name ?? ''); }, [source?.provider]); // eslint-disable-line react-hooks/exhaustive-deps

  const close = () => {
    clear();
    onClose();
  };

  const isKey = source?.credential === 'API_KEY';

  const mutation = useMutation({
    mutationFn: () => integrationsApi.connect(isKey
      ? { provider: source!.provider, label: label.trim(), apiKey: apiKey.trim(), apiSecret: apiSecret.trim(),
        consentAccepted: consent }
      : { provider: source!.provider, label: label.trim(), consentAccepted: consent }),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      showToast(c.status === 'PENDING_AUTHORIZATION'
        ? `"${c.label}" criada. Falta autorizar o Activest na ${source?.name}: use o link no cartão da conexão.`
        : isKey ? `Conta "${c.label}" conectada. Chave conferida: só leitura.`
          : `"${c.label}" conectada. Sincronize para trazer as suas operações.`, 'success', 8000);
      clear();
      onConnected(c);
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível conectar.', 'error', 8000),
  });

  if (!source) return null;
  const help = SOURCE_HELP[source.provider];
  const credentialOk = isKey ? !!(apiKey.trim() && apiSecret.trim()) : !!profileCpf;
  const canSave = !!label.trim() && credentialOk && consent && !mutation.isPending;

  return (
    <Modal
      isOpen={!!source}
      onClose={close}
      title={`Conectar ${source.name}`}
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" type="button" onClick={close}>Cancelar</Button>
          <Button type="submit" form={FORM_ID} disabled={!canSave} isLoading={mutation.isPending}>
            Conectar
          </Button>
        </div>
      }
    >
      <form
        id={FORM_ID}
        className="space-y-5"
        onSubmit={(e) => { e.preventDefault(); if (canSave) mutation.mutate(); }}
        autoComplete="off"
      >
        <div className="rounded-md border border-surface-light bg-surface-light/40 p-4 text-sm text-gray-300 space-y-2">
          <p className="font-medium text-white">{help.title}</p>
          <ol className="list-decimal pl-5 space-y-1">
            {help.steps.map((step, i) => <li key={i}>{step}</li>)}
          </ol>
          {help.note && <p className="text-xs text-gray-400">{help.note}</p>}
        </div>

        <Input label="Apelido" value={label} maxLength={100} onChange={(e) => setLabel(e.target.value)}
          hint={`Para você reconhecer a conexão, ex.: “${source.name} principal”.`} required />

        {isKey ? (
          <>
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
          </>
        ) : profileCpf ? (
          <Input label="CPF" value={profileCpf} disabled readOnly
            hint="O CPF do seu cadastro. Para usar outro, altere-o em Configurações." />
        ) : (
          <div className="rounded-md border border-yellow-500/40 bg-yellow-500/10 p-3 text-sm text-gray-200">
            A conexão com a {source.name} usa o CPF do seu cadastro, e ele ainda não foi informado.{' '}
            <Link to="/settings" onClick={close} className="text-accent hover:underline">Cadastrar o CPF em Configurações</Link>
          </div>
        )}

        <label className="flex items-start gap-3 text-sm text-gray-300 cursor-pointer">
          <input type="checkbox" className="mt-1 accent-accent" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
          <span>{help.consent}</span>
        </label>
      </form>
    </Modal>
  );
};
