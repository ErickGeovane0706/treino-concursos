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
