import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QuestaoFormRota, QuestoesLista } from './Questoes';

const VAZIA = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };
const pagina = (content: unknown[]) => ({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } });

const QUESTAO = {
  id: 12,
  idMateria: 1,
  nomeMateria: 'Português',
  idAssunto: null,
  nomeAssunto: null,
  idProva: null,
  descricaoProva: null,
  nomeAutor: 'Ana',
  tipo: 'MULTIPLA_ESCOLHA',
  origem: 'AUTORAL',
  enunciado: 'Assinale.',
  comentario: null,
  slug: 'assinale',
  status: 'RASCUNHO',
  anulada: false,
  desatualizada: false,
  totalRespostas: 0,
  totalAcertos: 0,
  criadoEm: '2026-10-19T10:00:00',
  atualizadoEm: '2026-10-19T10:00:00',
  publicadoEm: null,
  alternativas: ['A', 'B', 'C', 'D', 'E'].map((letra, i) => ({
    id: i + 1,
    letra,
    texto: `texto ${letra}`,
    correta: letra === 'A',
    totalMarcacoes: 0,
  })),
};

function json(status: number, corpo: unknown) {
  return Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }));
}

function renderizar(rota: string) {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <Routes>
        <Route path="/questoes" element={<QuestoesLista />} />
        <Route path="/questoes/:id" element={<QuestaoFormRota />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('telas de questões', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  // Respostas por "METODO caminho"; o que nao estiver aqui devolve lista vazia
  let rotas: Record<string, () => Promise<Response>>;

  beforeEach(() => {
    rotas = {
      'GET /api/admin/materias': () => json(200, pagina([{ id: 1, nome: 'Português', slug: 'portugues', ativa: true }])),
      'GET /api/admin/materias/1/assuntos': () => json(200, []),
      'POST /api/admin/previa-markdown': () => json(200, { html: '<p>prévia</p>' }),
    };
    fetchMock = vi.fn((url: string, opcoes: RequestInit) => {
      const chave = `${opcoes.method} ${url.split('?')[0]}`;
      return (rotas[chave] ?? (() => json(200, VAZIA)))();
    });
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const chamadas = (metodo: string, caminho: string) =>
    fetchMock.mock.calls.filter(([url, o]) => o.method === metodo && url.split('?')[0] === caminho);

  it('filtra a lista por status', async () => {
    renderizar('/questoes');
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'PUBLICADA');

    await waitFor(() =>
      expect(chamadas('GET', '/api/admin/questoes').some(([url]) => url.includes('status=PUBLICADA'))).toBe(true),
    );
  });

  it('cadastra com Alt+número marcando a correta', async () => {
    rotas['POST /api/admin/questoes'] = () => json(201, { ...QUESTAO, id: 30 });
    rotas['GET /api/admin/questoes/30'] = () => json(200, { ...QUESTAO, id: 30 });
    renderizar('/questoes/nova');

    await screen.findByRole('option', { name: 'Português' });
    await userEvent.selectOptions(screen.getByLabelText('Matéria'), '1');
    await userEvent.click(screen.getByLabelText('Autoral'));
    await userEvent.type(screen.getByLabelText('Enunciado (Markdown)'), 'Assinale.');
    for (const letra of ['A', 'B', 'C', 'D', 'E']) {
      await userEvent.type(screen.getByLabelText(`Texto da alternativa ${letra}`), `texto ${letra}`);
    }
    fireEvent.keyDown(screen.getByLabelText('Texto da alternativa E'), { code: 'Digit2', altKey: true });
    expect(screen.getByLabelText('Alternativa B é a correta')).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    const [[, opcoes]] = chamadas('POST', '/api/admin/questoes');
    const corpo = JSON.parse(opcoes.body);
    expect(corpo).toMatchObject({ idMateria: 1, origem: 'AUTORAL', idProva: null, enunciado: 'Assinale.' });
    expect(corpo.alternativas.filter((a: { correta: boolean }) => a.correta)).toEqual([
      { letra: 'B', texto: 'texto B', correta: true },
    ]);
    expect(await screen.findByText('Questão salva como rascunho.')).toBeInTheDocument();
  });

  it('não envia sem gabarito', async () => {
    rotas['GET /api/admin/questoes/12'] = () => json(200, { ...QUESTAO, alternativas: QUESTAO.alternativas.map((a) => ({ ...a, correta: false })) });
    renderizar('/questoes/12');
    expect(await screen.findByDisplayValue('Assinale.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Marque o gabarito.');
    expect(chamadas('PUT', '/api/admin/questoes/12')).toHaveLength(0);
  });

  it('mostra o motivo quando a publicação é recusada', async () => {
    rotas['GET /api/admin/questoes/12'] = () => json(200, QUESTAO);
    rotas['PATCH /api/admin/questoes/12/publicar'] = () =>
      json(409, { status: 409, detail: 'O comentário é obrigatório para publicar a questão.' });
    renderizar('/questoes/12');

    await userEvent.click(await screen.findByRole('button', { name: 'Publicar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('O comentário é obrigatório');
  });

  it('mostra a prévia com o HTML devolvido pela API', async () => {
    renderizar('/questoes/nova');
    expect(await screen.findByText('prévia', {}, { timeout: 2000 })).toBeInTheDocument();
  });
});
