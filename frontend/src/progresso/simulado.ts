// Andamento e resultado do simulado no navegador (RF13 RN1-RN5, US05).
// Uma chave por simulado no localStorage; toda leitura e escrita fica em try/catch,
// e sem localStorage o simulado continua funcionando, so que sem guardar (RNF06).

export const PREFIXO_SIMULADO = 'treino-concursos:simulado:';

export interface EstadoSimulado {
  /** Letra marcada por id de questao (o id nao muda se o editor reordenar). */
  respostas: Record<string, string>;
  /** Quando a primeira questao foi aberta, em ms (base do cronometro, RF13 RN5). */
  iniciadoEm: number | null;
  /** Preenchido ao abrir o resultado; depois disso as respostas ficam fixas (RF13 RN2). */
  finalizadoEm: number | null;
}

export interface ItemGabarito {
  id: number;
  materia: string;
  correta: string;
}

export type Situacao = 'certa' | 'errada' | 'branco';

export interface ItemResultado extends ItemGabarito {
  resposta: string | null;
  situacao: Situacao;
}

export interface ResultadoSimulado {
  total: number;
  acertos: number;
  percentual: number;
  itens: ItemResultado[];
  porMateria: { materia: string; acertos: number; total: number }[];
}

const LETRA = /^[A-E]$/;

function vazio(): EstadoSimulado {
  return { respostas: {}, iniciadoEm: null, finalizadoEm: null };
}

function obterStorage(storage?: Storage | null): Storage | null {
  if (storage !== undefined) return storage;
  try {
    return typeof globalThis !== 'undefined' ? globalThis.localStorage : null;
  } catch {
    return null;
  }
}

function chave(slug: string): string {
  return PREFIXO_SIMULADO + slug;
}

function numeroOuNull(valor: unknown): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

/** Le o estado salvo; dado corrompido ou ausente vira estado vazio. */
export function lerSimulado(slug: string, storage?: Storage | null): EstadoSimulado {
  try {
    const bruto = obterStorage(storage)?.getItem(chave(slug));
    if (!bruto) return vazio();
    const dados = JSON.parse(bruto) as Partial<EstadoSimulado> | null;
    if (!dados || typeof dados !== 'object') return vazio();
    const respostas: Record<string, string> = {};
    if (dados.respostas && typeof dados.respostas === 'object') {
      for (const [id, letra] of Object.entries(dados.respostas)) {
        if (/^\d+$/.test(id) && typeof letra === 'string' && LETRA.test(letra)) respostas[id] = letra;
      }
    }
    return {
      respostas,
      iniciadoEm: numeroOuNull(dados.iniciadoEm),
      finalizadoEm: numeroOuNull(dados.finalizadoEm),
    };
  } catch {
    return vazio();
  }
}

function salvar(slug: string, estado: EstadoSimulado, storage?: Storage | null): void {
  try {
    obterStorage(storage)?.setItem(chave(slug), JSON.stringify(estado));
  } catch {
    // Sem localStorage: o simulado funciona, mas nao guarda o andamento (RNF06)
  }
}

/** Comeca a contar o tempo, se ainda nao comecou (RF13 RN5). */
export function iniciarSimulado(slug: string, agora = Date.now(), storage?: Storage | null): EstadoSimulado {
  const estado = lerSimulado(slug, storage);
  if (estado.iniciadoEm !== null || estado.finalizadoEm !== null) return estado;
  const novo = { ...estado, iniciadoEm: agora };
  salvar(slug, novo, storage);
  return novo;
}

/**
 * RF13 RN2: marca ou troca a resposta ate finalizar; {@code letra = null} deixa em branco.
 * Depois de finalizado, nada muda.
 */
export function marcarResposta(
  slug: string,
  idQuestao: number,
  letra: string | null,
  agora = Date.now(),
  storage?: Storage | null,
): EstadoSimulado {
  const estado = lerSimulado(slug, storage);
  if (estado.finalizadoEm !== null) return estado;
  const respostas = { ...estado.respostas };
  if (letra === null) delete respostas[String(idQuestao)];
  else if (LETRA.test(letra)) respostas[String(idQuestao)] = letra;
  const novo = { respostas, iniciadoEm: estado.iniciadoEm ?? agora, finalizadoEm: null };
  salvar(slug, novo, storage);
  return novo;
}

/** US05: o resultado fica salvo no navegador. */
export function finalizarSimulado(slug: string, agora = Date.now(), storage?: Storage | null): EstadoSimulado {
  const estado = lerSimulado(slug, storage);
  if (estado.finalizadoEm !== null) return estado;
  const novo = { ...estado, finalizadoEm: agora };
  salvar(slug, novo, storage);
  return novo;
}

/** Apaga o andamento para refazer o simulado do zero. */
export function reiniciarSimulado(slug: string, storage?: Storage | null): void {
  try {
    obterStorage(storage)?.removeItem(chave(slug));
  } catch {
    // Ignorado quando localStorage nao esta disponivel
  }
}

/** RF13 RN3-RN4: percentual, acertos e desempenho por materia; em branco conta como erro. */
export function calcularResultado(gabarito: ItemGabarito[], respostas: Record<string, string>): ResultadoSimulado {
  const porMateria = new Map<string, { acertos: number; total: number }>();
  const itens = gabarito.map((item): ItemResultado => {
    const resposta = respostas[String(item.id)] ?? null;
    const situacao: Situacao = resposta === null ? 'branco' : resposta === item.correta ? 'certa' : 'errada';
    const materia = porMateria.get(item.materia) ?? { acertos: 0, total: 0 };
    materia.total += 1;
    if (situacao === 'certa') materia.acertos += 1;
    porMateria.set(item.materia, materia);
    return { ...item, resposta, situacao };
  });
  const acertos = itens.filter((i) => i.situacao === 'certa').length;
  return {
    total: itens.length,
    acertos,
    percentual: itens.length === 0 ? 0 : Math.round((acertos / itens.length) * 100),
    itens,
    porMateria: [...porMateria.entries()].map(([materia, v]) => ({ materia, ...v })),
  };
}

/** Primeira questao sem resposta, em numeracao de 1 a N; null se todas tem resposta. */
export function primeiraSemResposta(ids: number[], respostas: Record<string, string>): number | null {
  const indice = ids.findIndex((id) => respostas[String(id)] === undefined);
  return indice === -1 ? null : indice + 1;
}

/** RF13 RN5: tempo que falta, em ms (negativo quando acabou). */
export function tempoRestante(iniciadoEm: number, minutos: number, agora = Date.now()): number {
  return iniciadoEm + minutos * 60_000 - agora;
}

/** "mm:ss" ou "h:mm:ss"; tempo esgotado aparece como 00:00. */
export function formatarTempo(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const dois = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${dois(m)}:${dois(s)}` : `${dois(m)}:${dois(s)}`;
}
