import { Link } from 'react-router-dom';
import { PRIVACY, PROVISIONAL_NOTICE, TERMS, TERMS_VERSION } from '@/content/legal';

/** /termos e /privacidade: públicas, abertas a partir do cadastro e do modal de novo aceite. */
export const LegalPage = ({ kind }: { kind: 'terms' | 'privacy' }) => {
  const doc = kind === 'terms' ? TERMS : PRIVACY;
  return (
    <div className="min-h-screen bg-background p-4 sm:p-8">
      <div className="mx-auto max-w-3xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-bold text-accent">Prime<span className="text-white">Vest</span></h1>
          <Link to="/login" className="text-sm text-gray-400 hover:text-white">Ir para o login</Link>
        </div>
        <article className="rounded-xl border border-white/10 bg-surface p-6 sm:p-8 space-y-6">
          <header>
            <h2 className="text-2xl font-semibold text-white">{doc.title}</h2>
            <p className="mt-1 text-xs text-gray-500">Versão {TERMS_VERSION} · atualizada em {doc.updatedAt}</p>
            <p className="mt-4 rounded-md border border-accent/40 bg-accent/10 p-3 text-sm text-accent">{PROVISIONAL_NOTICE}</p>
          </header>
          {doc.sections.map((s) => (
            <section key={s.title} className="space-y-2">
              <h3 className="text-lg font-medium text-white">{s.title}</h3>
              {s.paragraphs.map((p) => <p key={p} className="text-sm leading-relaxed text-gray-300">{p}</p>)}
            </section>
          ))}
          <footer className="border-t border-white/10 pt-4 text-sm">
            {kind === 'terms'
              ? <Link to="/privacy" className="text-accent hover:underline">Ler a Política de Privacidade</Link>
              : <Link to="/terms" className="text-accent hover:underline">Ler os Termos de Uso</Link>}
          </footer>
        </article>
      </div>
    </div>
  );
};
