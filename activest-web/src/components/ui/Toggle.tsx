/** Opção de um {@link Toggle}. */
export interface ToggleOption<T extends string> {
  value: T;
  label: string;
  /** Hover da opção. */
  title?: string;
}

/**
 * Grupo de botões em que só um fica ligado (Trimestre | Anual, Valor simples | detalhado, 3A | 5A | MAX). Saiu dos
 * Demonstrativos da tela de Ativos (docs/planos/PLANO_TELA_ATIVOS.md, 2.6), para todos terem o mesmo desenho.
 */
export const Toggle = <T extends string>({ options, value, onChange, label, size = 'md' }: {
  options: ToggleOption<T>[];
  value: T;
  onChange: (v: T) => void;
  /** Nome do grupo para leitor de tela. */
  label: string;
  /** sm: dentro de cartão. */
  size?: 'sm' | 'md';
}) => (
  <div className="flex rounded-md bg-white/5 p-0.5" role="group" aria-label={label}>
    {options.map(o => (
      <button key={o.value} type="button" onClick={() => onChange(o.value)} aria-pressed={value === o.value}
              title={o.title}
              className={`rounded font-medium transition-colors ${size === 'sm' ? 'px-1.5 py-0.5 text-[10px]' : 'px-3 py-1 text-xs'} ${
                value === o.value ? 'bg-accent text-black' : 'text-gray-300 hover:text-white'
              }`}>
        {o.label}
      </button>
    ))}
  </div>
);
