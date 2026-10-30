// Progresso local do visitante no navegador (RF10 RN1-RN7, US03, RNF06).
// Toda leitura e escrita no localStorage fica em try/catch: em aba anonima ou
// com o armazenamento bloqueado, o site funciona normalmente sem lancar erros.

export const CHAVE_PROGRESSO = 'treino-concursos:progresso';
export const CHAVE_MATERIA_CADERNO = 'treino-concursos:caderno-materia';
export const LIMITE_QUESTOES = 20_000;
export const LIMITE_RESUMOS_CADERNO = 200;
const CHAVE_TESTE_STORAGE = 'treino-concursos:__teste_storage__';

export interface RegistroResposta {
  alternativa: string;
  acertou: boolean;
  data: string;
}

export interface DadosProgresso {
  v: 1;
  respostas: Record<string, RegistroResposta>;
  ultimaVista: Record<string, number>;
  ocultarResolvidas: boolean;
}

export interface ResumoProgresso {
  totalResolvidas: number;
  totalAcertos: number;
  totalErros: number;
  percentualAcertos: number;
}

export interface PaginaIdsQuestoes {
  itens: number[];
  proximoCursor: number | null;
}

export type ResultadoContinuar =
  | {
      status: 'encontrada';
      idQuestao: number;
      url: string;
      filtro: string;
    }
  | {
      status: 'fim_do_filtro';
      primeiroId: number;
      primeiraNaoResolvidaDoInicio: number | null;
      urlReiniciar: string;
      urlInicioFiltro: string;
      filtro: string;
    }
  | {
      status: 'vazio';
      filtro: string;
    }
  | {
      // Nenhuma nao resolvida depois de depoisDe (so com voltarAoInicio: false)
      status: 'sem_proxima';
      filtro: string;
    }
  | {
      // Parou em maxPaginas sem achar: o resultado e desconhecido
      status: 'limite_atingido';
      filtro: string;
    };

export interface OpcoesBuscarProxima {
  storage?: Storage | null;
  fetchFn?: typeof fetch;
  depoisDe?: number;
  /** Maximo de paginas de /api/questoes/ids consultadas depois de depoisDe (padrao: sem limite). */
  maxPaginas?: number;
  /** Se nao achar depois de depoisDe, consulta o inicio do filtro (padrao: true, RF10 RN3). */
  voltarAoInicio?: boolean;
}

export interface QuestaoCaderno {
  id: number;
  slug: string;
  slugMateria: string;
  nomeMateria: string;
  nomeAssunto: string | null;
  fonte: string;
  resumoEnunciado: string;
  anulada: boolean;
  desatualizada: boolean;
}

export interface MateriaCaderno {
  slug: string;
  nome: string;
  quantidade: number;
}

export interface OpcoesCaderno {
  storage?: Storage | null;
  fetchFn?: typeof fetch;
}

export interface ProximaQuestaoCaderno {
  questao: QuestaoCaderno;
  url: string;
}

function vazio(): DadosProgresso {
  return { v: 1, respostas: {}, ultimaVista: {}, ocultarResolvidas: false };
}

function obterStorage(storage?: Storage | null): Storage | null {
  if (storage !== undefined) return storage;
  try {
    return typeof globalThis !== 'undefined' ? globalThis.localStorage : null;
  } catch {
    return null;
  }
}

/**
 * Verifica se o localStorage esta disponivel para leitura e escrita (RF10 RN5, RNF06).
 */
export function armazenamentoDisponivel(storage?: Storage | null): boolean {
  try {
    const alvo = obterStorage(storage);
    if (!alvo) return false;
    alvo.getItem(CHAVE_PROGRESSO);
    alvo.setItem(CHAVE_TESTE_STORAGE, '1');
    alvo.removeItem(CHAVE_TESTE_STORAGE);
    return true;
  } catch {
    return false;
  }
}

/**
 * Normaliza o identificador de filtro (ex.: null, "", "/portugues/crase/" -> "portugues/crase").
 */
export function normalizarChaveFiltro(filtro?: string | null): string {
  if (!filtro) return 'todas';
  const limpo = filtro.trim().replace(/^\/+|\/+$/g, '');
  return limpo || 'todas';
}

/**
 * Extrai os parametros de query string para GET /api/questoes/ids a partir da chave do filtro.
 */
export function parametrosFiltroIds(filtro?: string | null): { materia?: string; assunto?: string } {
  const chave = normalizarChaveFiltro(filtro);
  if (chave === 'todas') return {};
  const [materia, assunto] = chave.split('/', 2);
  if (assunto) {
    return { materia, assunto };
  }
  return { materia };
}

/**
 * Monta a URL permanente da questao a partir do id, preservando ?de= quando houver filtro.
 * O servidor redireciona /questao/{id} com 301 para /questao/{id}/{slug}.
 */
export function urlQuestaoPorId(idQuestao: number, filtro?: string | null): string {
  const chave = normalizarChaveFiltro(filtro);
  const base = `/questao/${idQuestao}`;
  return chave === 'todas' ? base : `${base}?de=${chave}`;
}

/**
 * Monta a URL da listagem correspondente ao filtro.
 */
export function urlListagemDoFiltro(filtro?: string | null): string {
  const chave = normalizarChaveFiltro(filtro);
  return chave === 'todas' ? '/questoes' : `/questoes/${chave}`;
}

function registroRespostaValido(valor: unknown): valor is RegistroResposta {
  if (typeof valor !== 'object' || valor === null) return false;
  const r = valor as Record<string, unknown>;
  return (
    typeof r.alternativa === 'string' &&
    r.alternativa.length > 0 &&
    typeof r.acertou === 'boolean' &&
    typeof r.data === 'string'
  );
}

/**
 * Le o progresso salvo no navegador (formato versionado { v: 1, ... }).
 * Nunca lanca erro: se o armazenamento estiver indisponivel ou o JSON invalido, devolve vazio.
 */
export function lerProgresso(storage?: Storage | null): DadosProgresso {
  try {
    const alvo = obterStorage(storage);
    const bruto = alvo?.getItem(CHAVE_PROGRESSO);
    if (!bruto) return vazio();
    const lido = JSON.parse(bruto) as Partial<DadosProgresso>;
    if (
      lido?.v !== 1 ||
      typeof lido.respostas !== 'object' ||
      lido.respostas === null ||
      Array.isArray(lido.respostas)
    ) {
      return vazio();
    }

    const respostas: Record<string, RegistroResposta> = {};
    for (const [id, reg] of Object.entries(lido.respostas)) {
      if (registroRespostaValido(reg)) {
        respostas[id] = {
          alternativa: reg.alternativa,
          acertou: reg.acertou,
          data: reg.data,
        };
      }
    }

    const ultimaVista: Record<string, number> = {};
    if (typeof lido.ultimaVista === 'object' && lido.ultimaVista !== null && !Array.isArray(lido.ultimaVista)) {
      for (const [f, id] of Object.entries(lido.ultimaVista)) {
        if (typeof id === 'number' && Number.isInteger(id) && id > 0) {
          ultimaVista[f] = id;
        }
      }
    }

    return {
      v: 1,
      respostas,
      ultimaVista,
      ocultarResolvidas: lido.ocultarResolvidas === true,
    };
  } catch {
    return vazio();
  }
}

function salvarProgresso(dados: DadosProgresso, storage?: Storage | null): void {
  try {
    const alvo = obterStorage(storage);
    if (!alvo) return;
    alvo.setItem(CHAVE_PROGRESSO, JSON.stringify({ ...dados, v: 1 }));
  } catch {
    // Sem localStorage: o site continua funcionando sem salvar (RF10 RN5, RNF06)
  }
}

function descartarExcedentesMaisAntigos(respostas: Record<string, RegistroResposta>): void {
  const entradas = Object.entries(respostas);
  if (entradas.length <= LIMITE_QUESTOES) return;

  entradas.sort((a, b) => (a[1].data < b[1].data ? -1 : a[1].data > b[1].data ? 1 : 0));
  const quantidadeDescartar = entradas.length - LIMITE_QUESTOES;
  for (let i = 0; i < quantidadeDescartar; i++) {
    delete respostas[entradas[i][0]];
  }
}

/**
 * Registra a resposta de uma questao e a ultima questao vista no filtro (RF10 RN2, RN7).
 */
export function registrarResposta(
  idQuestao: number,
  alternativa: string,
  acertou: boolean,
  filtro: string | null = null,
  storage?: Storage | null,
): void {
  try {
    const alvo = obterStorage(storage);
    if (!alvo || !Number.isInteger(idQuestao) || idQuestao <= 0 || !alternativa) return;
    const atual = lerProgresso(alvo);
    const chave = String(idQuestao);
    delete atual.respostas[chave];
    atual.respostas[chave] = {
      alternativa,
      acertou,
      data: new Date().toISOString(),
    };
    descartarExcedentesMaisAntigos(atual.respostas);

    const chaveFiltro = normalizarChaveFiltro(filtro);
    atual.ultimaVista[chaveFiltro] = idQuestao;
    salvarProgresso(atual, alvo);
  } catch {
    // Sem localStorage: o treino funciona normalmente sem salvar (RF10 RN5, RNF06)
  }
}

/**
 * Consulta a resposta registrada para uma questao, ou null se ainda nao respondida.
 */
export function consultarResposta(idQuestao: number, storage?: Storage | null): RegistroResposta | null {
  const progresso = lerProgresso(storage);
  return progresso.respostas[String(idQuestao)] ?? null;
}

function ehDadosProgresso(fonte: Storage | DadosProgresso | null | undefined): fonte is DadosProgresso {
  return Boolean(fonte && typeof fonte === 'object' && (fonte as DadosProgresso).v === 1 && typeof (fonte as Storage).getItem !== 'function');
}

/**
 * Informa se uma questao ja foi resolvida neste navegador.
 */
export function foiResolvida(idQuestao: number, fonte?: Storage | DadosProgresso | null): boolean {
  const progresso: DadosProgresso = ehDadosProgresso(fonte) ? fonte : lerProgresso(fonte);
  return Boolean(progresso.respostas[String(idQuestao)]);
}

/**
 * Registra a ultima questao vista em um filtro (RF10 RN2).
 */
export function registrarUltimaVista(
  idQuestao: number,
  filtro: string | null = null,
  storage?: Storage | null,
): void {
  try {
    const alvo = obterStorage(storage);
    if (!alvo || !Number.isInteger(idQuestao) || idQuestao <= 0) return;
    const atual = lerProgresso(alvo);
    atual.ultimaVista[normalizarChaveFiltro(filtro)] = idQuestao;
    salvarProgresso(atual, alvo);
  } catch {
    // Ignorado quando localStorage nao esta disponivel
  }
}

/**
 * Consulta o id da ultima questao vista em um filtro, ou null se nao houver.
 */
export function obterUltimaVista(filtro: string | null = null, storage?: Storage | null): number | null {
  const progresso = lerProgresso(storage);
  return progresso.ultimaVista[normalizarChaveFiltro(filtro)] ?? null;
}

/**
 * Remove a referencia de ultima questao vista de um filtro para reiniciar do comeco (RF10 RN3).
 */
export function limparUltimaVista(filtro: string | null = null, storage?: Storage | null): void {
  try {
    const alvo = obterStorage(storage);
    if (!alvo) return;
    const atual = lerProgresso(alvo);
    delete atual.ultimaVista[normalizarChaveFiltro(filtro)];
    salvarProgresso(atual, alvo);
  } catch {
    // Ignorado quando localStorage nao esta disponivel
  }
}

/**
 * Le a preferencia "Ocultar questoes ja resolvidas" (RF10 RN4).
 */
export function lerOcultarResolvidas(storage?: Storage | null): boolean {
  return lerProgresso(storage).ocultarResolvidas;
}

/**
 * Atualiza a preferencia "Ocultar questoes ja resolvidas" (RF10 RN4).
 */
export function definirOcultarResolvidas(ocultar: boolean, storage?: Storage | null): void {
  try {
    const alvo = obterStorage(storage);
    if (!alvo) return;
    const atual = lerProgresso(alvo);
    atual.ocultarResolvidas = Boolean(ocultar);
    salvarProgresso(atual, alvo);
  } catch {
    // Ignorado quando localStorage nao esta disponivel
  }
}

/**
 * Calcula os totais de questoes resolvidas, acertos, erros e percentual (RF10 RN6).
 */
export function calcularResumoProgresso(fonte?: Storage | DadosProgresso | null): ResumoProgresso {
  const progresso: DadosProgresso = ehDadosProgresso(fonte) ? fonte : lerProgresso(fonte);
  const lista: RegistroResposta[] = Object.values(progresso.respostas);
  const totalResolvidas = lista.length;
  const totalAcertos = lista.filter((r) => r.acertou).length;
  const totalErros = totalResolvidas - totalAcertos;
  const percentualAcertos = totalResolvidas > 0 ? Math.round((totalAcertos / totalResolvidas) * 100) : 0;
  return { totalResolvidas, totalAcertos, totalErros, percentualAcertos };
}

/**
 * Apaga todo o progresso salvo neste navegador (RF10 RN6).
 */
export function limparProgresso(storage?: Storage | null): void {
  try {
    const alvo = obterStorage(storage);
    alvo?.removeItem(CHAVE_PROGRESSO);
    alvo?.removeItem(CHAVE_MATERIA_CADERNO);
  } catch {
    // Ignorado quando localStorage nao esta disponivel
  }
}

/**
 * Informa se uma questao esta atualmente no caderno de erros (RF11 RN1, US04):
 * entra sempre que o visitante erra e sai quando acerta ao refazer.
 */
export function estaNoCadernoDeErros(idQuestao: number, fonte?: Storage | DadosProgresso | null): boolean {
  const progresso: DadosProgresso = ehDadosProgresso(fonte) ? fonte : lerProgresso(fonte);
  const registro = progresso.respostas[String(idQuestao)];
  return Boolean(registro && registro.acertou === false);
}

/**
 * Lista os ids das questoes presentes no caderno de erros em ordem crescente de id (RF11 RN1, RN2).
 */
export function listarIdsCadernoDeErros(fonte?: Storage | DadosProgresso | null): number[] {
  const progresso: DadosProgresso = ehDadosProgresso(fonte) ? fonte : lerProgresso(fonte);
  const ids: number[] = [];
  for (const [chave, registro] of Object.entries(progresso.respostas)) {
    if (registro.acertou === false) {
      const id = Number(chave);
      if (Number.isInteger(id) && id > 0) {
        ids.push(id);
      }
    }
  }
  ids.sort((a, b) => a - b);
  return ids;
}

/**
 * Conta quantas questoes estao atualmente no caderno de erros (RF11 RN1).
 */
export function contarCadernoDeErros(fonte?: Storage | DadosProgresso | null): number {
  return listarIdsCadernoDeErros(fonte).length;
}

/**
 * Verifica se o filtro de navegacao corresponde ao caderno de erros (ex.: "caderno" ou "caderno/portugues").
 */
export function ehFiltroCaderno(filtro?: string | null): boolean {
  const chave = normalizarChaveFiltro(filtro);
  return chave === 'caderno' || chave.startsWith('caderno/');
}

/**
 * Extrai o slug da materia se o filtro for do formato "caderno/{materia}", ou null caso contrario.
 */
export function extrairMateriaFiltroCaderno(filtro?: string | null): string | null {
  const chave = normalizarChaveFiltro(filtro);
  if (!chave.startsWith('caderno/')) return null;
  const materia = chave.slice('caderno/'.length).trim().replace(/^\/+|\/+$/g, '');
  return materia && materia !== 'todas' ? materia : null;
}

/**
 * Le a materia selecionada para filtrar o caderno de erros neste navegador (RF11 RN2).
 */
export function lerMateriaCaderno(storage?: Storage | null): string | null {
  try {
    const alvo = obterStorage(storage);
    const lido = alvo?.getItem(CHAVE_MATERIA_CADERNO)?.trim() ?? '';
    return lido && lido !== 'todas' ? lido : null;
  } catch {
    return null;
  }
}

/**
 * Salva ou limpa a materia selecionada para filtrar o caderno de erros neste navegador (RF11 RN2).
 */
export function definirMateriaCaderno(slugMateria: string | null, storage?: Storage | null): void {
  try {
    const alvo = obterStorage(storage);
    if (!alvo) return;
    const limpo = slugMateria?.trim().replace(/^\/+|\/+$/g, '') ?? '';
    if (!limpo || limpo === 'todas') {
      alvo.removeItem(CHAVE_MATERIA_CADERNO);
    } else {
      alvo.setItem(CHAVE_MATERIA_CADERNO, limpo);
    }
  } catch {
    // Ignorado quando localStorage nao esta disponivel
  }
}

/**
 * Monta a URL real da questao para navegacao pelo caderno de erros (/questao/{id}/{slug}?de=caderno) (RF11, US04).
 */
export function urlQuestaoCaderno(idQuestao: number, slug?: string | null): string {
  const slugLimpo = slug?.trim().replace(/^\/+|\/+$/g, '') ?? '';
  const base = slugLimpo ? `/questao/${idQuestao}/${slugLimpo}` : `/questao/${idQuestao}`;
  return `${base}?de=caderno`;
}

/**
 * Filtra as questoes do caderno de erros por materia (ou retorna todas quando slugMateria for nulo/"todas"),
 * mantendo a ordem crescente de id (RF11 RN2).
 */
export function filtrarCadernoPorMateria(
  questoes: readonly QuestaoCaderno[],
  slugMateria?: string | null,
): QuestaoCaderno[] {
  const materia = slugMateria?.trim() ?? '';
  const filtradas =
    !materia || materia === 'todas'
      ? [...questoes]
      : questoes.filter((q) => q.slugMateria === materia);
  filtradas.sort((a, b) => a.id - b.id);
  return filtradas;
}

/**
 * Lista as materias presentes nas questoes do caderno de erros com suas quantidades,
 * ordenadas pelo nome da materia (RF11 RN2).
 */
export function listarMateriasDoCaderno(questoes: readonly QuestaoCaderno[]): MateriaCaderno[] {
  const mapa = new Map<string, MateriaCaderno>();
  for (const q of questoes) {
    if (!q.slugMateria || !q.nomeMateria) continue;
    const existente = mapa.get(q.slugMateria);
    if (existente) {
      existente.quantidade++;
    } else {
      mapa.set(q.slugMateria, { slug: q.slugMateria, nome: q.nomeMateria, quantidade: 1 });
    }
  }
  return [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

/**
 * Escolhe a proxima questao do caderno de erros em sequencia (id crescente maior que idAtual),
 * respeitando o filtro de materia quando ativo (RF11 RN2, US04).
 */
export function escolherProximaDoCaderno(
  questoes: readonly QuestaoCaderno[],
  idAtual: number | null = null,
  slugMateria?: string | null,
): QuestaoCaderno | null {
  const filtradas = filtrarCadernoPorMateria(questoes, slugMateria);
  const minimo = idAtual !== null && idAtual > 0 ? idAtual : 0;
  for (const q of filtradas) {
    if (q.id > minimo) {
      return q;
    }
  }
  return null;
}

function questaoCadernoValida(valor: unknown): valor is QuestaoCaderno {
  if (typeof valor !== 'object' || valor === null) return false;
  const q = valor as Record<string, unknown>;
  return (
    typeof q.id === 'number' &&
    Number.isInteger(q.id) &&
    q.id > 0 &&
    typeof q.slug === 'string' &&
    q.slug.length > 0 &&
    typeof q.slugMateria === 'string' &&
    q.slugMateria.length > 0 &&
    typeof q.nomeMateria === 'string' &&
    q.nomeMateria.length > 0 &&
    (q.nomeAssunto === null || typeof q.nomeAssunto === 'string') &&
    typeof q.fonte === 'string' &&
    typeof q.resumoEnunciado === 'string' &&
    typeof q.anulada === 'boolean' &&
    typeof q.desatualizada === 'boolean'
  );
}

/**
 * Busca no backend (GET /api/questoes/resumos?ids=...) os dados minimos das questoes
 * que estao no caderno de erros deste navegador, em ordem crescente de id (RF11 RN1-RN2, US04).
 */
export async function buscarQuestoesCadernoDeErros(
  opcoes: OpcoesCaderno = {},
): Promise<QuestaoCaderno[]> {
  const ids = listarIdsCadernoDeErros(opcoes.storage);
  if (ids.length === 0) return [];

  const fetchFn = opcoes.fetchFn ?? globalThis.fetch?.bind(globalThis);
  if (typeof fetchFn !== 'function') return [];

  const conjuntoIds = new Set(ids);
  const encontradas = new Map<number, QuestaoCaderno>();

  try {
    for (let inicio = 0; inicio < ids.length; inicio += LIMITE_RESUMOS_CADERNO) {
      const lote = ids.slice(inicio, inicio + LIMITE_RESUMOS_CADERNO);
      const url = `/api/questoes/resumos?ids=${lote.join(',')}`;
      const resposta = await fetchFn(url, { headers: { Accept: 'application/json' } });
      if (!resposta.ok) continue;
      const json: unknown = await resposta.json();
      if (!Array.isArray(json)) continue;
      for (const item of json) {
        if (questaoCadernoValida(item) && conjuntoIds.has(item.id)) {
          encontradas.set(item.id, {
            id: item.id,
            slug: item.slug,
            slugMateria: item.slugMateria,
            nomeMateria: item.nomeMateria,
            nomeAssunto: item.nomeAssunto,
            fonte: item.fonte,
            resumoEnunciado: item.resumoEnunciado,
            anulada: item.anulada,
            desatualizada: item.desatualizada,
          });
        }
      }
    }
  } catch {
    return [];
  }

  return [...encontradas.values()].sort((a, b) => a.id - b.id);
}

/**
 * Busca a proxima questao do caderno de erros apos idAtual (respeitando o filtro de materia
 * ativo no caderno) e devolve a questao e sua URL real (/questao/{id}/{slug}?de=caderno) (RF11 RN2, US04).
 */
export async function buscarProximaQuestaoCaderno(
  idAtual: number,
  opcoes: OpcoesCaderno & { materia?: string | null } = {},
): Promise<ProximaQuestaoCaderno | null> {
  const questoes = await buscarQuestoesCadernoDeErros(opcoes);
  const materia =
    opcoes.materia !== undefined ? opcoes.materia : lerMateriaCaderno(opcoes.storage);
  const proxima = escolherProximaDoCaderno(questoes, idAtual, materia);
  if (!proxima) return null;
  return {
    questao: proxima,
    url: urlQuestaoCaderno(proxima.id, proxima.slug),
  };
}

function idEstaResolvido(
  id: number,
  resolvidas: Record<string, unknown> | ReadonlySet<number>,
): boolean {
  if (resolvidas instanceof Set) {
    return resolvidas.has(id);
  }
  return Boolean((resolvidas as Record<string, unknown>)[String(id)]);
}

/**
 * Escolhe o primeiro id nao resolvido estritamente maior que ultimaVista dentro de uma
 * lista ordenada de ids (RF10 RN3, RN4).
 */
export function escolherProximaNaoResolvida(
  ids: readonly number[],
  resolvidas: Record<string, unknown> | ReadonlySet<number>,
  ultimaVista: number | null = null,
): number | null {
  const minimo = ultimaVista !== null && ultimaVista > 0 ? ultimaVista : 0;
  for (const id of ids) {
    if (id > minimo && !idEstaResolvido(id, resolvidas)) {
      return id;
    }
  }
  return null;
}

async function consultarPaginaIds(
  chaveFiltro: string,
  depoisDe: number,
  fetchFn: typeof fetch,
): Promise<PaginaIdsQuestoes> {
  const params = new URLSearchParams();
  const { materia, assunto } = parametrosFiltroIds(chaveFiltro);
  if (materia) params.set('materia', materia);
  if (assunto) params.set('assunto', assunto);
  if (depoisDe > 0) params.set('depoisDe', String(depoisDe));

  const qs = params.toString();
  const url = qs ? `/api/questoes/ids?${qs}` : '/api/questoes/ids';
  const resposta = await fetchFn(url, { headers: { Accept: 'application/json' } });
  if (!resposta.ok) {
    return { itens: [], proximoCursor: null };
  }
  const json = (await resposta.json()) as Partial<PaginaIdsQuestoes>;
  const itens = Array.isArray(json.itens)
    ? json.itens.filter((n): n is number => typeof n === 'number' && Number.isInteger(n) && n > 0)
    : [];
  const proximoCursor =
    typeof json.proximoCursor === 'number' && Number.isInteger(json.proximoCursor) && json.proximoCursor > 0
      ? json.proximoCursor
      : null;
  return { itens, proximoCursor };
}

/**
 * Busca os ids do filtro em /api/questoes/ids paginando pelo cursor (depoisDe) e escolhe
 * a primeira questao nao resolvida depois da ultima vista (RF10 RN3, RN4, US03).
 *
 * Se nao houver nenhuma questao nao resolvida depois da ultima vista, consulta o inicio
 * do filtro para devolver o estado 'fim_do_filtro' (permitindo voltar ao inicio e reiniciar).
 */
export async function buscarProximaNaoResolvida(
  filtro: string | null = null,
  opcoes: OpcoesBuscarProxima = {},
): Promise<ResultadoContinuar> {
  const chaveFiltro = normalizarChaveFiltro(filtro);
  const progresso = lerProgresso(opcoes.storage);
  const fetchFn = opcoes.fetchFn ?? globalThis.fetch.bind(globalThis);
  const depoisDeInicial =
    opcoes.depoisDe !== undefined && opcoes.depoisDe >= 0
      ? opcoes.depoisDe
      : (progresso.ultimaVista[chaveFiltro] ?? 0);

  let cursor = depoisDeInicial;
  let primeiroIdDoFiltro: number | null = null;
  let paginasLidas = 0;

  for (;;) {
    const pagina = await consultarPaginaIds(chaveFiltro, cursor, fetchFn);
    if (cursor === 0 && primeiroIdDoFiltro === null && pagina.itens.length > 0) {
      primeiroIdDoFiltro = pagina.itens[0];
    }

    const escolhida = escolherProximaNaoResolvida(pagina.itens, progresso.respostas, depoisDeInicial);
    if (escolhida !== null) {
      return {
        status: 'encontrada',
        idQuestao: escolhida,
        url: urlQuestaoPorId(escolhida, chaveFiltro),
        filtro: chaveFiltro,
      };
    }

    if (pagina.proximoCursor === null || pagina.itens.length === 0 || pagina.proximoCursor <= cursor) {
      break;
    }
    paginasLidas++;
    if (opcoes.maxPaginas !== undefined && paginasLidas >= opcoes.maxPaginas) {
      return { status: 'limite_atingido', filtro: chaveFiltro };
    }
    cursor = pagina.proximoCursor;
  }

  if (opcoes.voltarAoInicio === false) {
    return { status: 'sem_proxima', filtro: chaveFiltro };
  }

  // Nao encontrou apos depoisDeInicial: se comecou depois de 0, busca do inicio do filtro (RF10 RN3)
  let primeiraNaoResolvidaDoInicio: number | null = null;
  if (depoisDeInicial > 0) {
    let cursorInicio = 0;
    for (;;) {
      const paginaInicio = await consultarPaginaIds(chaveFiltro, cursorInicio, fetchFn);
      if (cursorInicio === 0 && primeiroIdDoFiltro === null && paginaInicio.itens.length > 0) {
        primeiroIdDoFiltro = paginaInicio.itens[0];
      }
      if (primeiraNaoResolvidaDoInicio === null) {
        primeiraNaoResolvidaDoInicio = escolherProximaNaoResolvida(
          paginaInicio.itens,
          progresso.respostas,
          0,
        );
      }
      if (
        primeiraNaoResolvidaDoInicio !== null ||
        paginaInicio.proximoCursor === null ||
        paginaInicio.itens.length === 0 ||
        paginaInicio.proximoCursor <= cursorInicio ||
        paginaInicio.proximoCursor >= depoisDeInicial
      ) {
        break;
      }
      cursorInicio = paginaInicio.proximoCursor;
    }
  }

  if (primeiroIdDoFiltro === null) {
    return { status: 'vazio', filtro: chaveFiltro };
  }

  const idAlvoReinicio = primeiraNaoResolvidaDoInicio ?? primeiroIdDoFiltro;
  return {
    status: 'fim_do_filtro',
    primeiroId: primeiroIdDoFiltro,
    primeiraNaoResolvidaDoInicio,
    urlReiniciar: urlQuestaoPorId(idAlvoReinicio, chaveFiltro),
    urlInicioFiltro: urlListagemDoFiltro(chaveFiltro),
    filtro: chaveFiltro,
  };
}
