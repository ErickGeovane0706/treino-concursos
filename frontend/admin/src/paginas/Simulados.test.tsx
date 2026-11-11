import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { SimuladoFormRota, SimuladosLista } from './Simulados';

const pagina = (content: unknown[]) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
});

const questao = (id: number, extra: Record<string, unknown> = {}) => ({
  ordem: id,
  id,
  trecho: `Enunciado ${id}`,
  nomeMateria: 'Português',
  status: 'PUBLICADA',
  anulada: false,
  desatualizada: false,
  elegivel: true,
  ...extra,
});

const SIMULADO = {
  id: 7,
  titulo: 'Simulado de Português nº 1',
  slug: 'simulado-de-portugues-n-1',
  descricao: null,
  tempoSugeridoMin: 30,
  status: 'PUBLICADO',
  precisaRevisao: true,
  nomeAutor: 'Ana',
  criadoEm: '2026-11-01T10:00:00',
  publicadoEm: '2026-11-01T11:00:00',
  questoes: [questao(1), questao(2, { anulada: true, elegivel: false }), questao(3)],
};

function json(status: number, corpo: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }),
  );
}

describe('Simulados no painel (US11, RF14)', () => {
  let rotas: Record<string, (opcoes: RequestInit) => Promise<Response>>;

  beforeEach(() => {
    rotas = {
      'GET /api/admin/simulados': () =>
        json(200, pagina([{ id: 7, titulo: SIMULADO.titulo, slug: SIMULADO.slug, status: 'PUBLICADO', precisaRevisao: true, totalQuestoes: 3, publicadoEm: null }])),
      'GET /api/admin/simulados/7': () => json(200, SIMULADO),
      'GET /api/admin/materias': () => json(200, pagina([{ id: 1, nome: 'Português', slug: 'portugues', ativa: true }])),
      'GET /api/admin/questoes': () =>
        json(200, pagina([
          { id: 3, trecho: 'Enunciado 3', nomeMateria: 'Português', tipo: 'MULTIPLA_ESCOLHA', status: 'PUBLICADA', anulada: false, desatualizada: false, atualizadoEm: '' },
          { id: 9, trecho: 'Enunciado 9', nomeMateria: 'Português', tipo: 'MULTIPLA_ESCOLHA', status: 'PUBLICADA', anulada: false, desatualizada: false, atualizadoEm: '' },
          { id: 10, trecho: 'Enunciado 10', nomeMateria: 'Português', tipo: 'MULTIPLA_ESCOLHA', status: 'PUBLICADA', anulada: false, desatualizada: true, atualizadoEm: '' },
        ])),
    };
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opcoes: RequestInit) => {
        const chave = `${opcoes.method} ${url.split('?')[0]}`;
        return (rotas[chave] ?? (() => json(200, pagina([]))))(opcoes);
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function abrir(caminho: string) {
    render(
      <MemoryRouter initialEntries={[caminho]}>
        <Routes>
          <Route path="/simulados" element={<SimuladosLista />} />
          <Route path="/simulados/:id" element={<SimuladoFormRota />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it('destaca na lista o simulado que precisa de revisão (RN4)', async () => {
    abrir('/simulados');
    expect(await screen.findByText('Simulado de Português nº 1')).toBeInTheDocument();
    expect(screen.getByText('Precisa de revisão')).toBeInTheDocument();
  });

  it('mostra a questão inelegível, reordena, troca e salva as questões na nova ordem (RN1, RN4)', async () => {
    const user = userEvent.setup();
    let enviado: { idsQuestoes: number[] } | null = null;
    rotas['PUT /api/admin/simulados/7'] = (opcoes) => {
      enviado = JSON.parse(String(opcoes.body));
      return json(200, {
        ...SIMULADO,
        precisaRevisao: false,
        questoes: [questao(3, { ordem: 1 }), questao(1, { ordem: 2 }), questao(9, { ordem: 3 })],
      });
    };

    abrir('/simulados/7');

    expect(await screen.findByText(/Precisa de revisão: alguma questão/)).toBeInTheDocument();
    const lista = screen.getByRole('list', { name: 'Questões do simulado' });
    expect(within(lista).getByText('anulada')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Remover questão 2' }));
    await user.click(screen.getByRole('button', { name: 'Subir questão 3' }));

    // Na busca: a 3 ja esta no simulado e a 10 esta desatualizada
    expect(await screen.findByRole('button', { name: 'Adicionar questão 9' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Adicionar questão 3' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Adicionar questão 10' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Adicionar questão 9' }));

    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(enviado).not.toBeNull());
    expect(enviado!.idsQuestoes).toEqual([3, 1, 9]);
    expect(await screen.findByText('Simulado salvo.')).toBeInTheDocument();
    expect(screen.queryByText(/Precisa de revisão: alguma questão/)).toBeNull();
  });

  it('não deixa mudar o slug depois de publicado e mostra o erro do servidor', async () => {
    const user = userEvent.setup();
    rotas['PATCH /api/admin/simulados/7/arquivar'] = () =>
      json(409, { detail: 'O simulado já está arquivado.' });

    abrir('/simulados/7');

    expect(await screen.findByLabelText(/^Endereço \(slug\)/)).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Excluir' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Arquivar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('O simulado já está arquivado.');
  });
});
