import { ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Issuer } from '@/types';
import { MONTHS, formatCnpj } from '@/utils/indicators';

/** Link externo só com http(s): o site vem da CVM, digitado pela companhia. */
const siteUrl = (site: string | null): string | null => {
  if (!site) return null;
  const s = site.trim();
  const url = /^https?:\/\//i.test(s) ? s : `https://${s}`;
  try {
    const u = new URL(url);
    return u.protocol === 'http:' || u.protocol === 'https:' ? u.toString() : null;
  } catch {
    return null;
  }
};

const Field = ({ label, children }: { label: string; children: ReactNode }) => (
  <div className="min-w-0">
    <dt className="text-xs uppercase tracking-wide text-gray-500">{label}</dt>
    <dd className="mt-0.5 text-sm text-white break-words">{children}</dd>
  </div>
);

/**
 * Cadastro do emissor (PLANO_TELA_EMISSORES.md, 2.5). Campo nulo não aparece.
 */
export const IssuerRegistry = ({ issuer }: { issuer: Issuer }) => {
  const site = siteUrl(issuer.website);
  const sector = [issuer.b3Sector, issuer.b3Subsector, issuer.b3Segment].filter(Boolean).join(' › ');
  const status = issuer.registrationStatus;

  return (
    <Card title="Cadastro">
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label="CNPJ">{formatCnpj(issuer.cnpj)}</Field>
        {issuer.cvmCode != null && <Field label="Código CVM">{issuer.cvmCode}</Field>}
        {status && (
          <Field label="Situação do registro">
            <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
              status.toUpperCase() === 'ATIVO' ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
            }`}>
              {status}
            </span>
          </Field>
        )}
        {site && (
          <Field label="Site">
            <a href={site} target="_blank" rel="noopener noreferrer"
               className="inline-flex items-center gap-1 text-accent hover:underline break-all">
              {issuer.website}
              <ExternalLink className="h-3.5 w-3.5 shrink-0" />
            </a>
          </Field>
        )}
        {sector && <Field label="Setor B3">{sector}</Field>}
        {issuer.segment && <Field label="Segmento">{issuer.segment}</Field>}
        {issuer.listingSegment && <Field label="Segmento de listagem">{issuer.listingSegment}</Field>}
        {issuer.fiscalYearEndMonth && (
          <Field label="Fim do exercício">{MONTHS[issuer.fiscalYearEndMonth - 1]}</Field>
        )}
        {issuer.administratorName && <Field label="Administrador">{issuer.administratorName}</Field>}
      </dl>
    </Card>
  );
};
