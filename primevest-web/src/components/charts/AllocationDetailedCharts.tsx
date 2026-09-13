import React from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { PortfolioAllocationDetailed } from '@/types';
import { Card } from '../ui/Card';

interface AllocationDetailedChartsProps {
  data: PortfolioAllocationDetailed[];
}

const generateColors = (baseColor: string, count: number) => {
  const colors = [baseColor];
  const shades = ['#c6a962', '#38a169', '#3182ce', '#805ad5', '#dd6b20', '#e53e3e'];
  for (let i = 1; i < count; i++) {
    colors.push(shades[i % shades.length]);
  }
  return colors;
};

const CustomTooltip = ({ active, payload }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg z-50">
        <p className="font-medium text-white mb-1">{data.name}</p>
        <p className="text-sm text-gray-300">
          Valor: <span className="text-white font-medium">{formatCurrency(data.value)}</span>
        </p>
        <p className="text-sm text-gray-300">
          Participação: <span className="text-white font-medium">{formatPercentage(data.percentage * 100)}</span>
        </p>
      </div>
    );
  }
  return null;
};

export const AllocationDetailedCharts = ({ data }: AllocationDetailedChartsProps) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      {data.map((typeItem, typeIdx) => {
        if (typeItem.totalValue <= 0) return null;
        
        const chartData = typeItem.categories.map((cat, idx) => ({
          name: cat.categoryName,
          value: cat.value,
          percentage: cat.percentage,
          color: generateColors(typeItem.colorHex, typeItem.categories.length)[idx]
        }));

        return (
          <Card key={typeIdx} title={typeItem.typeName} className="flex flex-col items-center h-80">
             <div className="w-full h-48 relative">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={chartData}
                    cx="50%"
                    cy="50%"
                    innerRadius="40%"
                    outerRadius="80%"
                    paddingAngle={2}
                    dataKey="value"
                    stroke="none"
                  >
                    {chartData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomTooltip />} />
                </PieChart>
              </ResponsiveContainer>
             </div>
             <div className="w-full mt-2 overflow-y-auto flex-1">
               <ul className="text-xs space-y-1">
                 {chartData.map((item, idx) => (
                   <li key={idx} className="flex items-center justify-between">
                     <div className="flex items-center gap-1.5 truncate">
                       <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: item.color }} />
                       <span className="text-gray-300 truncate" title={item.name}>{item.name}</span>
                     </div>
                     <span className="font-medium">{formatPercentage(item.percentage * 100)}</span>
                   </li>
                 ))}
               </ul>
             </div>
          </Card>
        );
      })}
    </div>
  );
};
