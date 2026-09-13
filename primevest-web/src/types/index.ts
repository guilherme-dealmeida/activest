export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl: string;
  createdAt?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface AssetType {
  id: number;
  name: string;
  description: string;
  colorHex: string;
}

export interface AssetCategory {
  id: number;
  name: string;
  description: string;
  assetTypeId: number;
  assetTypeName: string;
}

export interface Asset {
  id: number;
  ticker: string;
  name: string;
  typeName: string;
  categoryName: string;
  sector: string;
  currentPrice: number;
  priceUpdatedAt: string;
  maturityDate: string | null;
  indexer: string | null;
  indexerRate: number | null;
  active: boolean;
}

export interface PortfolioSummary {
  id: string;
  name: string;
  totalInvested: number;
  currentValue: number;
  totalDividends: number;
  totalReturn: number;
  returnPercentage: number;
}

export interface PortfolioAssetDetail {
  id: string;
  ticker: string;
  assetName: string;
  typeName: string;
  categoryName: string;
  totalShares: number;
  averagePrice: number;
  totalInvested: number;
  currentValue: number;
  currentPrice: number;
  totalDividends: number;
  valorization: number;
  returnPercentage: number;
  isReservation: boolean;
}

export interface PortfolioAllocation {
  typeName: string;
  colorHex: string;
  value: number;
  percentage: number;
}

export interface CategoryAllocation {
  categoryName: string;
  value: number;
  percentage: number;
}

export interface PortfolioAllocationDetailed {
  typeName: string;
  colorHex: string;
  totalValue: number;
  percentage: number;
  categories: CategoryAllocation[];
}

export interface PortfolioPerformance {
  totalInvested: number;
  currentValue: number;
  totalDividends: number;
  totalReturn: number;
  returnPercentage: number;
  assets: { ticker: string; invested: number; currentValue: number; returnPct: number }[];
}

export enum TransactionType {
  BUY = 'BUY',
  SELL = 'SELL'
}

export interface TransactionResponse {
  id: string;
  ticker: string;
  assetName: string;
  assetTypeName: string;
  type: string;
  shares: number;
  pricePerShare: number;
  totalAmount: number;
  brokerageFee: number;
  transactionDate: string;
  createdAt: string;
}

export interface TransactionCreate {
  ticker: string;
  type: string;
  shares: number;
  pricePerShare: number;
  brokerageFee?: number;
  transactionDate: string;
  isReservation?: boolean;
}

export interface Strategy {
  id: string;
  assetTypeId: number;
  assetTypeName: string;
  targetPercentage: number;
  includeReservation: boolean;
}

export interface StrategyUpdate {
  items: { assetTypeId: number; targetPercentage: number; includeReservation: boolean }[];
}

export interface RebalanceItem {
  typeName: string;
  idealPercentage: number;
  currentPercentage: number;
  diffPercentage: number;
  currentValue: number;
  idealValue: number;
  amountToRebalance: number;
}

export interface Rebalance {
  totalPortfolioValue: number;
  items: RebalanceItem[];
}

export interface CeilingPrice {
  id: string;
  ticker: string;
  assetName: string;
  maxPrice: number;
  currentPrice: number;
  diffPercentage: number;
  alertEnabled: boolean;
  belowCeiling: boolean;
}

export interface CeilingPriceCreate {
  ticker: string;
  maxPrice: number;
  alertEnabled?: boolean;
}

export enum DividendType {
  DIVIDENDO = 'DIVIDENDO',
  JCP = 'JCP',
  RENDIMENTO = 'RENDIMENTO'
}

export interface DividendResponse {
  id: number;
  ticker: string;
  assetName: string;
  amountPerShare: number;
  totalAmount: number;
  exDate: string;
  paymentDate: string;
  type: string;
}

export interface DividendCreate {
  ticker: string;
  amountPerShare: number;
  exDate: string;
  paymentDate?: string;
  type: string;
}

export interface DividendSummary {
  totalDividends: number;
  byType: Record<string, number>;
  recent: DividendResponse[];
}

export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface GoalResponse {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  progressPercentage: number;
  targetDate: string;
  status: GoalStatus;
  createdAt: string;
}

export interface GoalCreate {
  name: string;
  targetAmount: number;
  targetDate?: string;
}

export interface GoalUpdate {
  name?: string;
  targetAmount?: number;
  currentAmount?: number;
  targetDate?: string;
  status?: GoalStatus;
}

export interface PriceHistory {
  date: string;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
}
