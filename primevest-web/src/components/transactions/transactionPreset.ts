import { AssetType, PortfolioAssetDetail, TransactionResponse, TransactionType } from '@/types';
import { isFixedIncomeNote } from '@/utils/assetTypeRules';
import type { TransactionPreset } from './TransactionFormModal';

/**
 * O preset dos ícones Comprar e Vender de uma posição da carteira.
 *
 * O título só vai junto na Renda Fixa: lá a linha É uma aplicação, e comprar
 * mais significa aportar nela. No Tesouro cada compra é um título novo — a
 * taxa é a do dia — e a venda é do ativo, com os títulos escolhidos por FIFO.
 *
 * Carteira e Transações usam esta mesma função: os ícones das duas telas
 * abrem o modal no mesmo estado.
 */
export const presetFromPosition = (
  operation: TransactionType, type: AssetType, asset: PortfolioAssetDetail
): TransactionPreset => ({
  operation,
  assetType: type,
  ticker: asset.ticker,
  // Emissora, distribuidora e código do título; no Tesouro, a distribuidora da linha vira a da venda/compra
  noteInfo: asset.noteInfo ?? null,
  note: isFixedIncomeNote(type) && asset.noteId
    ? {
        noteId: asset.noteId,
        issuer: asset.issuer,
        maturityDate: asset.maturityDate,
        rateType: asset.rateType,
        indexer: asset.indexer,
        indexerRate: asset.indexerRate,
        currentValue: asset.currentValue,
        estimatedNet: asset.estimatedNet ?? null,
      }
    : null,
});

/**
 * O preset dos ícones Comprar e Vender de uma linha do extrato cuja posição
 * não está mais na carteira (encerrada). Mesma regra de presetFromPosition,
 * com o título lido da própria transação; sem saldo na curva, o "Resgatar
 * tudo" não aparece — e a API recusa a venda se não houver o que vender.
 */
export const presetFromTransaction = (
  operation: TransactionType, type: AssetType, row: TransactionResponse
): TransactionPreset => ({
  operation,
  assetType: type,
  ticker: row.ticker,
  noteInfo: row.noteInfo ?? null,
  note: isFixedIncomeNote(type) && row.noteId
    ? {
        noteId: row.noteId,
        issuer: row.issuer,
        maturityDate: row.maturityDate,
        rateType: row.rateType,
        indexer: row.indexer,
        indexerRate: row.indexerRate,
        currentValue: null,
      }
    : null,
});
