import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErroApi, lerTokenCsrf, requisitar } from './api';

describe('cliente da API do painel', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=abc%2D123; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('lê o token CSRF do cookie', () => {
    expect(lerTokenCsrf()).toBe('abc-123');
  });

  it('envia o cabeçalho X-XSRF-TOKEN em requisições que alteram dados', async () => {
    await requisitar('POST', '/api/auth/login', { email: 'a@b.com', senha: 'x' });

    const [, opcoes] = fetchMock.mock.calls[0];
    expect(opcoes.headers['X-XSRF-TOKEN']).toBe('abc-123');
    expect(opcoes.credentials).toBe('same-origin');
  });

  it('não envia o token em GET', async () => {
    await requisitar('GET', '/api/auth/eu');

    const [, opcoes] = fetchMock.mock.calls[0];
    expect(opcoes.headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('transforma Problem Details em ErroApi com a mensagem da API', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 409, detail: 'O último administrador ativo não pode ser inativado.' }), {
        status: 409,
      }),
    );

    await expect(requisitar('PATCH', '/api/admin/usuarios/1/inativar')).rejects.toEqual(
      new ErroApi(409, 'O último administrador ativo não pode ser inativado.'),
    );
  });
});
