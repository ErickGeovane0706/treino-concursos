import { act } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CHAVE_PROGRESSO,
  definirOcultarResolvidas,
  lerProgresso,
  registrarResposta,
} from '../progresso/progresso';
import { IlhaTreino, iniciarIlhaTreino, lerDadosTreino, type DadosTreino } from './treino';

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

  it('envia POST /api/questoes/{id}/respostas só na primeira resposta do navegador e exibe "62% acertaram" e % por alternativa com 30+ respostas (RF17 RN1–RN3)', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/respostas')) {
        return new Response(null, { status: 202 });
      }
      if (url.endsWith('/estatisticas')) {
        return new Response(
          JSON.stringify({
            disponivel: true,
            totalRespostas: 100,
            percentualAcertos: 62,
            alternativas: [
              { letra: 'A', percentual: 20 },
              { letra: 'B', percentual: 62 },
              { letra: 'C', percentual: 18 },
            ],
          }),
          { status: 200 },
        );
      }
      return new Response(null, { status: 404 });
    });

    render(<IlhaTreino dados={criarDadosPadrao()} />);

    // Primeira resposta neste navegador -> envia POST e busca estatisticas
    await user.click(screen.getAllByRole('button')[1]); // B

    expect(await screen.findByText('62% acertaram')).toBeInTheDocument();
    expect(document.querySelector('[data-estatistica-alternativa="A"]')).toHaveTextContent('20%');
    expect(document.querySelector('[data-estatistica-alternativa="B"]')).toHaveTextContent('62%');
    expect(document.querySelector('[data-estatistica-alternativa="C"]')).toHaveTextContent('18%');

    const chamadasPostAntes = fetchSpy.mock.calls.filter(([u]) => String(u).endsWith('/respostas'));
    expect(chamadasPostAntes).toHaveLength(1);
    expect(chamadasPostAntes[0][0]).toBe('/api/questoes/42/respostas');
    expect(chamadasPostAntes[0][1]).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ letra: 'B' }),
    });

    // Clicar em "Refazer" e responder de novo nao envia novo POST (RF17 RN2)
    await user.click(screen.getByRole('button', { name: 'Refazer' }));
    expect(screen.queryByText('62% acertaram')).toBeNull();

    await user.click(screen.getAllByRole('button')[0]); // A
    expect(await screen.findByText('62% acertaram')).toBeInTheDocument();

    const chamadasPostDepois = fetchSpy.mock.calls.filter(([u]) => String(u).endsWith('/respostas'));
    expect(chamadasPostDepois).toHaveLength(1);
  });

  it('não envia POST /api/questoes/{id}/respostas quando a questão já constava como resolvida no navegador (RF17 RN2)', async () => {
    const user = userEvent.setup();
    registrarResposta(42, 'A', false, 'direito-constitucional');

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          disponivel: false,
          totalRespostas: 12,
          percentualAcertos: null,
          alternativas: [],
        }),
        { status: 200 },
      ),
    );

    render(<IlhaTreino dados={criarDadosPadrao()} />);
    await user.click(screen.getAllByRole('button')[1]); // B

    const chamadasPost = fetchSpy.mock.calls.filter(([u]) => String(u).endsWith('/respostas'));
    expect(chamadasPost).toHaveLength(0);
    // Com menos de 30 respostas, nao exibe percentual (RF17 RN3)
    expect(document.querySelector('[data-estatistica-acertos]')).toBeNull();
    expect(document.querySelector('[data-estatistica-alternativa]')).toBeNull();
  });
});

