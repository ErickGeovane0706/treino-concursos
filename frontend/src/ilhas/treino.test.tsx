import { act, StrictMode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CHAVE_PROGRESSO,
  definirOcultarResolvidas,
  lerProgresso,
  registrarResposta,
} from '../progresso/progresso';
import {
  IlhaTreino,
  iniciarIlhaTreino,
  lerDadosTreino,
  MAX_PAGINAS_PROXIMA,
  type DadosTreino,
} from './treino';

function criarDadosPadrao(parcial: Partial<DadosTreino> = {}): DadosTreino {
  return {
    id: 42,
    correta: 'B',
    rotuloCorreta: 'B',
    proximaUrl: '/questao/43/proxima-questao?de=direito-constitucional',
    listagemUrl: '/materia/direito-constitucional',
    filtro: 'direito-constitucional',
    alternativas: [
      { letra: 'A', rotulo: 'A', textoHtml: '<p>Alternativa A</p>' },
      { letra: 'B', rotulo: 'B', textoHtml: '<p>Alternativa <strong>correta</strong></p>' },
      { letra: 'C', rotulo: 'C', textoHtml: '<p>Alternativa C</p>' },
    ],
    comentarioHtml: '<p>Comentário explicando por que a B está certa.</p>',
    ...parcial,
  };
}

describe('Ilha de treino da questão', () => {
  beforeEach(() => {
    localStorage.clear();
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('lê os dados do HTML renderizado pelo servidor e substitui o <details> ao montar a ilha', async () => {
    document.body.innerHTML = `
      <div
        data-ilha="treino"
        data-id="15"
        data-correta="C"
        data-rotulo-correta="Certo"
        data-proxima-url="/questao/16/outra?de=assunto:atos-administrativos"
        data-listagem-url="/assunto/atos-administrativos"
        data-filtro="assunto:atos-administrativos"
      >
        <ul class="opts">
          <li>
            <button type="button" class="opt" data-letra="C">
              <span class="l">Certo</span>
              <span class="opt-texto"><p>Certo</p></span>
            </button>
          </li>
          <li>
            <button type="button" class="opt" data-letra="E">
              <span class="l">Errado</span>
              <span class="opt-texto"><p>Errado</p></span>
            </button>
          </li>
        </ul>
        <details class="gabarito-ssr">
          <summary>Ver resposta</summary>
          <div data-comentario-html><p>Comentário do servidor</p></div>
        </details>
      </div>
    `;

    const elemento = document.querySelector<HTMLElement>('[data-ilha="treino"]')!;
    const lido = lerDadosTreino(elemento);

    expect(lido).toEqual({
      id: 15,
      correta: 'C',
      rotuloCorreta: 'Certo',
      proximaUrl: '/questao/16/outra?de=assunto:atos-administrativos',
      listagemUrl: '/assunto/atos-administrativos',
      filtro: 'assunto:atos-administrativos',
      alternativas: [
        { letra: 'C', rotulo: 'Certo', textoHtml: '<p>Certo</p>' },
        { letra: 'E', rotulo: 'Errado', textoHtml: '<p>Errado</p>' },
      ],
      comentarioHtml: '<p>Comentário do servidor</p>',
    });

    await act(async () => iniciarIlhaTreino());
    expect(document.querySelector('details.gabarito-ssr')).toBeNull();
    expect(screen.getAllByRole('button')).toHaveLength(2);
  });

  it('corrige ao clicar na alternativa certa, trava os botões, foca em "Próxima questão" e salva no localStorage', async () => {
    const user = userEvent.setup();
    render(<IlhaTreino dados={criarDadosPadrao()} />);

    const botoes = screen.getAllByRole('button');
    await user.click(botoes[1]); // B (correta)

    expect(botoes[0]).toHaveClass('opt', 'dim');
    expect(botoes[1]).toHaveClass('opt', 'ok');
    expect(botoes[2]).toHaveClass('opt', 'dim');
    for (const botao of botoes) {
      expect(botao).toBeDisabled();
    }

    expect(screen.getByRole('status')).toHaveTextContent('Você acertou');
    expect(screen.getByText('Comentário explicando por que a B está certa.')).toBeInTheDocument();
    expect(document.querySelector('[data-espaco-anuncio]')).not.toBeNull();

    const linkProxima = screen.getByRole('link', { name: 'Próxima questão' });
    expect(linkProxima).toHaveAttribute('href', '/questao/43/proxima-questao?de=direito-constitucional');
    expect(linkProxima).toHaveFocus();

    const progresso = lerProgresso();
    expect(progresso.respostas['42']).toMatchObject({
      alternativa: 'B',
      acertou: true,
    });
    expect(progresso.ultimaVista['direito-constitucional']).toBe(42);
  });

  it('marca a escolhida como errada e destaca a correta quando o visitante erra, permitindo "Refazer"', async () => {
    const user = userEvent.setup();
    render(<IlhaTreino dados={criarDadosPadrao()} />);

    const botoes = screen.getAllByRole('button');
    await user.click(botoes[0]); // A (errada)

    expect(botoes[0]).toHaveClass('opt', 'bad');
    expect(botoes[1]).toHaveClass('opt', 'ok');
    expect(botoes[2]).toHaveClass('opt', 'dim');
    expect(screen.getByRole('status')).toHaveTextContent('Você errou. A correta é a B');

    await user.click(screen.getByRole('button', { name: 'Refazer' }));

    expect(screen.queryByRole('status')).toBeNull();
    for (const botao of screen.getAllByRole('button')) {
      expect(botao).not.toBeDisabled();
      expect(botao).toHaveClass('opt');
      expect(botao).not.toHaveClass('ok', 'bad', 'dim');
    }
  });

  it('permite responder pelas teclas A a E sem usar o mouse (RNF02)', async () => {
    const user = userEvent.setup();
    render(<IlhaTreino dados={criarDadosPadrao()} />);

    await user.keyboard('b');

    expect(screen.getByRole('status')).toHaveTextContent('Você acertou');
    const botoes = screen.getAllByRole('button');
    expect(botoes[1]).toHaveClass('opt', 'ok');

    // Outra tecla depois de respondida nao muda a resposta
    await user.keyboard('a');
    expect(screen.getByRole('status')).toHaveTextContent('Você acertou');
  });

  it('mostra "Ver mais questões" apontando para a listagem quando não há próxima questão', async () => {
    const user = userEvent.setup();
    render(<IlhaTreino dados={criarDadosPadrao({ proximaUrl: null })} />);

    await user.click(screen.getAllByRole('button')[1]);

    const link = screen.getByRole('link', { name: 'Ver mais questões' });
    expect(link).toHaveAttribute('href', '/materia/direito-constitucional');
  });

  it('pula questões já resolvidas no botão "Próxima questão" quando "Ocultar questões já resolvidas" está ativa (RF10 RN4)', async () => {
    const user = userEvent.setup();
    registrarResposta(43, 'A', true, 'direito-constitucional');
    definirOcultarResolvidas(true);

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ itens: [43, 45], proximoCursor: null }), { status: 200 }),
    );

    render(<IlhaTreino dados={criarDadosPadrao()} />);
    await user.click(screen.getAllByRole('button')[1]);

    const linkProxima = await screen.findByRole('link', { name: 'Próxima questão' });
    expect(linkProxima).toHaveAttribute('href', '/questao/45?de=direito-constitucional');
  });

  it('mantém o link do servidor quando não acha não resolvida dentro do limite de páginas (RNF03)', async () => {
    registrarResposta(43, 'A', true, 'direito-constitucional');
    definirOcultarResolvidas(true);

    let chamadas = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
      chamadas++;
      const base = 43 + (chamadas - 1) * 2;
      return new Response(JSON.stringify({ itens: [base], proximoCursor: base }), { status: 200 });
    });
    registrarResposta(45, 'A', true, 'direito-constitucional');

    render(<IlhaTreino dados={criarDadosPadrao()} />);
    await act(async () => {
      await new Promise((r) => setTimeout(r, 0));
    });

    expect(chamadas).toBe(MAX_PAGINAS_PROXIMA);
    expect(screen.getByRole('link', { name: 'Próxima questão' })).toHaveAttribute(
      'href',
      '/questao/43/proxima-questao?de=direito-constitucional',
    );
  });

  it('antes de responder mantém o <details>, o espaço do anúncio e o link renderizados pelo servidor (CLS)', () => {
    render(<IlhaTreino dados={criarDadosPadrao()} />);

    const gabarito = document.querySelector('details[data-gabarito]');
    expect(gabarito).not.toBeNull();
    expect(gabarito).toHaveTextContent('Gabarito: B');
    expect(gabarito).toHaveTextContent('Comentário explicando por que a B está certa.');
    expect(document.querySelector('[data-espaco-anuncio]')).not.toBeNull();
    expect(screen.getByRole('link', { name: 'Próxima questão' })).toHaveAttribute(
      'href',
      '/questao/43/proxima-questao?de=direito-constitucional',
    );
    expect(screen.queryByRole('button', { name: 'Refazer' })).toBeNull();
  });

  it('registra a resposta uma única vez mesmo no StrictMode e com cliques repetidos', async () => {
    const user = userEvent.setup();
    render(
      <StrictMode>
        <IlhaTreino dados={criarDadosPadrao()} />
      </StrictMode>,
    );

    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    const botoes = screen.getAllByRole('button');
    await user.click(botoes[0]);
    await user.keyboard('b');

    const gravacoesProgresso = setItemSpy.mock.calls.filter(([chave]) => chave === CHAVE_PROGRESSO);
    expect(gravacoesProgresso).toHaveLength(1);
    expect(lerProgresso().respostas['42']).toMatchObject({ alternativa: 'A', acertou: false });
  });

  it('continua funcionando mesmo com localStorage bloqueado e exibe aviso discreto (RF10 RN5, RNF06)', async () => {
    const user = userEvent.setup();
    const getItemSpy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Blocked', 'SecurityError');
    });
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Blocked', 'SecurityError');
    });

    try {
      render(<IlhaTreino dados={criarDadosPadrao()} />);
      expect(document.querySelector('[data-aviso-armazenamento]')).toHaveTextContent(
        'Armazenamento do navegador indisponível',
      );
      await user.click(screen.getAllByRole('button')[1]);
      expect(screen.getByRole('status')).toHaveTextContent('Você acertou');
      expect(localStorage.length).toBe(0);
      expect(CHAVE_PROGRESSO).toBe('treino-concursos:progresso');
    } finally {
      getItemSpy.mockRestore();
      setItemSpy.mockRestore();
    }
  });
});

