import { act, StrictMode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CHAVE_PROGRESSO,
  definirOcultarResolvidas,
  estaNoCadernoDeErros,
  lerProgresso,
  registrarResposta,
} from '../progresso/progresso';
import {
  IlhaReporte,
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

  it('mantém o bloco de anúncio do servidor intacto ao montar a ilha e usa as ações do servidor (RF12)', async () => {
    document.body.innerHTML = `
      <div data-ilha="treino" data-id="15" data-correta="A" data-proxima-url="/questao/16/outra">
        <button class="opt" data-letra="A"><span class="l">A</span><span class="opt-texto">Um</span></button>
        <div data-comentario-html><p>Comentário</p></div>
      </div>
      <aside data-bloco-anuncio="questao"><span>Publicidade</span><ins class="adsbygoogle"></ins></aside>
      <div data-acoes-treino><a href="/questao/16/outra" data-proxima data-acoes-servidor>Próxima questão</a></div>
    `;
    const anuncio = document.querySelector('aside[data-bloco-anuncio]');
    const insercao = document.querySelector('ins.adsbygoogle');

    await act(async () => iniciarIlhaTreino());

    expect(document.querySelector('aside[data-bloco-anuncio]')).toBe(anuncio);
    expect(document.querySelector('ins.adsbygoogle')).toBe(insercao);
    expect(anuncio).toHaveTextContent('Publicidade');

    // Um único "Próxima questão", depois do anúncio
    const links = screen.getAllByRole('link', { name: 'Próxima questão' });
    expect(links).toHaveLength(1);
    expect(document.querySelector('[data-acoes-servidor]')).toBeNull();
    expect(links[0].closest('[data-acoes-treino]')).not.toBeNull();
    expect(anuncio!.compareDocumentPosition(links[0]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    // "Refazer" aparece no mesmo lugar depois da resposta
    await act(async () => screen.getAllByRole('button')[0].click());
    expect(screen.getByRole('button', { name: 'Refazer' }).closest('[data-acoes-treino]')).not.toBeNull();
    expect(document.querySelector('aside[data-bloco-anuncio]')).toBe(anuncio);
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

  it('abre o formulário de reporte de erro sem dados pessoais e envia POST /api/questoes/{id}/reportes (RF16 RN1–RN2, US07)', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 7, idQuestao: 42, motivo: 'GABARITO_ERRADO', status: 'ABERTO' }), {
        status: 201,
      }),
    );

    render(<IlhaReporte idQuestao={42} />);

    await user.click(screen.getByRole('button', { name: 'Reportar erro' }));

    // RF16 RN2: formulario pequeno sem campos de nome ou e-mail
    expect(screen.queryByLabelText(/nome/i)).toBeNull();
    expect(screen.queryByLabelText(/e-mail|email/i)).toBeNull();

    await user.selectOptions(screen.getByLabelText('Motivo do reporte'), 'COMENTARIO_INCORRETO');
    await user.type(screen.getByLabelText('Descrição (opcional)'), 'O artigo citado foi revogado.');
    await user.click(screen.getByRole('button', { name: 'Enviar reporte' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Reporte enviado');
    expect(fetchSpy).toHaveBeenCalledWith('/api/questoes/42/reportes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        motivo: 'COMENTARIO_INCORRETO',
        descricao: 'O artigo citado foi revogado.',
      }),
    });
  });

  it('exibe a mensagem de erro quando o limite de reportes por IP (429) é atingido (RF16 RN3)', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 429,
          detail: 'Muitas requisições deste endereço em pouco tempo. Tente novamente mais tarde.',
        }),
        { status: 429, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    render(<IlhaReporte idQuestao={42} />);
    await user.click(screen.getByRole('button', { name: 'Reportar erro' }));
    await user.click(screen.getByRole('button', { name: 'Enviar reporte' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas requisições deste endereço');
  });

  it('remove a questão do caderno de erros ao acertar na revisão e aponta "Próxima questão" para a seguinte do caderno (/questao/{id}/{slug}?de=caderno) (RF11 RN1–RN2, US04)', async () => {
    const user = userEvent.setup();
    // Visitante errou as questoes 42 e 50 anteriormente
    registrarResposta(42, 'A', false, 'direito-constitucional');
    registrarResposta(50, 'C', false, 'portugues');
    expect(estaNoCadernoDeErros(42)).toBe(true);
    expect(estaNoCadernoDeErros(50)).toBe(true);

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith('/api/questoes/resumos')) {
        return new Response(
          JSON.stringify([
            {
              id: 42,
              slug: 'questao-42',
              slugMateria: 'direito-constitucional',
              nomeMateria: 'Direito Constitucional',
              nomeAssunto: null,
              fonte: 'Questão autoral',
              resumoEnunciado: 'Enunciado 42',
              anulada: false,
              desatualizada: false,
            },
            {
              id: 50,
              slug: 'concordancia-verbal',
              slugMateria: 'portugues',
              nomeMateria: 'Português',
              nomeAssunto: 'Sintaxe',
              fonte: 'Questão autoral',
              resumoEnunciado: 'Enunciado 50',
              anulada: false,
              desatualizada: false,
            },
          ]),
          { status: 200 },
        );
      }
      return new Response(JSON.stringify({ disponivel: false, totalRespostas: 1, percentualAcertos: null, alternativas: [] }), {
        status: 200,
      });
    });

    render(
      <IlhaTreino
        dados={criarDadosPadrao({
          id: 42,
          filtro: 'caderno',
          listagemUrl: '/caderno-de-erros',
          proximaUrl: null,
        })}
      />,
    );

    // Acerta a questao #42 ao refazer pelo caderno
    await user.click(screen.getAllByRole('button')[1]); // B (correta)

    expect(estaNoCadernoDeErros(42)).toBe(false);
    expect(estaNoCadernoDeErros(50)).toBe(true);
    expect(document.querySelector('[data-removida-caderno]')).toHaveTextContent(
      'Questão removida do caderno de erros.',
    );

    const linkProxima = await screen.findByRole('link', { name: 'Próxima questão' });
    expect(linkProxima).toHaveAttribute('href', '/questao/50/concordancia-verbal?de=caderno');
  });
});

