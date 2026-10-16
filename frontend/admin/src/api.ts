// Cliente da API do painel. A sessao vai no cookie (mesma origem via proxy do
// Vite em dev). Toda requisicao que altera dados leva o token CSRF que o Spring
// grava no cookie XSRF-TOKEN (doc/api.md, secao 9).

export type Perfil = 'EDITOR' | 'ADMIN';

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  ativo: boolean;
  bloqueadoAte: string | null;
  criadoEm: string;
}

export interface Pagina<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

/** Erro da API no formato Problem Details. */
export class ErroApi extends Error {
  constructor(
    readonly status: number,
    mensagem: string,
  ) {
    super(mensagem);
  }
}

const MENSAGENS_PADRAO: Record<number, string> = {
  401: 'Sua sessão expirou. Entre novamente.',
  403: 'Você não tem permissão para esta ação.',
};

const COOKIE_CSRF = 'XSRF-TOKEN';

export function lerTokenCsrf(): string | null {
  const prefixo = `${COOKIE_CSRF}=`;
  const cookie = document.cookie.split('; ').find((c) => c.startsWith(prefixo));
  return cookie ? decodeURIComponent(cookie.slice(prefixo.length)) : null;
}

export async function requisitar<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
  const cabecalhos: Record<string, string> = { Accept: 'application/json' };
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';
  if (metodo !== 'GET') {
    const csrf = lerTokenCsrf();
    if (csrf) cabecalhos['X-XSRF-TOKEN'] = csrf;
  }

  const resposta = await fetch(caminho, {
    method: metodo,
    credentials: 'same-origin',
    headers: cabecalhos,
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });

  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => null);
    const mensagem = problema?.detail ?? MENSAGENS_PADRAO[resposta.status] ?? 'Erro inesperado. Tente novamente.';
    throw new ErroApi(resposta.status, mensagem);
  }
  if (resposta.status === 204) return undefined as T;
  return (await resposta.json()) as T;
}

// ---------- classificacoes (US10) ----------

export type Esfera = 'FEDERAL' | 'ESTADUAL' | 'MUNICIPAL';
export type Nivel = 'FUNDAMENTAL' | 'MEDIO' | 'SUPERIOR';

export interface Materia {
  id: number;
  nome: string;
  slug: string;
  ativa: boolean;
}

export interface Assunto {
  id: number;
  idMateria: number;
  idAssuntoPai: number | null;
  nome: string;
  slug: string;
  ativo: boolean;
  nivel: number;
}

export interface Banca {
  id: number;
  nome: string;
  sigla: string;
  slug: string;
}

export interface Orgao {
  id: number;
  nome: string;
  sigla: string;
  slug: string;
  esfera: Esfera;
  uf: string | null;
}

export interface Prova {
  id: number;
  idBanca: number;
  siglaBanca: string;
  idOrgao: number;
  siglaOrgao: string;
  ano: number;
  cargo: string;
  nivel: Nivel;
  slug: string;
}

/** CRUD padrao de um recurso paginado do painel (doc/api.md, secoes 3 e 8). */
export function recurso<T, D>(caminho: string) {
  return {
    listar: (busca: string, pagina: number, tamanho = 20) =>
      requisitar<Pagina<T>>('GET', `${caminho}?busca=${encodeURIComponent(busca)}&page=${pagina}&size=${tamanho}`),
    buscar: (id: number) => requisitar<T>('GET', `${caminho}/${id}`),
    criar: (dados: D) => requisitar<T>('POST', caminho, dados),
    atualizar: (id: number, dados: D) => requisitar<T>('PUT', `${caminho}/${id}`, dados),
    excluir: (id: number) => requisitar<void>('DELETE', `${caminho}/${id}`),
  };
}

export const materias = recurso<Materia, { nome: string; slug: string; ativa: boolean }>('/api/admin/materias');
export const bancas = recurso<Banca, { nome: string; sigla: string; slug: string }>('/api/admin/bancas');
export const orgaos = recurso<Orgao, { nome: string; sigla: string; slug: string; esfera: Esfera; uf: string | null }>(
  '/api/admin/orgaos',
);
export const provas = recurso<
  Prova,
  { idBanca: number; idOrgao: number; ano: number; cargo: string; nivel: Nivel }
>('/api/admin/provas');

type DadosAssunto = { nome: string; slug: string; idAssuntoPai: number | null; ativo: boolean };

export const assuntos = {
  listar: (idMateria: number) => requisitar<Assunto[]>('GET', `/api/admin/materias/${idMateria}/assuntos`),
  buscar: (idMateria: number, id: number) =>
    requisitar<Assunto>('GET', `/api/admin/materias/${idMateria}/assuntos/${id}`),
  criar: (idMateria: number, dados: DadosAssunto) =>
    requisitar<Assunto>('POST', `/api/admin/materias/${idMateria}/assuntos`, dados),
  atualizar: (idMateria: number, id: number, dados: DadosAssunto) =>
    requisitar<Assunto>('PUT', `/api/admin/materias/${idMateria}/assuntos/${id}`, dados),
  excluir: (idMateria: number, id: number) =>
    requisitar<void>('DELETE', `/api/admin/materias/${idMateria}/assuntos/${id}`),
};

export const api = {
  eu: () => requisitar<Usuario>('GET', '/api/auth/eu'),
  entrar: (email: string, senha: string) => requisitar<Usuario>('POST', '/api/auth/login', { email, senha }),
  sair: () => requisitar<void>('POST', '/api/auth/logout'),

  listarUsuarios: (pagina: number) => requisitar<Pagina<Usuario>>('GET', `/api/admin/usuarios?page=${pagina}`),
  criarUsuario: (dados: { nome: string; email: string; senha: string; perfil: Perfil }) =>
    requisitar<Usuario>('POST', '/api/admin/usuarios', dados),
  atualizarUsuario: (id: number, dados: { nome: string; email: string; perfil: Perfil }) =>
    requisitar<Usuario>('PUT', `/api/admin/usuarios/${id}`, dados),
  inativarUsuario: (id: number) => requisitar<Usuario>('PATCH', `/api/admin/usuarios/${id}/inativar`),
  reativarUsuario: (id: number) => requisitar<Usuario>('PATCH', `/api/admin/usuarios/${id}/reativar`),
  desbloquearUsuario: (id: number) => requisitar<Usuario>('PATCH', `/api/admin/usuarios/${id}/desbloquear`),
  redefinirSenha: (id: number, novaSenha: string) =>
    requisitar<Usuario>('PATCH', `/api/admin/usuarios/${id}/redefinir-senha`, { novaSenha }),
};
