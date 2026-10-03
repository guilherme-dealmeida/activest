import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { integrationsApi } from '@/api/integrations';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { formatCpf, isValidCpf } from '@/utils/integrations';
import { IntegrationConnection } from '@/types';

const FORM_ID = 'connect-b3-form';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onConnected: (c: IntegrationConnection) => void;
}

/**
 * Conectar a Área do Investidor da B3 (docs/planos/PLANO_INTEGRACAO_B3.md, 6.9 e F6).
 *
 * Só o CPF: quem libera o acesso é o próprio investidor, na B3, com o login dele (opt-in). Por isso a
 * conexão nasce "aguardando autorização" e o cartão dela mostra o link. O CPF vive só no estado deste
 * formulário; a API o guarda cifrado e devolve só os 2 últimos dígitos.
 */
export const ConnectB3Modal = ({ isOpen, onClose, onConnected }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [label, setLabel] = useState('B3');
  const [cpf, setCpf] = useState('');
  const [consent, setConsent] = useState(false);

  const limpar = () => {
    setLabel('B3');
    setCpf('');
    setConsent(false);
  };

  const fechar = () => {
    limpar();
    onClose();
  };

  const mutation = useMutation({
    mutationFn: () => integrationsApi.connect({
      provider: 'B3', label: label.trim(), documentNumber: cpf.replace(/\D/g, ''), consentAccepted: consent,
    }),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
      showToast(c.status === 'PENDING_AUTHORIZATION'
        ? `"${c.label}" criada. Falta autorizar o PrimeVest na B3: use o link no cartão da conexão.`
        : `"${c.label}" conectada. Sincronize para trazer as suas negociações.`, 'success', 8000);
      limpar();
      onConnected(c);
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível conectar.', 'error', 8000),
  });

  const cpfCompleto = cpf.replace(/\D/g, '').length === 11;
  const cpfOk = isValidCpf(cpf);
  const podeSalvar = label.trim() && cpfOk && consent && !mutation.isPending;

  return (
    <Modal
      isOpen={isOpen}
      onClose={fechar}
      title="Conectar B3 (Área do Investidor)"
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
          <p className="font-medium text-white">Como funciona</p>
          <ol className="list-decimal pl-5 space-y-1">
            <li>Informe o seu CPF e conecte.</li>
            <li>No cartão da conexão, abra <span className="text-white">Autorizar na B3</span>, entre com o seu login da
              Área do Investidor e autorize o PrimeVest.</li>
            <li>Volte e sincronize. Você confere as compras e vendas antes de entrarem na carteira.</li>
          </ol>
          <p className="text-xs text-gray-400">
            Entram as negociações de ações, FIIs, ETFs e BDRs. Proventos e eventos do papel a B3 só confere: a carteira
            já os calcula pelo catálogo. A B3 publica o dia anterior a partir das 8h e aceita uma consulta por dia.
          </p>
        </div>

        <Input label="Apelido" value={label} maxLength={100} onChange={(e) => setLabel(e.target.value)}
          hint="Para você reconhecer a conexão, ex.: “B3”." required />
        <Input label="CPF" value={cpf} inputMode="numeric" maxLength={14}
          onChange={(e) => setCpf(formatCpf(e.target.value))} spellCheck={false}
          error={cpfCompleto && !cpfOk ? 'CPF inválido.' : undefined}
          hint="Guardado cifrado. Na tela aparecem só os 2 últimos dígitos." required />

        <label className="flex items-start gap-3 text-sm text-gray-300 cursor-pointer">
          <input type="checkbox" className="mt-1 accent-accent" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
          <span>
            Autorizo o PrimeVest a <span className="text-white">ler</span> na Área do Investidor da B3 as minhas
            negociações, movimentações, posição e proventos, para importar as compras e vendas e conferir a carteira.
            A leitura continua até eu desconectar aqui ou revogar na B3; ao desconectar, o CPF é apagado na hora.
          </span>
        </label>
      </form>
    </Modal>
  );
};
