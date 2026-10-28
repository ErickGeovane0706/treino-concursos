import { act } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  lerMateriaCaderno,
  lerOcultarResolvidas,
  lerProgresso,
  obterUltimaVista,
  registrarResposta,
} from '../progresso/progresso';
import { IlhaCadernoDeErros, IlhaListagem, IlhaMeuProgresso, iniciarIlhaListagem } from './listagem';

describe('Ilha de listagem e página Meu progresso (RF10 RN1-RN6, US03, RNF06)', () => {
  beforeEach(() => {
    localStorage.clear();
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('marca questões resolvidas como acertou/errou e oculta-as ao ativar "Ocultar questões já resolvidas" (RN2, RN4)', async () => {
    const user = userEvent.setup();
    registrarResposta(10, 'A', true, 'portugues');
    registrarResposta(12, 'C', false, 'portugues');

    document.body.innerHTML = `
      <section data-ilha="listagem" data-filtro="portugues">
        <h1>Questões de Português</h1>
        <ol class="lista-questoes" data-lista-questoes>
          <li class="item-questao" data-id-questao="10"><div class="meta"><span>#10</span></div></li>
          <li class="item-questao" data-id-questao="11"><div class="meta"><span>#11</span></div></li>
          <li class="item-questao" data-id-questao="12"><div class="meta"><span>#12</span></div></li>
        </ol>
      </section>
    `;

    await act(async () => iniciarIlhaListagem());

    // Preserva o H1 e a lista renderizados pelo servidor
    expect(screen.getByRole('heading', { name: 'Questões de Português' })).toBeInTheDocument();

    const item10 = document.querySelector<HTMLElement>('[data-id-questao="10"]')!;
    const item11 = document.querySelector<HTMLElement>('[data-id-questao="11"]')!;
    const item12 = document.querySelector<HTMLElement>('[data-id-questao="12"]')!;

    expect(item10).toHaveAttribute('data-resolvida', 'true');
    expect(item10).toHaveAttribute('data-resultado', 'acertou');
    expect(item10).toHaveTextContent('Resolvida · Acertou');

    expect(item11).not.toHaveAttribute('data-resolvida');

    expect(item12).toHaveAttribute('data-resolvida', 'true');
    expect(item12).toHaveAttribute('data-resultado', 'errou');
    expect(item12).toHaveTextContent('Resolvida · Errou');

    const chaveOcultar = screen.getByRole('checkbox', { name: /ocultar questões já resolvidas/i });
    expect(chaveOcultar).not.toBeChecked();

    await user.click(chaveOcultar);

    expect(lerOcultarResolvidas()).toBe(true);
    expect(item10.hidden).toBe(true);
    expect(item11.hidden).toBe(false);
    expect(item12.hidden).toBe(true);

    await user.click(chaveOcultar);
    expect(lerOcultarResolvidas()).toBe(false);
    expect(item10.hidden).toBe(false);
    expect(item12.hidden).toBe(false);
  });

  it('"Continuar de onde parei" busca ids do filtro e navega para a primeira não resolvida após a última vista (RN3, US03)', async () => {
    const user = userEvent.setup();
    registrarResposta(10, 'A', true, 'portugues');

    const raiz = document.createElement('section');
    const aoNavegar = vi.fn();
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ itens: [11, 14], proximoCursor: null }), { status: 200 }),
    ) as unknown as typeof fetch;

    render(
      <IlhaListagem
        raiz={raiz}
        filtro="portugues"
        temListaQuestoes={true}
        aoNavegar={aoNavegar}
        fetchFn={fetchMock}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Continuar de onde parei' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/questoes/ids?materia=portugues&depoisDe=10',
      expect.any(Object),
    );
    expect(aoNavegar).toHaveBeenCalledWith('/questao/11?de=portugues');
  });

  it('oferece reiniciar do início quando não há questão não resolvida após a última vista (RN3)', async () => {
    const user = userEvent.setup();
    registrarResposta(20, 'B', true, 'portugues/crase');

    const raiz = document.createElement('section');
    const aoNavegar = vi.fn();
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('depoisDe=20')) {
        return new Response(JSON.stringify({ itens: [], proximoCursor: null }), { status: 200 });
      }
      return new Response(JSON.stringify({ itens: [5, 20], proximoCursor: null }), { status: 200 });
    }) as unknown as typeof fetch;

    render(
      <IlhaListagem
        raiz={raiz}
        filtro="portugues/crase"
        temListaQuestoes={true}
        aoNavegar={aoNavegar}
        fetchFn={fetchMock}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Continuar de onde parei' }));

    expect(document.querySelector('[data-fim-filtro]')).not.toBeNull();
    const botaoReiniciar = screen.getByRole('button', { name: 'Reiniciar do início' });
    await user.click(botaoReiniciar);

    expect(obterUltimaVista('portugues/crase')).toBeNull();
    expect(aoNavegar).toHaveBeenCalledWith('/questao/5?de=portugues/crase');
  });

  it('exibe resumo na página Meu progresso e permite apagar todo o progresso (RN6)', async () => {
    const user = userEvent.setup();
    registrarResposta(1, 'A', true, 'portugues');
    registrarResposta(2, 'B', true, 'portugues');
    registrarResposta(3, 'C', false, 'direito');

    render(<IlhaMeuProgresso />);

    expect(document.querySelector('[data-total-resolvidas]')).toHaveTextContent('3');
    expect(document.querySelector('[data-total-acertos]')).toHaveTextContent('2');
    expect(document.querySelector('[data-total-erros]')).toHaveTextContent('1');
    expect(document.querySelector('[data-percentual-acertos]')).toHaveTextContent('67%');

    const botaoApagar = screen.getByRole('button', { name: 'Apagar todo o progresso' });
    await user.click(botaoApagar);

    expect(lerProgresso().respostas).toEqual({});
    expect(document.querySelector('[data-total-resolvidas]')).toHaveTextContent('0');
    expect(document.querySelector('[data-progresso-apagado]')).toHaveTextContent(
      'Todo o progresso salvo neste navegador foi apagado.',
    );
  });

  it('exibe aviso discreto e não lança erro no console quando o localStorage está bloqueado (RN5, RNF06)', () => {
    const erroConsole = vi.spyOn(console, 'error');
    const getItemSpy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Blocked', 'SecurityError');
    });
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Blocked', 'SecurityError');
    });

    try {
      const raiz = document.createElement('section');
      const { unmount } = render(<IlhaListagem raiz={raiz} filtro="todas" temListaQuestoes={true} />);
      expect(document.querySelector('[data-aviso-armazenamento]')).not.toBeNull();
      unmount();

      render(<IlhaMeuProgresso />);
      expect(document.querySelector('[data-aviso-armazenamento]')).not.toBeNull();
      expect(erroConsole).not.toHaveBeenCalled();
    } finally {
      getItemSpy.mockRestore();
      setItemSpy.mockRestore();
      erroConsole.mockRestore();
    }
  });

  it('exibe estado vazio em /caderno-de-erros quando não há questões erradas (RF11, US04)', () => {
    registrarResposta(10, 'A', true, 'portugues');
    render(<IlhaCadernoDeErros />);

    expect(document.querySelector('[data-caderno-vazio]')).toHaveTextContent(
      'Você não tem questões no caderno de erros no momento.',
    );
  });

  it('busca os dados mínimos das questões erradas em /caderno-de-erros, permite filtrar por matéria e aponta para /questao/{id}/{slug}?de=caderno (RF11 RN1–RN2, US04)', async () => {
    const user = userEvent.setup();
    registrarResposta(10, 'A', false, 'portugues');
    registrarResposta(15, 'B', true, 'portugues');
    registrarResposta(22, 'C', false, 'direito-constitucional');
    registrarResposta(30, 'D', false, 'portugues');

    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify([
          {
            id: 10,
            slug: 'concordancia-verbal',
            slugMateria: 'portugues',
            nomeMateria: 'Português',
            nomeAssunto: 'Concordância verbal',
            fonte: 'CEBRASPE · PRF · 2024 · Policial',
            resumoEnunciado: 'Assinale a opção correta sobre concordância.',
            anulada: false,
            desatualizada: false,
          },
          {
            id: 22,
            slug: 'direitos-fundamentais',
            slugMateria: 'direito-constitucional',
            nomeMateria: 'Direito Constitucional',
            nomeAssunto: 'Art. 5º',
            fonte: 'Questão autoral',
            resumoEnunciado: 'Sobre os direitos fundamentais na CF/88.',
            anulada: false,
            desatualizada: false,
          },
          {
            id: 30,
            slug: 'emprego-da-crase',
            slugMateria: 'portugues',
            nomeMateria: 'Português',
            nomeAssunto: 'Crase',
            fonte: 'FGV · TJ · 2025 · Técnico',
            resumoEnunciado: 'Assinale a alternativa em que há crase.',
            anulada: false,
            desatualizada: false,
          },
        ]),
        { status: 200 },
      ),
    ) as unknown as typeof fetch;

    render(<IlhaCadernoDeErros fetchFn={fetchMock} />);

    expect(await screen.findByText('Assinale a opção correta sobre concordância.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/questoes/resumos?ids=10,22,30', expect.any(Object));

    expect(document.querySelector('[data-resumo-caderno]')).toHaveTextContent('3 questões para revisar');
    expect(document.querySelector('[data-iniciar-caderno]')).toHaveAttribute(
      'href',
      '/questao/10/concordancia-verbal?de=caderno',
    );
    expect(document.querySelector('[data-link-questao-caderno="22"]')).toHaveAttribute(
      'href',
      '/questao/22/direitos-fundamentais?de=caderno',
    );

    // Filtra por Direito Constitucional
    await user.click(screen.getByRole('button', { name: 'Direito Constitucional (1)' }));

    expect(lerMateriaCaderno()).toBe('direito-constitucional');
    expect(document.querySelector('[data-resumo-caderno]')).toHaveTextContent('1 questão para revisar');
    expect(document.querySelector('[data-iniciar-caderno]')).toHaveAttribute(
      'href',
      '/questao/22/direitos-fundamentais?de=caderno',
    );
    expect(screen.queryByText('Assinale a opção correta sobre concordância.')).toBeNull();
    expect(screen.getByText('Sobre os direitos fundamentais na CF/88.')).toBeInTheDocument();

    // Volta para Todas
    await user.click(screen.getByRole('button', { name: 'Todas (3)' }));
    expect(lerMateriaCaderno()).toBeNull();
    expect(document.querySelector('[data-resumo-caderno]')).toHaveTextContent('3 questões para revisar');
  });
});
