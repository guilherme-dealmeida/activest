import React from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts';
import { formatCurrency, formatPercentage } from '@/utils/format';

interface AllocationData {
  name: string;
  value: number;
  percentage: number;
  color: string;
}

interface AllocationPieChartProps {
  data: AllocationData[];
  totalValue: number;
  title?: string;
}

const CustomTooltip = ({ active, payload }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg">
        <p className="font-medium text-white mb-1">{data.name}</p>
        <p className="text-sm text-gray-300">
          Valor: <span className="text-white font-medium">{formatCurrency(data.value)}</span>
        </p>
        <p className="text-sm text-gray-300">
          Participação: <span className="text-white font-medium">{formatPercentage(data.percentage)}</span>
        </p>
      </div>
    );
  }
  return null;
};

export const AllocationPieChart = ({ data, totalValue, title }: AllocationPieChartProps) => {
  return (
    <div className="w-full h-full min-h-[300px] flex flex-col items-center">
      {title && <h3 className="text-lg font-medium text-white mb-4 text-center">{title}</h3>}
      <div className="flex-1 w-full relative">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius="60%"
              outerRadius="80%"
              paddingAngle={2}
              dataKey="value"
              stroke="none"
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip content={<CustomTooltip />} />
            <Legend 
              verticalAlign="bottom" 
              height={36} 
              formatter={(value, entry: any) => (
                <span className="text-gray-300 ml-1">{value} ({formatPercentage(entry.payload.percentage)})</span>
              )}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none pb-8">
          <span className="text-sm text-gray-400">Total</span>
          <span className="text-lg font-semibold text-white">{formatCurrency(totalValue)}</span>
        </div>
      </div>
    </div>
  );
};
