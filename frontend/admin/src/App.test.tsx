import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { App } from './App';

function respostaJson(status: number, corpo: unknown) {
  return Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }));
}

const admin = {
  id: 1,
  nome: 'Administrador',
  email: 'admin@exemplo.com',
  perfil: 'ADMIN',
  ativo: true,
  bloqueadoAte: null,
  criadoEm: '2026-10-16T09:00:00',
};

function renderizar(rota = '/') {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App do painel', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mostra o login quando não há sessão', async () => {
    fetchMock.mockReturnValueOnce(respostaJson(401, {}));

    renderizar('/usuarios');

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument();
  });

  it('mostra a mensagem da API quando o login falha', async () => {
    fetchMock
      .mockReturnValueOnce(respostaJson(401, {}))
      .mockReturnValueOnce(respostaJson(423, { detail: 'Conta bloqueada por excesso de tentativas. Tente novamente em 15 minutos.' }));
    renderizar('/usuarios');

    await userEvent.type(await screen.findByLabelText('E-mail'), 'admin@exemplo.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'errada');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Conta bloqueada');
  });

  it('entra no painel e mostra os usuários para o ADMIN', async () => {
    fetchMock
      .mockReturnValueOnce(respostaJson(401, {}))
      .mockReturnValueOnce(respostaJson(200, admin))
      .mockReturnValueOnce(
        respostaJson(200, { content: [admin], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } }),
      );
    renderizar('/usuarios');

    await userEvent.type(await screen.findByLabelText('E-mail'), 'admin@exemplo.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-certa');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Usuários do painel' })).toBeInTheDocument();
    expect(await screen.findByText('admin@exemplo.com')).toBeInTheDocument();
  });
});
