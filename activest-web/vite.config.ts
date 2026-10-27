import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// Dentro do container, 'localhost' é o próprio container do front — o proxy
// precisa apontar para o serviço 'api'. Fora do Docker, o padrão continua valendo.
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080';
// Tarefas (docs/planos/PLANO_TAREFAS.md, D9): a API da tela de Tarefas mora na adm-api.
const admApiTarget = process.env.VITE_ADM_API_TARGET || 'http://localhost:8081';

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
      // antes do '/api': o Vite usa a primeira regra que casa
      '/api/v1/tasks': {
        target: admApiTarget,
        changeOrigin: true,
        xfwd: true,
      },
      // Tela de administrador Moedas (docs/planos/PLANO_CADASTRO_MOEDAS.md, D13): o cadastro e catalogo, na adm-api
      '/api/v1/admin/currencies': {
        target: admApiTarget,
        changeOrigin: true,
        xfwd: true,
      },
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
