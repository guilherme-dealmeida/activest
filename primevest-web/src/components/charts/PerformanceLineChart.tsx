import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatCurrency, formatDate } from '@/utils/format';
import { PortfolioPerformance } from '@/types';

interface PerformanceLineChartProps {
  data: PortfolioPerformance;
}

export const PerformanceLineChart = ({ data }: PerformanceLineChartProps) => {
  // Transform data format for Recharts
  const chartData = data.dates.map((date, index) => ({
    date,
    value: data.portfolioValues[index],
    benchmark: data.benchmarkValues ? data.benchmarkValues[index] : null,
  }));

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg">
          <p className="text-gray-300 mb-2">{formatDate(label)}</p>
          <p className="font-medium text-accent">
            Carteira: {formatCurrency(payload[0].value)}
          </p>
          {payload[1] && (
            <p className="font-medium text-gray-400 mt-1">
              Benchmark: {formatCurrency(payload[1].value)}
            </p>
          )}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="w-full h-80">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 20, bottom: 0 }}>
          <defs>
            <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#c6a962" stopOpacity={0.3}/>
              <stop offset="95%" stopColor="#c6a962" stopOpacity={0}/>
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#242b3d" vertical={false} />
          <XAxis 
            dataKey="date" 
            stroke="#4b5563" 
            tickFormatter={(val) => {
              const date = new Date(val);
              return `${date.getMonth() + 1}/${date.getFullYear().toString().substr(-2)}`;
            }}
            tick={{ fill: '#9ca3af', fontSize: 12 }}
            dy={10}
          />
          <YAxis 
            stroke="#4b5563"
            tickFormatter={(val) => `R$ ${(val/1000).toFixed(0)}k`}
            tick={{ fill: '#9ca3af', fontSize: 12 }}
            dx={-10}
          />
          <Tooltip content={<CustomTooltip />} />
          <Area 
            type="monotone" 
            dataKey="value" 
            stroke="#c6a962" 
            strokeWidth={2}
            fillOpacity={1} 
            fill="url(#colorValue)" 
            name="Carteira"
          />
          {chartData[0]?.benchmark !== null && (
            <Area 
              type="monotone" 
              dataKey="benchmark" 
              stroke="#4b5563" 
              strokeWidth={2}
              fill="none" 
              name="Benchmark"
            />
          )}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
};
