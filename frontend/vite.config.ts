/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Um unico projeto Vite com varias entradas (doc/ambiente-de-desenvolvimento.md, 10.3):
// as ilhas sao carregadas pelas paginas Thymeleaf e o painel e uma SPA em /admin/.
// O manifest.json permite ao Spring achar o nome com hash de cada arquivo.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  return {
    plugins: [react(), tailwindcss()],
    build: {
      manifest: true,
      rollupOptions: {
        input: {
          'ilha-treino': 'src/ilhas/treino.tsx',
          'ilha-listagem': 'src/ilhas/listagem.tsx',
          admin: 'admin/index.html',
        },
      },
    },
    server: {
      port: 5173,
      strictPort: true,
      // O painel chama /api pelo proprio Vite, entao o cookie de sessao funciona
      // como se fosse a mesma origem do Spring.
      proxy: {
        '/api': env.VITE_API_URL || 'http://localhost:8080',
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./vitest.setup.ts'],
    },
  };
});
