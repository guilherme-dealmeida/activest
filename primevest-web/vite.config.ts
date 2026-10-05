import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// Dentro do container, 'localhost' é o próprio container do front — o proxy
// precisa apontar para o serviço 'api'. Fora do Docker, o padrão continua valendo.
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080';

// Bind mount no Windows não propaga eventos de arquivo para o container Linux;
// sem polling o hot reload não dispara.
const usePolling = process.env.CHOKIDAR_USEPOLLING === 'true';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: true,
    port: 5173,
    watch: {
      usePolling,
    },
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        // X-Forwarded-For com o IP do navegador: os limites por IP da API (cadastro, login,
        // "esqueci a senha") contam a pessoa, e nao o container do front.
        xfwd: true,
      },
    },
  },
});
