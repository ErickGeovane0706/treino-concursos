import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

/**
 * Teste de carga na origem sem cache (RNF03, Issue #16).
 *
 * Metas do RNF03 verificadas por este script sobre uma massa de 50.000 questoes:
 * - Sustentacao de 50 requisicoes por segundo na origem (JVM com -Xmx512m);
 * - Zero erros 5xx (http_req_failed == 0);
 * - Tempo de resposta do servidor (p95) <= 300 ms sem cache na pagina da questao
 *   e nas listagens por cursor.
 *
 * Execucao com Docker:
 *   docker run --rm --network host -v "$PWD:/work" -w /work grafana/k6:latest \
 *     run -e BASE_URL=http://127.0.0.1:8080 \
 *         -e RELATORIO_PATH=perf/relatorios/k6-depois.json \
 *         perf/carga-origem.k6.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const RATE = Number(__ENV.RATE || 50);
const DURATION = __ENV.DURATION || '30s';
const RELATORIO_PATH = __ENV.RELATORIO_PATH || '';

const MATERIAS = [
  'portugues',
  'direito-constitucional',
  'direito-administrativo',
  'informatica',
  'raciocinio-logico',
  'matematica-financeira',
  'administracao-geral',
  'contabilidade-geral',
  'direito-penal',
  'arquivologia',
];

const duracaoPaginaQuestao = new Trend('duracao_pagina_questao', true);
const duracaoListagemQuestoes = new Trend('duracao_listagem_questoes', true);
const erros5xx = new Counter('erros_5xx');

export const options = {
  scenarios: {
    carga_origem_50rps: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 30,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    erros_5xx: ['count==0'],
    http_req_duration: ['p(95)<=300'],
    'http_req_duration{cenario:pagina_questao}': ['p(95)<=300'],
    'http_req_duration{cenario:listagem_questoes}': ['p(95)<=300'],
    duracao_pagina_questao: ['p(95)<=300'],
    duracao_listagem_questoes: ['p(95)<=300'],
  },
};

/**
 * Escolhe um id de questao PUBLICADA distribuido nos 50.000 registros.
 * No seed-50k-questoes.sql, multiplos de 10 acima de 1000 sao RASCUNHO (g % 20 == 0)
 * ou ARQUIVADA (g % 20 == 10); todos os demais sao PUBLICADA.
 */
function sortearIdPublicado(iteracao) {
  let id = 1 + ((iteracao * 7919 + __VU * 104729) % 50000);
  if (id > 1000 && id % 10 === 0) {
    id += 1;
  }
  return id;
}

function sortearCursor(iteracao) {
  return (iteracao * 3571 + __VU * 65537) % 49000;
}

export default function () {
  const iter = __ITER;
  const cabecalhos = {
    'Cache-Control': 'no-cache',
    Pragma: 'no-cache',
    'Accept-Encoding': 'gzip',
  };

  // 70% pagina individual da questao (com e sem filtro ?de=), 30% listagens por cursor
  if (iter % 10 < 7) {
    const id = sortearIdPublicado(iter);
    let sufixoFiltro = '';
    if (iter % 3 === 1) {
      sufixoFiltro = '?de=portugues';
    } else if (iter % 3 === 2) {
      sufixoFiltro = '?de=portugues/sintaxe';
    }
    const url = `${BASE_URL}/questao/${id}/questao-${id}${sufixoFiltro}`;
    const res = http.get(url, {
      headers: cabecalhos,
      redirects: 0,
      tags: { cenario: 'pagina_questao', name: 'GET /questao/{id}/{slug}' },
    });

    duracaoPaginaQuestao.add(res.timings.duration);
    if (res.status >= 500) {
      erros5xx.add(1);
    }

    check(res, {
      'pagina da questao respondeu 200': (r) => r.status === 200,
      'pagina da questao contem enunciado': (r) =>
        typeof r.body === 'string' && r.body.includes('data-ilha="treino"'),
    });
  } else {
    const cursor = sortearCursor(iter);
    const tipoListagem = iter % 3;
    let caminho;
    if (tipoListagem === 0) {
      caminho = `/questoes?depoisDe=${cursor}`;
    } else if (tipoListagem === 1) {
      const materia = MATERIAS[iter % MATERIAS.length];
      caminho = `/questoes/${materia}?depoisDe=${cursor}`;
    } else {
      caminho = `/questoes/portugues/sintaxe?depoisDe=${cursor}`;
    }

    const res = http.get(`${BASE_URL}${caminho}`, {
      headers: cabecalhos,
      redirects: 0,
      tags: { cenario: 'listagem_questoes', name: 'GET /questoes/...' },
    });

    duracaoListagemQuestoes.add(res.timings.duration);
    if (res.status >= 500) {
      erros5xx.add(1);
    }

    check(res, {
      'listagem respondeu 200': (r) => r.status === 200,
      'listagem contem ilha de listagem': (r) =>
        typeof r.body === 'string' && r.body.includes('data-ilha="listagem"'),
    });
  }
}

export function handleSummary(data) {
  const saidas = {};
  if (RELATORIO_PATH) {
    saidas[RELATORIO_PATH] = JSON.stringify(data, null, 2);
  }
  return saidas;
}
