import { Link } from 'react-router-dom';
import { PRIVACY, PROVISIONAL_NOTICE, TERMS, TERMS_VERSION } from '@/content/legal';

/** /termos e /privacidade: públicas, abertas a partir do cadastro e do modal de novo aceite. */
export const LegalPage = ({ kind }: { kind: 'terms' | 'privacy' }) => {
  const doc = kind === 'terms' ? TERMS : PRIVACY;
  return (
    <div className="min-h-screen bg-bg p-4 sm:p-8">
      <div className="mx-auto max-w-3xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-bold text-accent-text">Acti<span className="text-content">vest</span></h1>
          <Link to="/login" className="text-sm text-content-3 hover:text-content">Ir para o login</Link>
        </div>
        <article className="rounded-panel border border-line bg-surface p-6 sm:p-8 space-y-6">
          <header>
            <h2 className="text-2xl font-semibold text-content">{doc.title}</h2>
            <p className="mt-1 text-xs text-content-4">Versão {TERMS_VERSION} · atualizada em {doc.updatedAt}</p>
            <p className="mt-4 rounded-control border border-accent/40 bg-accent/10 p-3 text-sm text-accent-text">{PROVISIONAL_NOTICE}</p>
          </header>
          {doc.sections.map((s) => (
            <section key={s.title} className="space-y-2">
              <h3 className="text-lg font-medium text-content">{s.title}</h3>
              {s.paragraphs.map((p) => <p key={p} className="text-sm leading-relaxed text-content-2">{p}</p>)}
            </section>
          ))}
          <footer className="border-t border-line pt-4 text-sm">
            {kind === 'terms'
              ? <Link to="/privacy" className="text-accent-text hover:underline">Ler a Política de Privacidade</Link>
              : <Link to="/terms" className="text-accent-text hover:underline">Ler os Termos de Uso</Link>}
          </footer>
        </article>
      </div>
    </div>
  );
};
