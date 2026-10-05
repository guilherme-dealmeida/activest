import React, { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CodeStep } from '@/components/auth/CodeStep';
import { usersApi } from '@/api/users';
import { CodeSent, User } from '@/types';
import { formErrorMessage } from '@/utils/apiError';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onChanged: (u: User) => void;
}

/** Trocar o e-mail (E3): o código vai para o endereço novo; sem ele, nada muda. */
export const EmailChangeModal = ({ isOpen, onClose, onChanged }: Props) => {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState<CodeSent | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const fechar = () => {
    setEmail('');
    setSent(null);
    setError('');
    onClose();
  };

  const pedir = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      setSent(await usersApi.requestEmailChange(email.trim()));
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível enviar o código.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={fechar} title="Alterar e-mail">
      {sent ? (
        <CodeStep
          sent={sent}
          confirmLabel="Confirmar novo e-mail"
          onConfirm={async (code) => {
            onChanged(await usersApi.confirmEmailChange(sent.requestId, code));
            fechar();
          }}
          onResend={() => usersApi.resendEmailChange(sent.requestId)}
          onBack={() => setSent(null)}
          backLabel="Trocar o endereço"
        />
      ) : (
        <form onSubmit={pedir} className="space-y-4">
          <p className="text-sm text-gray-300">
            O e-mail é o seu login. Enviaremos um código para o endereço novo; a troca só vale depois de confirmá-lo,
            e o endereço atual recebe um aviso.
          </p>
          {error && <p className="text-sm text-danger">{error}</p>}
          <Input label="Novo e-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" onClick={fechar}>Cancelar</Button>
            <Button type="submit" isLoading={busy} disabled={!email.trim()}>Enviar código</Button>
          </div>
        </form>
      )}
    </Modal>
  );
};
