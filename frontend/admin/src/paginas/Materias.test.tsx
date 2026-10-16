import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { MateriaForm, MateriasLista } from './Materias';

function respostaJson(status: number, corpo: unknown) {
  return Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }));
}

function renderizar(rota: string) {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <Routes>
        <Route path="/materias" element={<MateriasLista />} />
        <Route path="/materias/:id" element={<MateriaForm />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('telas de matérias', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lista as matérias com link para editar', async () => {
    fetchMock.mockReturnValueOnce(
      respostaJson(200, {
        content: [{ id: 3, nome: 'Português', slug: 'portugues', ativa: true }],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      }),
    );
    renderizar('/materias');

    expect(await screen.findByRole('link', { name: 'Português' })).toHaveAttribute('href', '/materias/3');
  });

  it('mostra o motivo quando a exclusão é recusada (409)', async () => {
    fetchMock
      .mockReturnValueOnce(respostaJson(200, { id: 3, nome: 'Português', slug: 'portugues', ativa: true }))
      .mockReturnValueOnce(
        respostaJson(409, { status: 409, detail: 'A matéria tem questões e não pode ser excluída. Inative-a.' }),
      );
    renderizar('/materias/3');
    expect(await screen.findByDisplayValue('Português')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Inative-a');
    const [url, opcoes] = fetchMock.mock.calls[1];
    expect(url).toBe('/api/admin/materias/3');
    expect(opcoes.method).toBe('DELETE');
  });

  it('envia o slug vazio para o servidor gerar a partir do nome', async () => {
    const criada = { id: 7, nome: 'Informática', slug: 'informatica', ativa: true };
    fetchMock.mockReturnValueOnce(respostaJson(201, criada)).mockReturnValueOnce(respostaJson(200, criada));
    renderizar('/materias/nova');

    await userEvent.type(screen.getByLabelText('Nome'), 'Informática');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    const [, opcoes] = fetchMock.mock.calls[0];
    expect(JSON.parse(opcoes.body)).toEqual({ nome: 'Informática', slug: '', ativa: true });
    // Depois de criar, abre a edicao da materia nova, ja com o slug gerado
    expect(await screen.findByDisplayValue('informatica')).toBeInTheDocument();
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/materias/7');
  });
});
