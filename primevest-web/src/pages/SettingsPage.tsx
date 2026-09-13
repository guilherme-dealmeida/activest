import React, { useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Save, User, Mail, Phone, CreditCard } from 'lucide-react';

export const SettingsPage = () => {
  const { user } = useAuth();
  
  const [formData, setFormData] = useState({
    fullName: user?.name || 'Guilherme Henrique',
    email: user?.email || 'guilherme@email.com',
    phone: '(41) 99999-9999',
    document: '123.456.789-00',
  });

  const [saved, setSaved] = useState(false);

  const handleChange = (field: string, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    setSaved(false);
  };

  const formatPhone = (value: string) => {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (digits.length <= 2) return `(${digits}`;
    if (digits.length <= 7) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
  };

  const formatDocument = (value: string) => {
    const digits = value.replace(/\D/g, '');
    if (digits.length <= 11) {
      // CPF
      if (digits.length <= 3) return digits;
      if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`;
      if (digits.length <= 9) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`;
      return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
    } else {
      // CNPJ
      const cnpj = digits.slice(0, 14);
      if (cnpj.length <= 2) return cnpj;
      if (cnpj.length <= 5) return `${cnpj.slice(0, 2)}.${cnpj.slice(2)}`;
      if (cnpj.length <= 8) return `${cnpj.slice(0, 2)}.${cnpj.slice(2, 5)}.${cnpj.slice(5)}`;
      if (cnpj.length <= 12) return `${cnpj.slice(0, 2)}.${cnpj.slice(2, 5)}.${cnpj.slice(5, 8)}/${cnpj.slice(8)}`;
      return `${cnpj.slice(0, 2)}.${cnpj.slice(2, 5)}.${cnpj.slice(5, 8)}/${cnpj.slice(8, 12)}-${cnpj.slice(12)}`;
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // Mock save
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-white dark:text-white light:text-gray-900">Configurações</h2>
        <p className="text-gray-400 mt-1">Gerencie suas informações pessoais</p>
      </div>

      <div className="bg-surface dark:bg-surface light:bg-white rounded-lg border border-white/10 dark:border-white/10 light:border-gray-200 p-8">
        <div className="flex items-center mb-8">
          <div className="h-16 w-16 rounded-full bg-accent/20 flex items-center justify-center border-2 border-accent/40">
            <span className="text-accent text-2xl font-bold">{formData.fullName[0]}</span>
          </div>
          <div className="ml-4">
            <h3 className="text-lg font-semibold text-white dark:text-white light:text-gray-900">{formData.fullName}</h3>
            <p className="text-gray-400 text-sm">{formData.email}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div className="relative">
            <label className="block text-sm font-medium text-gray-300 dark:text-gray-300 light:text-gray-700 mb-1.5">
              <User className="inline h-4 w-4 mr-1.5 -mt-0.5" />
              Nome Completo
            </label>
            <input
              type="text"
              value={formData.fullName}
              onChange={(e) => handleChange('fullName', e.target.value)}
              className="flex h-10 w-full rounded-md border border-white/10 dark:border-white/10 light:border-gray-300 bg-black/20 dark:bg-black/20 light:bg-gray-50 px-3 py-2 text-sm text-white dark:text-white light:text-gray-900 focus:ring-2 focus:ring-accent focus:border-transparent transition-colors"
              placeholder="Seu nome completo"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-300 dark:text-gray-300 light:text-gray-700 mb-1.5">
              <Mail className="inline h-4 w-4 mr-1.5 -mt-0.5" />
              Email
            </label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => handleChange('email', e.target.value)}
              className="flex h-10 w-full rounded-md border border-white/10 dark:border-white/10 light:border-gray-300 bg-black/20 dark:bg-black/20 light:bg-gray-50 px-3 py-2 text-sm text-white dark:text-white light:text-gray-900 focus:ring-2 focus:ring-accent focus:border-transparent transition-colors"
              placeholder="seu@email.com"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-300 dark:text-gray-300 light:text-gray-700 mb-1.5">
              <Phone className="inline h-4 w-4 mr-1.5 -mt-0.5" />
              Telefone
            </label>
            <input
              type="text"
              value={formData.phone}
              onChange={(e) => handleChange('phone', formatPhone(e.target.value))}
              className="flex h-10 w-full rounded-md border border-white/10 dark:border-white/10 light:border-gray-300 bg-black/20 dark:bg-black/20 light:bg-gray-50 px-3 py-2 text-sm text-white dark:text-white light:text-gray-900 focus:ring-2 focus:ring-accent focus:border-transparent transition-colors"
              placeholder="(11) 99999-9999"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-300 dark:text-gray-300 light:text-gray-700 mb-1.5">
              <CreditCard className="inline h-4 w-4 mr-1.5 -mt-0.5" />
              CPF/CNPJ
            </label>
            <input
              type="text"
              value={formData.document}
              onChange={(e) => handleChange('document', formatDocument(e.target.value))}
              className="flex h-10 w-full rounded-md border border-white/10 dark:border-white/10 light:border-gray-300 bg-black/20 dark:bg-black/20 light:bg-gray-50 px-3 py-2 text-sm text-white dark:text-white light:text-gray-900 focus:ring-2 focus:ring-accent focus:border-transparent transition-colors"
              placeholder="000.000.000-00"
            />
          </div>

          <div className="pt-4 border-t border-white/10 dark:border-white/10 light:border-gray-200">
            <Button type="submit" className="w-full sm:w-auto">
              <Save className="w-4 h-4 mr-2" />
              {saved ? '✓ Salvo com sucesso!' : 'Salvar Alterações'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
