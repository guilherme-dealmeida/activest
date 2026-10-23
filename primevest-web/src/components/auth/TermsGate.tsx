import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { usersApi } from '@/api/users';
import { Button } from '@/components/ui/Button';
import { formErrorMessage } from '@/utils/apiError';

/**
 * Novo aceite dos termos (docs/planos/PLANO_CADASTRO_USUARIO.md, D19): com a versão aceita diferente
 * da atual, a tela fica coberta até aceitar ou sair. A API já recusa o resto (403 TERMS_REQUIRED).
 */
export const TermsGate = () => {
  const { user, setUser, logout } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (!user?.termsPending) return null;

  const accept = async () => {
    setBusy(true);
    setError('');
    try {
      setUser(await usersApi.acceptTerms());
      window.location.reload(); // as telas que levaram 403 carregam de novo
    } catch (err) {
      setError(formErrorMessage(err, 'Não foi possível registrar o aceite.'));
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4">
      <div className="w-full max-w-lg rounded-xl bg-surface border border-surface-light p-6 space-y-4">
        <h3 className="text-lg font-semibold text-white">Termos atualizados</h3>
        <p className="text-sm text-gray-300">
          {user.termsVersion
            ? 'Os Termos de Uso e a Política de Privacidade do PrimeVest mudaram desde o seu último aceite.'
            : 'Para continuar, aceite os Termos de Uso e a Política de Privacidade do PrimeVest.'}
          {' '}Leia os textos e aceite a nova versão para continuar.
        </p>
        <div className="flex gap-4 text-sm">
          <Link to="/terms" target="_blank" className="text-accent hover:underline">Termos de Uso</Link>
          <Link to="/privacy" target="_blank" className="text-accent hover:underline">Política de Privacidade</Link>
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-3 pt-2">
          <Button variant="ghost" onClick={logout}>Sair</Button>
          <Button onClick={accept} isLoading={busy}>Li e aceito</Button>
        </div>
      </div>
    </div>
  );
};
