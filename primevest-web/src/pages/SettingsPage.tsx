import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Save, User as UserIcon, Mail, CreditCard, KeyRound, Moon, Sun, ShieldAlert, FileText, Camera, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { useToast } from '@/context/ToastContext';
import { useGoogleSignIn } from '@/hooks/useGoogleSignIn';
import { usersApi } from '@/api/users';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { EmailChangeModal } from '@/components/settings/EmailChangeModal';
import { DeleteAccountModal } from '@/components/settings/DeleteAccountModal';
import { AvatarCropModal } from '@/components/settings/AvatarCropModal';
import { AVATAR_MAX_MB, AVATAR_MIN_PX, AVATAR_TYPES, validateAvatarFile } from '@/utils/avatar';
import { UserAvatar } from '@/components/ui/UserAvatar';
import { formErrorMessage } from '@/utils/apiError';
import { formatPhone, isPhoneComplete, phoneFromApi } from '@/utils/phone';
import { formatCpf, isValidCpf } from '@/utils/integrations';
import { tokens } from '@/api/tokens';

const Section = ({ title, icon, children, danger }: {
  title: string; icon: React.ReactNode; children: React.ReactNode; danger?: boolean;
}) => (
  <section className={`bg-surface rounded-lg border p-6 ${danger ? 'border-danger/40' : 'border-white/10'}`}>
    <h3 className={`mb-5 flex items-center gap-2 text-base font-semibold ${danger ? 'text-danger' : 'text-white'}`}>
      {icon}{title}
    </h3>
    {children}
  </section>
);

const formatDate = (iso?: string) => (iso ? new Date(iso).toLocaleDateString('pt-BR') : '—');

/**
 * Configurações (docs/planos/PLANO_CADASTRO_USUARIO.md, seção 4): perfil, acesso, preferências e
 * exclusão da conta. Até 05/10/2026 a tela era uma maquete com valores fixos e "salvar" de mentira.
 */
export const SettingsPage = () => {
  const { user, setUser, startSession, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const { showToast } = useToast();

  // ---------------- perfil
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [cpf, setCpf] = useState('');
  const [editingCpf, setEditingCpf] = useState(false);
  const [saving, setSaving] = useState(false);
  const [emailModal, setEmailModal] = useState(false);
  const [deleteModal, setDeleteModal] = useState(false);

  // ---------------- foto
  const photoInput = React.useRef<HTMLInputElement>(null);
  const [photoFile, setPhotoFile] = useState<File | null>(null);

  const choosePhoto = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = ''; // escolher o mesmo arquivo de novo também dispara
    if (!f) return;
    const problem = validateAvatarFile(f);
    if (problem) {
      showToast(problem, 'error', 8000);
      return;
    }
    setPhotoFile(f);
  };

  const savePhoto = async (image: Blob) => {
    try {
      setUser(await usersApi.uploadAvatar(image));
      setPhotoFile(null);
      showToast('Foto atualizada.', 'success');
    } catch (err) {
      throw new Error(formErrorMessage(err, 'Não foi possível salvar a foto.'));
    }
  };

  const removePhoto = async () => {
    try {
      setUser(await usersApi.deleteAvatar());
      showToast('Foto removida.', 'success');
    } catch (err) {
      showToast(formErrorMessage(err, 'Não foi possível remover a foto.'), 'error', 8000);
    }
  };

  useEffect(() => {
    if (!user) return;
    setName(user.name);
    setPhone(phoneFromApi(user.phone));
    setCpf('');
    setEditingCpf(!user.cpfMasked);
  }, [user]);

  const cpfDigits = cpf.replace(/\D/g, '');
  const cpfOk = cpfDigits.length === 0 || isValidCpf(cpf);
  const profileOk = name.trim() && isPhoneComplete(phone) && cpfOk;

  const saveProfile = async (e: React.FormEvent, removeCpf = false) => {
    e.preventDefault();
    setSaving(true);
    try {
      // CPF: ausente = não mexe (a API só devolve o mascarado); '' = tira; número = troca.
      const cpfToSend = removeCpf ? '' : (editingCpf && cpfDigits ? cpfDigits : undefined);
      const updated = await usersApi.update({ name: name.trim(), phone: phone.trim() || null, cpf: cpfToSend });
      setUser(updated);
      showToast(removeCpf ? 'CPF removido.' : 'Dados salvos.', 'success');
    } catch (err) {
      showToast(formErrorMessage(err, 'Não foi possível salvar os dados.'), 'error', 8000);
    } finally {
      setSaving(false);
    }
  };

  // ---------------- senha
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  const passwordOk = newPassword.length >= 8 && newPassword.length <= 72 && newPassword === passwordConfirm
    && (!user?.hasPassword || currentPassword.length > 0);

  const savePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setChangingPassword(true);
    try {
      const defined = !user?.hasPassword;
      // A troca derruba as sessões (D15): os tokens novos mantêm esta aberta.
      await startSession(await usersApi.changePassword(user?.hasPassword ? currentPassword : undefined, newPassword));
      setCurrentPassword('');
      setNewPassword('');
      setPasswordConfirm('');
      showToast(defined ? 'Senha definida. Você também pode entrar por e-mail e senha.'
        : 'Senha alterada. As outras sessões abertas foram encerradas.', 'success', 6000);
    } catch (err) {
      showToast(formErrorMessage(err, 'Não foi possível alterar a senha.'), 'error', 8000);
    } finally {
      setChangingPassword(false);
    }
  };

  // ---------------- Google
  const link = useCallback(async (credential: string) => {
    setUser(await usersApi.linkGoogle(credential));
    showToast('Conta Google vinculada.', 'success');
  }, [setUser, showToast]);
  const google = useGoogleSignIn({ onCredential: link });

  const unlink = async () => {
    try {
      setUser(await usersApi.unlinkGoogle());
      showToast('Conta Google desvinculada.', 'success');
    } catch (err) {
      showToast(formErrorMessage(err, 'Não foi possível desvincular.'), 'error', 8000);
    }
  };

  if (!user) return null;

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-white">Configurações</h2>
        <p className="text-gray-400 mt-1">Seus dados, o acesso à conta e as preferências</p>
      </div>

      {/* Perfil */}
      <Section title="Perfil" icon={<UserIcon className="h-4 w-4 text-accent" />}>
        {/* Foto centralizada, com troca por upload (adendo de 05/10/2026). */}
        <div className="flex flex-col items-center text-center mb-8">
          <button type="button" onClick={() => photoInput.current?.click()}
            className="group relative rounded-full focus:outline-none focus:ring-2 focus:ring-accent"
            title="Alterar foto" aria-label="Alterar foto">
            <UserAvatar user={user} className="h-28 w-28 border-2 border-accent/40" textClassName="text-4xl font-bold" />
            <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
              <Camera className="h-7 w-7 text-white" />
            </span>
            <span className="absolute bottom-1 right-1 flex h-8 w-8 items-center justify-center rounded-full bg-accent text-black shadow">
              <Camera className="h-4 w-4" />
            </span>
          </button>
          <input ref={photoInput} type="file" accept={AVATAR_TYPES.join(',')} className="hidden" onChange={choosePhoto} />
          <h4 className="mt-4 text-lg font-semibold text-white">{user.name}</h4>
          <p className="text-gray-400 text-sm">Conta criada em {formatDate(user.createdAt)}</p>
          <div className="mt-3 flex gap-2">
            <Button type="button" size="sm" variant="outline" onClick={() => photoInput.current?.click()}>
              {user.avatarVersion ? 'Trocar foto' : 'Enviar foto'}
            </Button>
            {user.avatarVersion && (
              <Button type="button" size="sm" variant="ghost" onClick={removePhoto}>Remover foto</Button>
            )}
          </div>
          <p className="mt-2 text-xs text-gray-500">
            JPEG, PNG ou WebP, até {AVATAR_MAX_MB} MB e com pelo menos {AVATAR_MIN_PX}×{AVATAR_MIN_PX} px.
            {!user.avatarVersion && user.avatarUrl && ' Hoje aparece a foto da sua conta Google.'}
          </p>
        </div>

        <form onSubmit={saveProfile} className="space-y-5">
          <Input label="Nome completo" value={name} onChange={(e) => setName(e.target.value)} maxLength={255} required />

          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">
              <Mail className="inline h-4 w-4 mr-1.5 -mt-0.5" />E-mail
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-sm text-white">{user.email}</span>
              {user.emailVerified
                ? <Badge variant="success">verificado</Badge>
                : <Badge variant="warning">não verificado</Badge>}
              <Button type="button" size="sm" variant="outline" onClick={() => setEmailModal(true)}>Alterar</Button>
            </div>
            <p className="mt-1 text-xs text-gray-500">É o seu login. A troca é confirmada por um código enviado ao novo endereço.</p>
          </div>

          <div className="grid gap-5 sm:grid-cols-2">
            <div>
              <Input label="Celular" value={phone} inputMode="tel" placeholder="(11) 99999-9999"
                onChange={(e) => setPhone(formatPhone(e.target.value))}
                error={!isPhoneComplete(phone) ? 'Informe DDD e número.' : undefined}
                hint="Opcional. Contato, não serve para entrar." />
            </div>
            <div>
              {editingCpf ? (
                <Input label="CPF" value={cpf} inputMode="numeric" maxLength={14} placeholder="000.000.000-00"
                  onChange={(e) => setCpf(formatCpf(e.target.value))}
                  error={cpfDigits.length === 11 && !cpfOk ? 'CPF inválido.' : undefined}
                  hint={user.cpfMasked ? 'Digite o novo CPF e salve.' : 'Opcional. Usado na integração com a B3.'} />
              ) : (
                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-1.5">
                    <CreditCard className="inline h-4 w-4 mr-1.5 -mt-0.5" />CPF
                  </label>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-white">{user.cpfMasked}</span>
                    <Button type="button" size="sm" variant="outline" onClick={() => setEditingCpf(true)}>Alterar</Button>
                    <Button type="button" size="sm" variant="ghost" onClick={(e) => saveProfile(e, true)}>Remover</Button>
                  </div>
                  <p className="mt-1 text-xs text-gray-500">Guardado cifrado. Não muda enquanto houver conexão com a B3.</p>
                </div>
              )}
            </div>
          </div>

          <div className="pt-4 border-t border-white/10 flex gap-3">
            <Button type="submit" disabled={!profileOk} isLoading={saving}>
              <Save className="w-4 h-4 mr-2" />Salvar alterações
            </Button>
            {editingCpf && user.cpfMasked && (
              <Button type="button" variant="ghost" onClick={() => { setEditingCpf(false); setCpf(''); }}>Manter CPF atual</Button>
            )}
          </div>
        </form>
      </Section>

      {/* Acesso */}
      <Section title="Acesso" icon={<KeyRound className="h-4 w-4 text-accent" />}>
        <div className="space-y-8">
          <form onSubmit={savePassword} className="space-y-4">
            <div>
              <h4 className="text-sm font-medium text-white">{user.hasPassword ? 'Alterar senha' : 'Definir senha'}</h4>
              <p className="text-xs text-gray-500 mt-0.5">
                {user.hasPassword
                  ? 'Ao trocar, as outras sessões abertas são encerradas.'
                  : 'Sua conta entra só pelo Google. Defina uma senha para entrar também por e-mail.'}
              </p>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              {user.hasPassword && (
                <Input label="Senha atual" type="password" value={currentPassword} autoComplete="current-password"
                  onChange={(e) => setCurrentPassword(e.target.value)} />
              )}
              <Input label="Nova senha" type="password" value={newPassword} autoComplete="new-password"
                onChange={(e) => setNewPassword(e.target.value)}
                error={newPassword && (newPassword.length < 8 || newPassword.length > 72) ? 'De 8 a 72 caracteres.' : undefined} />
              <Input label="Confirme" type="password" value={passwordConfirm} autoComplete="new-password"
                onChange={(e) => setPasswordConfirm(e.target.value)}
                error={passwordConfirm && newPassword !== passwordConfirm ? 'As senhas não conferem.' : undefined} />
            </div>
            <Button type="submit" disabled={!passwordOk} isLoading={changingPassword}>
              {!changingPassword && <KeyRound className="h-4 w-4 mr-2" />}
              {user.hasPassword ? 'Alterar senha' : 'Definir senha'}
            </Button>
          </form>

          <div className="pt-6 border-t border-white/10 space-y-3">
            <div className="flex flex-wrap items-center gap-3">
              <h4 className="text-sm font-medium text-white">Conta Google</h4>
              {user.googleLinked ? <Badge variant="success">vinculada</Badge> : <Badge>não vinculada</Badge>}
            </div>
            {user.googleLinked ? (
              <div className="flex flex-wrap items-center gap-3">
                <Button type="button" size="sm" variant="ghost" onClick={unlink} disabled={!user.hasPassword}>
                  Desvincular
                </Button>
                {!user.hasPassword && (
                  <span className="text-xs text-gray-500">Defina uma senha antes: sem ela não haveria como entrar.</span>
                )}
              </div>
            ) : google.enabled ? (
              <div>
                <p className="text-xs text-gray-500 mb-2">Vincule para entrar com um clique pelo Google.</p>
                {google.error && <p className="text-sm text-danger mb-2">{google.error}</p>}
                <div className="min-h-[44px]">
                  {google.isLoading ? <span className="text-sm text-gray-400">Vinculando...</span> : <div ref={google.containerRef} />}
                </div>
              </div>
            ) : (
              <p className="text-xs text-gray-500">O login com Google não está configurado neste ambiente.</p>
            )}
          </div>

          <div className="pt-6 border-t border-white/10">
            <h4 className="text-sm font-medium text-white flex items-center gap-2"><FileText className="h-4 w-4" />Termos</h4>
            <p className="text-sm text-gray-400 mt-1">
              {user.termsAcceptedAt
                ? <>Versão <span className="text-white">{user.termsVersion}</span> aceita em {formatDate(user.termsAcceptedAt)}.</>
                : 'Nenhum aceite registrado.'}{' '}
              <Link to="/terms" target="_blank" className="text-accent hover:underline">Termos de Uso</Link> ·{' '}
              <Link to="/privacy" target="_blank" className="text-accent hover:underline">Política de Privacidade</Link>
            </p>
          </div>
        </div>
      </Section>

      {/* Preferências */}
      <Section title="Preferências" icon={isDark ? <Moon className="h-4 w-4 text-accent" /> : <Sun className="h-4 w-4 text-accent" />}>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm text-white">Tema</p>
            <p className="text-xs text-gray-500">Fica guardado na sua conta e vale em qualquer navegador.</p>
          </div>
          <div className="inline-flex rounded-md border border-white/10 p-1">
            {(['dark', 'light'] as const).map((t) => (
              <button key={t} type="button"
                onClick={() => { if ((t === 'dark') !== isDark) toggleTheme(); }}
                className={`px-4 py-1.5 text-sm rounded ${(t === 'dark') === isDark ? 'bg-accent text-black font-medium' : 'text-gray-400 hover:text-white'}`}>
                {t === 'dark' ? 'Escuro' : 'Claro'}
              </button>
            ))}
          </div>
        </div>
      </Section>

      {/* Zona de perigo */}
      <Section title="Zona de perigo" icon={<ShieldAlert className="h-4 w-4" />} danger>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className="text-sm text-gray-300">Excluir a conta apaga definitivamente todos os seus dados no PrimeVest.</p>
          <Button type="button" variant="danger" onClick={() => setDeleteModal(true)}>
            <Trash2 className="h-4 w-4 mr-2" />
            Excluir conta
          </Button>
        </div>
      </Section>

      <EmailChangeModal isOpen={emailModal} onClose={() => setEmailModal(false)}
        onChanged={(u) => { setUser(u); showToast('E-mail alterado.', 'success'); }} />
      <AvatarCropModal file={photoFile} onClose={() => setPhotoFile(null)} onConfirm={savePhoto} />
      <DeleteAccountModal isOpen={deleteModal} onClose={() => setDeleteModal(false)}
        onDeleted={() => { tokens.clear(); logout(); }} />
    </div>
  );
};
