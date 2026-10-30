import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ReportesFila } from './Reportes';

const pagina = (content: unknown[]) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
});

const QUESTAO_12 = {
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
  enunciado: 'Assinale a alternativa correta sobre crase.',
  comentario: 'Comentário original.',
  slug: 'assinale-a-alternativa-correta-sobre-crase',
  status: 'PUBLICADA',
  anulada: false,
  desatualizada: false,
  totalRespostas: 15,
  totalAcertos: 9,
  criadoEm: '2026-10-19T10:00:00',
  atualizadoEm: '2026-10-19T10:00:00',
  publicadoEm: '2026-10-19T10:05:00',
  alternativas: ['A', 'B', 'C', 'D', 'E'].map((letra, i) => ({
    id: i + 1,
    letra,
    texto: `Alternativa ${letra}`,
    correta: letra === 'B',
    totalMarcacoes: 3,
  })),
};

const REPORTE_PRIORITARIO = {
  id: 8,
  idQuestao: 12,
  trechoQuestao: 'Assinale a alternativa correta sobre crase.',
  nomeMateria: 'Português',
  motivo: 'GABARITO_ERRADO',
  descricao: 'A correta é a letra C.',
  status: 'ABERTO',
  prioritario: true,
  reportesGabaritoAbertos: 3,
  criadoEm: '2026-10-20T14:00:00',
  idResolvidoPor: null,
  nomeResolvidoPor: null,
  resolvidoEm: null,
  observacaoResolucao: null,
};

const REPORTE_NORMAL = {
  id: 5,
  idQuestao: 9,
  trechoQuestao: 'Outra questão sem prioridade.',
  nomeMateria: 'Direito Constitucional',
  motivo: 'ENUNCIADO_COM_ERRO',
  descricao: 'Erro de digitação.',
  status: 'ABERTO',
  prioritario: false,
  reportesGabaritoAbertos: 0,
  criadoEm: '2026-10-20T11:00:00',
  idResolvidoPor: null,
  nomeResolvidoPor: null,
  resolvidoEm: null,
  observacaoResolucao: null,
};

function json(status: number, corpo: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }),
  );
}

describe('Fila de reportes no painel (US12, RF16)', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  let rotas: Record<string, (opcoes: RequestInit) => Promise<Response>>;

  beforeEach(() => {
    rotas = {
      'GET /api/admin/reportes-erro': () => json(200, pagina([REPORTE_PRIORITARIO, REPORTE_NORMAL])),
      'GET /api/admin/questoes/12': () => json(200, QUESTAO_12),
      'GET /api/admin/materias': () =>
        json(200, pagina([{ id: 1, nome: 'Português', slug: 'portugues', ativa: true }])),
      'GET /api/admin/materias/1/assuntos': () => json(200, []),
      'GET /api/admin/provas': () => json(200, pagina([])),
      'POST /api/admin/previa-markdown': () => json(200, { html: '<p>prévia</p>' }),
    };
    fetchMock = vi.fn((url: string, opcoes: RequestInit) => {
      const chave = `${opcoes.method} ${url.split('?')[0]}`;
      return (rotas[chave] ?? (() => json(200, pagina([]))))(opcoes);
    });
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('exibe prioridade para 3+ reportes de gabarito errado, abre a questão ao lado e resolve com observação sem trocar de tela', async () => {
    const user = userEvent.setup();
    rotas['PUT /api/admin/questoes/12'] = () =>
      json(200, { ...QUESTAO_12, comentario: 'Comentário corrigido.' });
    rotas['PATCH /api/admin/reportes-erro/8/resolver'] = () => {
      rotas['GET /api/admin/reportes-erro'] = () => json(200, pagina([REPORTE_NORMAL]));
      return json(200, {
        ...REPORTE_PRIORITARIO,
        status: 'RESOLVIDO',
        idResolvidoPor: 1,
        nomeResolvidoPor: 'Ana',
        resolvidoEm: '2026-10-20T15:00:00',
        observacaoResolucao: 'Gabarito revisado e comentário ajustado.',
      });
    };

    render(
      <MemoryRouter initialEntries={['/reportes']}>
        <Routes>
          <Route path="/reportes" element={<ReportesFila />} />
        </Routes>
      </MemoryRouter>,
    );

    // RF16 RN5: reporte prioritario aparece com destaque de prioridade
    expect(await screen.findByText('Prioridade (3 de gabarito)')).toBeInTheDocument();

    // Clicar em abrir abre a questao ao lado do reporte na mesma tela (/reportes)
    await user.click(screen.getAllByRole('button', { name: 'Abrir questão' })[0]);

    expect(await screen.findByRole('heading', { name: 'Reporte #8' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Questão #12' })).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Assinale a alternativa correta sobre crase.')).toBeInTheDocument();

    // Editor salva a questao ali mesmo
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Questão salva.')).toBeInTheDocument();

    // Editor preenche a observacao e resolve o reporte (RF16 RN4)
    await user.type(screen.getByLabelText('Observação da resolução'), 'Corrigido.');
    await user.click(screen.getByRole('button', { name: 'Resolver reporte' }));

    await waitFor(() => {
      const chamadasResolver = fetchMock.mock.calls.filter(
        ([url, o]) => o.method === 'PATCH' && url === '/api/admin/reportes-erro/8/resolver',
      );
      expect(chamadasResolver).toHaveLength(1);
      expect(JSON.parse(chamadasResolver[0][1].body)).toEqual({
        observacao: 'Corrigido.',
      });
    });

    expect(await screen.findByText('Reporte #8 marcado como resolvido.')).toBeInTheDocument();
  });

  it('permite descartar reporte com observação (RF16 RN4)', async () => {
    const user = userEvent.setup();
    rotas['PATCH /api/admin/reportes-erro/8/descartar'] = () =>
      json(200, {
        ...REPORTE_PRIORITARIO,
        status: 'DESCARTADO',
        idResolvidoPor: 1,
        nomeResolvidoPor: 'Ana',
        resolvidoEm: '2026-10-20T15:00:00',
        observacaoResolucao: 'Improcedente.',
      });

    render(
      <MemoryRouter initialEntries={['/reportes']}>
        <Routes>
          <Route path="/reportes" element={<ReportesFila />} />
        </Routes>
      </MemoryRouter>,
    );

    await user.click((await screen.findAllByRole('button', { name: 'Abrir questão' }))[0]);
    await user.type(await screen.findByLabelText('Observação da resolução'), 'Improcedente.');
    await user.click(screen.getByRole('button', { name: 'Descartar reporte' }));

    expect(await screen.findByText('Reporte #8 descartado.')).toBeInTheDocument();
  });
});
