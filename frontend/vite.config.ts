/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv, normalizePath, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const RAIZ = normalizePath(fileURLToPath(new URL('.', import.meta.url)));
/** Codigo das paginas publicas (ilhas, progresso, consentimento). O painel fica em admin/. */
const PASTA_PUBLICA = `${RAIZ}src/`;

/** O que as ilhas importam do React passa a vir do Preact (mesma API, ~4 KB gzip; RNF03). */
const REACT_PARA_PREACT: Record<string, string> = {
  react: 'preact/compat',
  'react-dom': 'preact/compat',
  'react-dom/client': 'preact/compat/client',
  'react/jsx-runtime': 'preact/jsx-runtime',
  'react/jsx-dev-runtime': 'preact/jsx-dev-runtime',
};

function ehCodigoPublico(id: string | undefined): boolean {
  return !!id && normalizePath(id).startsWith(PASTA_PUBLICA);
}

/**
 * RNF03 (JavaScript publico <= 50 KB gzip): as ilhas usam Preact com preact/compat,
 * e o painel continua no React 19. A troca vale so para imports feitos por arquivos
 * de src/, entao os dois convivem no mesmo build e no mesmo servidor de dev.
 */
function preactNasIlhas(): Plugin {
  return {
    name: 'preact-nas-ilhas',
    enforce: 'pre',
    async resolveId(origem, importador, opcoes) {
      const destino = REACT_PARA_PREACT[origem];
      if (!destino || !ehCodigoPublico(importador)) return null;
      return this.resolve(destino, importador, { ...opcoes, skipSelf: true });
    },
  };
}

// Em dev, /admin/materias/3 (rota do React Router) deve abrir o index.html do
// painel, como o Spring fara em producao. Arquivos (com ponto) passam direto.
function fallbackDoPainel(): Plugin {
  return {
    name: 'fallback-do-painel',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        const caminho = req.url?.split('?')[0] ?? '';
        if (caminho.startsWith('/admin/') && !caminho.split('/').pop()!.includes('.')) {
          req.url = '/admin/index.html';
        }
        next();
      });
    },
  };
}

// Um unico projeto Vite com varias entradas (doc/ambiente-de-desenvolvimento.md, 10.3):
// as ilhas sao carregadas pelas paginas Thymeleaf e o painel e uma SPA em /admin/.
// O manifest.json permite ao Spring achar o nome com hash de cada arquivo.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  return {
    plugins: [
      preactNasIlhas(),
      // Fast Refresh so no painel: o runtime do React nao atualiza componentes Preact
      // (nas ilhas, editar um arquivo recarrega a pagina).
      react({ exclude: [new RegExp(`^${PASTA_PUBLICA.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`), /node_modules/] }),
      tailwindcss(),
      fallbackDoPainel(),
    ],
    build: {
      manifest: true,
      rollupOptions: {
        input: {
          // Aviso de cookies, em todas as paginas publicas (chave = caminho, igual ao manifest)
          consentimento: 'src/consentimento/main.ts',
          'ilha-treino': 'src/ilhas/treino.tsx',
          'ilha-listagem': 'src/ilhas/listagem.tsx',
          'ilha-simulado': 'src/ilhas/simulado.tsx',
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
      testTimeout: 15000,
      maxWorkers: 2,
      projects: [
        {
          // Ilhas testadas com o mesmo runtime de producao: Preact. A Testing Library do
          // React vira a do Preact (mesma API: render, screen, act, waitFor, cleanup).
          extends: true,
          test: {
            name: 'ilhas',
            include: ['src/**/*.test.{ts,tsx}'],
          },
          resolve: {
            alias: [
              { find: /^@testing-library\/react$/, replacement: '@testing-library/preact' },
              { find: /^react$/, replacement: `${RAIZ}src/teste/react-preact.js` },
              { find: /^react-dom$/, replacement: `${RAIZ}src/teste/react-preact.js` },
              { find: /^react-dom\/client$/, replacement: 'preact/compat/client' },
              { find: /^react-dom\/test-utils$/, replacement: 'preact/test-utils' },
              { find: /^react\/jsx-runtime$/, replacement: 'preact/jsx-runtime' },
              { find: /^react\/jsx-dev-runtime$/, replacement: 'preact/jsx-dev-runtime' },
            ],
          },
        },
        {
          extends: true,
          test: { name: 'painel', include: ['admin/**/*.test.{ts,tsx}'] },
        },
      ],
    },
  };
});
