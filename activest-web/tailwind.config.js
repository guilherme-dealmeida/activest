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
        primary: "#0d2818",
        accent: "#c9a84c",
        success: "#38a169",
        danger: "#e53e3e",
        background: {
          DEFAULT: "#000000",
          light: "#f5f5f0",
        },
        surface: {
          DEFAULT: "#111111",
          light: "#1c1c1c",
        },
        "surface-light": {
          DEFAULT: "#2a2a2a",
          light: "#e8e8e0",
        },
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
