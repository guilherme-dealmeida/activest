import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { goalsApi } from '@/api/goals';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Badge } from '@/components/ui/Badge';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { GoalResponse, GoalCreate } from '@/types';
import { Plus, Target, Trash2 } from 'lucide-react';

export const GoalsPage = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState<GoalCreate>({
    name: '',
    targetAmount: 0
  });

  const { data: goals, isLoading } = useQuery<GoalResponse[]>({
    queryKey: ['goals'],
    queryFn: goalsApi.list as any
  });

  const createMutation = useMutation({
    mutationFn: goalsApi.create as any,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      setIsModalOpen(false);
      setFormData({ name: '', targetAmount: 0 });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: goalsApi.delete as any,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['goals'] })
  });

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(formData as any);
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-xl font-semibold text-white flex items-center gap-2">
            <Target className="text-accent" /> Minhas Metas
          </h2>
          <p className="text-gray-400 text-sm mt-1">Acompanhe seu progresso rumo à liberdade financeira.</p>
        </div>
        <Button onClick={() => setIsModalOpen(true)}>
          <Plus className="w-4 h-4 mr-2" />
          Nova Meta
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
        {goals?.map((goal) => {
          const progress = Math.min(goal.progressPercentage, 100);
          const isCompleted = goal.status === 'COMPLETED';

          return (
            <Card key={goal.id} className="relative group">
              <button 
                onClick={() => { if(window.confirm('Excluir meta?')) deleteMutation.mutate(goal.id as any); }}
                className="absolute top-4 right-4 text-gray-500 hover:text-danger opacity-0 group-hover:opacity-100 transition-opacity"
              >
                <Trash2 className="w-4 h-4" />
              </button>
              
              <div className="mb-4 pr-6">
                <h3 className="text-lg font-medium text-white">{goal.name}</h3>
                {isCompleted && <Badge variant="success" className="mt-1">Concluída</Badge>}
              </div>

              <div className="space-y-2 mb-4">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-400">Progresso</span>
                  <span className="font-medium text-white">{formatPercentage(progress)}</span>
                </div>
                <div className="w-full bg-surface-light rounded-full h-2.5">
                  <div 
                    className="bg-accent h-2.5 rounded-full transition-all duration-500" 
                    style={{ width: `${progress}%` }}
                  ></div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-surface-light">
                <div>
                  <p className="text-xs text-gray-500">Atual</p>
                  <p className="font-semibold text-white">{formatCurrency(goal.currentAmount)}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-gray-500">Alvo</p>
                  <p className="font-semibold text-white">{formatCurrency(goal.targetAmount)}</p>
                </div>
              </div>
            </Card>
          );
        })}
        {(!goals || goals.length === 0) && (
          <div className="col-span-full py-12 text-center text-gray-400 border border-dashed border-surface-light rounded-xl">
            Nenhuma meta cadastrada. Defina seus objetivos!
          </div>
        )}
      </div>

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Nova Meta">
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input 
            label="Título da Meta" 
            value={formData.name}
            onChange={(e) => setFormData({...formData, name: e.target.value})}
            placeholder="Ex: Reserva de Emergência, 100k, Independência..."
            required
          />
          <Input 
            label="Valor Alvo (R$)" 
            type="number" 
            step="any"
            value={formData.targetAmount || ''}
            onChange={(e) => setFormData({...formData, targetAmount: Number(e.target.value)})}
            required
          />
          <Input 
            label="Prazo (Opcional)" 
            type="date" 
            value={formData.targetDate || ''}
            onChange={(e) => setFormData({...formData, targetDate: e.target.value})}
          />
          <div className="pt-4 flex justify-end gap-3 border-t border-surface-light">
            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>Cancelar</Button>
            <Button type="submit" isLoading={createMutation.isPending}>Salvar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
