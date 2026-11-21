/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Papéis do tema (src/index.css). As cores que precisam de opacidade
        // (text-content/70, bg-accent/10) usam a forma "rgb(var(--x-rgb) / ...)":
        // sem ela, o tema claro exigia um override por classe gerada.
        bg: 'var(--bg)',
        surface: 'var(--surface)',
        elevated: 'var(--surface-2)',
        raised: 'var(--surface-3)',
        content: 'rgb(var(--content-rgb) / <alpha-value>)',
        'content-2': 'rgb(var(--content-2-rgb) / <alpha-value>)',
        'content-3': 'rgb(var(--content-3-rgb) / <alpha-value>)',
        'content-4': 'rgb(var(--content-4-rgb) / <alpha-value>)',
        // Fios e véus já carregam a própria opacidade: um token por força.
        line: 'var(--line)',
        'line-soft': 'var(--line-soft)',
        'line-strong': 'var(--line-strong)',
        overlay: 'var(--overlay)',
        selected: 'var(--selected)',
        accent: 'rgb(var(--accent-rgb) / <alpha-value>)',
        'accent-text': 'var(--accent-text)',
        'on-accent': 'var(--on-accent)',
        success: 'rgb(var(--up-rgb) / <alpha-value>)',
        danger: 'rgb(var(--down-rgb) / <alpha-value>)',
        info: 'rgb(var(--info-rgb) / <alpha-value>)',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
      borderRadius: {
        // Duas raizes, só: painel e controle. Antes cada tela escolhia a sua
        // (rounded-lg no Dashboard, rounded-xl no Card, rounded-full nas pílulas).
        panel: '0.75rem',
        control: '0.5rem',
      },
      transitionDuration: {
        DEFAULT: '150ms',
      },
    },
  },
  plugins: [],
}
