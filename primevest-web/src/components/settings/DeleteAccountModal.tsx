import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { CodeStep } from '@/components/auth/CodeStep';
import { usersApi } from '@/api/users';
import { CodeSent } from '@/types';
import { formErrorMessage } from '@/utils/apiError';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onDeleted: () => void;
}

/** Excluir a conta (D14): definitiva, com o código enviado ao e-mail da conta. */
export const DeleteAccountModal = ({ isOpen, onClose, onDeleted }: Props) => {
  const [sent, setSent] = useState<CodeSent | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const close = () => {
    setSent(null);
    setError('');
    onClose();
  };

  const request = async () => {
    setError('');
    setBusy(true);
    try {
      setSent(await usersApi.requestDeletion());
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível enviar o código.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={close} title="Excluir a conta">
      {sent ? (
        <CodeStep
          sent={sent}
          danger
          confirmLabel="Excluir definitivamente"
          onConfirm={async (code) => {
            await usersApi.deleteAccount(sent.requestId, code);
            onDeleted();
          }}
          onResend={() => usersApi.resendDeletion(sent.requestId)}
          onBack={close}
          backLabel="Cancelar"
        />
      ) : (
        <div className="space-y-4">
          <div className="rounded-md border border-danger/40 bg-danger/10 p-4 text-sm text-gray-200 space-y-2">
            <p className="font-medium text-danger">Esta ação não tem volta.</p>
            <p>
              A conta e tudo o que está ligado a ela — carteira, transações, proventos, estratégia, metas, preços-teto
              e integrações — serão apagados definitivamente.
            </p>
          </div>
          <p className="text-sm text-gray-300">Para confirmar, enviaremos um código para o e-mail da conta.</p>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" onClick={close}>Cancelar</Button>
            <Button type="button" variant="danger" onClick={request} isLoading={busy}>Enviar código</Button>
          </div>
        </div>
      )}
    </Modal>
  );
};
