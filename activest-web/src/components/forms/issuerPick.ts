import { FixedIncomeIssuerOption, NoteInfo } from '@/types';

/** A empresa escolhida num seletor de emissora ou distribuidora. */
export interface IssuerPick {
  id: number;
  /** "Grupo — razão social" */
  label: string;
  dead?: boolean;
  status?: string | null;
  saleRule?: string | null;
}

export const pickOf = (o: FixedIncomeIssuerOption): IssuerPick =>
  ({ id: o.id, label: o.label, dead: o.dead, status: o.status, saleRule: o.saleRule });

/** "Nubank — Nu Financeira S.A." a partir do nome de exibição e da razão social de um título. */
const labelText = (display: string | null, legal: string | null) =>
  !display ? (legal ?? '') : !legal || legal === display ? display : `${display} — ${legal}`;

export const issuerPickOf = (info: NoteInfo | null | undefined): IssuerPick | null =>
  info?.issuerId ? { id: info.issuerId, label: labelText(info.issuerName, info.issuerLegalName) } : null;

export const distributorPickOf = (info: NoteInfo | null | undefined): IssuerPick | null =>
  info?.distributorId
    ? { id: info.distributorId, label: labelText(info.distributorName, info.distributorLegalName),
        saleRule: info.distributorSaleRule }
    : null;
