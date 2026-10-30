import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CHAVE_MATERIA_CADERNO,
  CHAVE_PROGRESSO,
  LIMITE_QUESTOES,
  armazenamentoDisponivel,
  buscarProximaNaoResolvida,
  buscarProximaQuestaoCaderno,
  buscarQuestoesCadernoDeErros,
  calcularResumoProgresso,
  consultarResposta,
  contarCadernoDeErros,
  definirMateriaCaderno,
  definirOcultarResolvidas,
  ehFiltroCaderno,
  escolherProximaDoCaderno,
  escolherProximaNaoResolvida,
  estaNoCadernoDeErros,
  extrairMateriaFiltroCaderno,
  filtrarCadernoPorMateria,
  foiResolvida,
  lerMateriaCaderno,
  lerOcultarResolvidas,
  lerProgresso,
  limparProgresso,
  limparUltimaVista,
  listarIdsCadernoDeErros,
  listarMateriasDoCaderno,
  normalizarChaveFiltro,
  obterUltimaVista,
  parametrosFiltroIds,
  registrarResposta,
  registrarUltimaVista,
  urlListagemDoFiltro,
  urlQuestaoCaderno,
  urlQuestaoPorId,
  type DadosProgresso,
  type QuestaoCaderno,
  type RegistroResposta,
} from './progresso';

describe('Módulo de progresso local (RF10 RN1-RN7, US03, RNF06)', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('inicia vazio com formato versionado v=1 quando não há dados salvos', () => {
    expect(armazenamentoDisponivel()).toBe(true);
    expect(lerProgresso()).toEqual({
      v: 1,
      respostas: {},
      ultimaVista: {},
      ocultarResolvidas: false,
    });
    expect(calcularResumoProgresso()).toEqual({
      totalResolvidas: 0,
      totalAcertos: 0,
      totalErros: 0,
      percentualAcertos: 0,
    });
  });

  it('registra respostas, consulta resultado, calcula resumo e atualiza última questão vista por filtro (RN1, RN2)', () => {
    registrarResposta(10, 'B', true, 'portugues/crase');
    registrarResposta(15, 'A', false, 'portugues/crase');
    registrarResposta(20, 'C', true, null);

    const bruto = JSON.parse(localStorage.getItem(CHAVE_PROGRESSO)!);
    expect(bruto.v).toBe(1);

    expect(foiResolvida(10)).toBe(true);
    expect(foiResolvida(15)).toBe(true);
    expect(foiResolvida(99)).toBe(false);

    const r10 = consultarResposta(10);
    expect(r10).not.toBeNull();
    expect(r10?.alternativa).toBe('B');
    expect(r10?.acertou).toBe(true);
    expect(() => new Date(r10!.data).toISOString()).not.toThrow();

    expect(consultarResposta(15)?.acertou).toBe(false);
    expect(consultarResposta(99)).toBeNull();

    expect(obterUltimaVista('portugues/crase')).toBe(15);
    expect(obterUltimaVista(null)).toBe(20);
    expect(obterUltimaVista('todas')).toBe(20);
    expect(obterUltimaVista('direito-constitucional')).toBeNull();

    const progresso = lerProgresso();
    expect(foiResolvida(10, progresso)).toBe(true);
    expect(calcularResumoProgresso(progresso)).toEqual({
      totalResolvidas: 3,
      totalAcertos: 2,
      totalErros: 1,
      percentualAcertos: 67,
    });
  });

  it('permite atualizar última vista separadamente e reiniciá-la por filtro (RN2, RN3)', () => {
    registrarUltimaVista(33, '/portugues/sintaxe/');
    expect(obterUltimaVista('portugues/sintaxe')).toBe(33);

    limparUltimaVista('portugues/sintaxe');
    expect(obterUltimaVista('portugues/sintaxe')).toBeNull();

    // Ignora ids invalidos
    registrarUltimaVista(0, 'portugues');
    registrarUltimaVista(-5, 'portugues');
    expect(obterUltimaVista('portugues')).toBeNull();
  });

  it('lê e altera a preferência "Ocultar questões já resolvidas" (RN4)', () => {
    expect(lerOcultarResolvidas()).toBe(false);

    definirOcultarResolvidas(true);
    expect(lerOcultarResolvidas()).toBe(true);

    definirOcultarResolvidas(false);
    expect(lerOcultarResolvidas()).toBe(false);
  });

  it('limpa todo o progresso salvo no navegador (RN6)', () => {
    registrarResposta(1, 'A', true, 'portugues');
    definirOcultarResolvidas(true);
    expect(calcularResumoProgresso().totalResolvidas).toBe(1);

    limparProgresso();

    expect(localStorage.getItem(CHAVE_PROGRESSO)).toBeNull();
    expect(lerProgresso()).toEqual({
      v: 1,
      respostas: {},
      ultimaVista: {},
      ocultarResolvidas: false,
    });
  });

  it('descarta os registros mais antigos quando ultrapassa o limite de 20.000 questões (RN7)', () => {
    const respostasIniciais: Record<string, RegistroResposta> = {};
    const baseMs = Date.parse('2026-01-01T00:00:00.000Z');

    // Preenche exatamente 20.000 questoes com datas crescentes (id 1 e a mais antiga, id 20000 a mais recente)
    for (let id = 1; id <= LIMITE_QUESTOES; id++) {
      respostasIniciais[String(id)] = {
        alternativa: 'A',
        acertou: true,
        data: new Date(baseMs + id * 1000).toISOString(),
      };
    }
    const semente: DadosProgresso = {
      v: 1,
      respostas: respostasIniciais,
      ultimaVista: { todas: LIMITE_QUESTOES },
      ocultarResolvidas: false,
    };
    localStorage.setItem(CHAVE_PROGRESSO, JSON.stringify(semente));

    // Re-responde a questao #1 agora (passa a ser recente, nao deve ser descartada)
    registrarResposta(1, 'B', false, 'todas');
    // Responde duas questoes novas alem do limite (20001 e 20002): deve descartar #2 e #3 (as mais antigas)
    registrarResposta(20_001, 'C', true, 'todas');
    registrarResposta(20_002, 'D', true, 'todas');

    const lido = lerProgresso();
    expect(Object.keys(lido.respostas)).toHaveLength(LIMITE_QUESTOES);
    expect(lido.respostas['1']).toBeDefined();
    expect(lido.respostas['1'].alternativa).toBe('B');
    expect(lido.respostas['2']).toBeUndefined();
    expect(lido.respostas['3']).toBeUndefined();
    expect(lido.respostas['4']).toBeDefined();
    expect(lido.respostas['20001']).toBeDefined();
    expect(lido.respostas['20002']).toBeDefined();
  });

  it('ignora JSON corrompido ou versão desconhecida no localStorage', () => {
    localStorage.setItem(CHAVE_PROGRESSO, '{json-invalido');
    expect(lerProgresso().respostas).toEqual({});

    localStorage.setItem(CHAVE_PROGRESSO, JSON.stringify({ v: 2, respostas: { '1': { alternativa: 'A', acertou: true, data: 'x' } } }));
    expect(lerProgresso().respostas).toEqual({});

    localStorage.setItem(
      CHAVE_PROGRESSO,
      JSON.stringify({
        v: 1,
        respostas: {
          '1': { alternativa: 'A', acertou: true, data: '2026-01-01T00:00:00.000Z' },
          invalida: { alternativa: '', acertou: 'sim' },
        },
        ultimaVista: { portugues: 10, ruim: -3, texto: 'abc' },
        ocultarResolvidas: 'nao-booleano',
      }),
    );

    const saneado = lerProgresso();
    expect(Object.keys(saneado.respostas)).toEqual(['1']);
    expect(saneado.ultimaVista).toEqual({ portugues: 10 });
    expect(saneado.ocultarResolvidas).toBe(false);
  });

  it('funciona sem lançar erro quando o localStorage está bloqueado ou indisponível (RN5, RNF06)', () => {
    const storageBloqueado: Storage = {
      length: 0,
      clear() {
        throw new DOMException('Blocked', 'SecurityError');
      },
      getItem() {
        throw new DOMException('Blocked', 'SecurityError');
      },
      key() {
        return null;
      },
      removeItem() {
        throw new DOMException('Blocked', 'SecurityError');
      },
      setItem() {
        throw new DOMException('Blocked', 'SecurityError');
      },
    };

    expect(armazenamentoDisponivel(storageBloqueado)).toBe(false);
    expect(armazenamentoDisponivel(null)).toBe(false);
    expect(() => registrarResposta(10, 'A', true, 'portugues', storageBloqueado)).not.toThrow();
    expect(() => registrarUltimaVista(10, 'portugues', storageBloqueado)).not.toThrow();
    expect(() => limparUltimaVista('portugues', storageBloqueado)).not.toThrow();
    expect(() => definirOcultarResolvidas(true, storageBloqueado)).not.toThrow();
    expect(() => limparProgresso(storageBloqueado)).not.toThrow();
    expect(lerProgresso(storageBloqueado)).toEqual({
      v: 1,
      respostas: {},
      ultimaVista: {},
      ocultarResolvidas: false,
    });
    expect(consultarResposta(10, storageBloqueado)).toBeNull();
    expect(obterUltimaVista('portugues', storageBloqueado)).toBeNull();
    expect(lerOcultarResolvidas(storageBloqueado)).toBe(false);
  });

  it('normaliza filtros e monta URLs e parâmetros de /api/questoes/ids', () => {
    expect(normalizarChaveFiltro(null)).toBe('todas');
    expect(normalizarChaveFiltro('  ')).toBe('todas');
    expect(normalizarChaveFiltro('/portugues/crase/')).toBe('portugues/crase');

    expect(parametrosFiltroIds('todas')).toEqual({});
    expect(parametrosFiltroIds('portugues')).toEqual({ materia: 'portugues' });
    expect(parametrosFiltroIds('portugues/crase')).toEqual({ materia: 'portugues', assunto: 'crase' });

    expect(urlQuestaoPorId(42, 'todas')).toBe('/questao/42');
    expect(urlQuestaoPorId(42, 'portugues/crase')).toBe('/questao/42?de=portugues/crase');

    expect(urlListagemDoFiltro(null)).toBe('/questoes');
    expect(urlListagemDoFiltro('portugues')).toBe('/questoes/portugues');
    expect(urlListagemDoFiltro('portugues/crase')).toBe('/questoes/portugues/crase');
  });

  it('escolhe a primeira questão não resolvida depois da última vista em uma lista de ids (RN3, RN4)', () => {
    const ids = [10, 12, 15, 20, 25];
    expect(escolherProximaNaoResolvida(ids, new Set([10, 15]), 10)).toBe(12);
    expect(escolherProximaNaoResolvida(ids, new Set([10, 12, 15]), 10)).toBe(20);
    expect(escolherProximaNaoResolvida(ids, { '10': true, '12': true, '15': true, '20': true, '25': true }, 10)).toBeNull();
    expect(escolherProximaNaoResolvida(ids, {}, null)).toBe(10);
  });

  it('busca a próxima não resolvida em /api/questoes/ids paginando pelo cursor depoisDe (RN3, US03)', async () => {
    // Visitante ja resolveu 1, 2, 4, 5 e a ultima vista em portugues/sintaxe foi 2
    registrarResposta(1, 'A', true, 'portugues/sintaxe');
    registrarResposta(4, 'B', false, 'portugues/sintaxe');
    registrarResposta(5, 'C', true, 'portugues/sintaxe');
    registrarUltimaVista(2, 'portugues/sintaxe');

    const chamadas: string[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      chamadas.push(url);
      if (url === '/api/questoes/ids?materia=portugues&assunto=sintaxe&depoisDe=2') {
        // Primeira pagina depois de 2: 4 e 5 ja estao resolvidas, entao precisa seguir o cursor 5
        return new Response(JSON.stringify({ itens: [4, 5], proximoCursor: 5 }), { status: 200 });
      }
      if (url === '/api/questoes/ids?materia=portugues&assunto=sintaxe&depoisDe=5') {
        // Segunda pagina: 8 ainda nao foi resolvida
        return new Response(JSON.stringify({ itens: [8, 9], proximoCursor: null }), { status: 200 });
      }
      return new Response(JSON.stringify({ itens: [], proximoCursor: null }), { status: 200 });
    }) as unknown as typeof fetch;

    const resultado = await buscarProximaNaoResolvida('portugues/sintaxe', { fetchFn: fetchMock });

    expect(chamadas).toEqual([
      '/api/questoes/ids?materia=portugues&assunto=sintaxe&depoisDe=2',
      '/api/questoes/ids?materia=portugues&assunto=sintaxe&depoisDe=5',
    ]);
    expect(resultado).toEqual({
      status: 'encontrada',
      idQuestao: 8,
      url: '/questao/8?de=portugues/sintaxe',
      filtro: 'portugues/sintaxe',
    });
  });

  it('volta ao início do filtro e oferece reiniciar quando não há não resolvida após a última vista (RN3)', async () => {
    // Questoes do filtro: [3, 7, 12]. Visitante resolveu a 12 (ultima vista = 12), mas nao resolveu a 3
    registrarResposta(12, 'A', true, 'portugues');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === '/api/questoes/ids?materia=portugues&depoisDe=12') {
        return new Response(JSON.stringify({ itens: [], proximoCursor: null }), { status: 200 });
      }
      if (url === '/api/questoes/ids?materia=portugues') {
        return new Response(JSON.stringify({ itens: [3, 7, 12], proximoCursor: null }), { status: 200 });
      }
      return new Response(JSON.stringify({ itens: [], proximoCursor: null }), { status: 200 });
    }) as unknown as typeof fetch;

    const resultado = await buscarProximaNaoResolvida('portugues', { fetchFn: fetchMock });

    expect(resultado).toEqual({
      status: 'fim_do_filtro',
      primeiroId: 3,
      primeiraNaoResolvidaDoInicio: 3,
      urlReiniciar: '/questao/3?de=portugues',
      urlInicioFiltro: '/questoes/portugues',
      filtro: 'portugues',
    });
  });

  it('indica fim_do_filtro apontando para a primeira questão quando todas do filtro já foram resolvidas (RN3)', async () => {
    registrarResposta(3, 'A', true, 'todas');
    registrarResposta(7, 'B', true, 'todas');
    limparUltimaVista('todas'); // Mesmo com ultimaVista=0, todas ja foram resolvidas

    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ itens: [3, 7], proximoCursor: null }), { status: 200 });
    }) as unknown as typeof fetch;

    const resultado = await buscarProximaNaoResolvida('todas', { fetchFn: fetchMock });

    expect(resultado).toEqual({
      status: 'fim_do_filtro',
      primeiroId: 3,
      primeiraNaoResolvidaDoInicio: null,
      urlReiniciar: '/questao/3',
      urlInicioFiltro: '/questoes',
      filtro: 'todas',
    });
  });

  it('retorna status vazio quando o filtro não possui questões ou a requisição falha', async () => {
    const fetchFalha = vi.fn(async () => new Response(null, { status: 500 })) as unknown as typeof fetch;
    const r1 = await buscarProximaNaoResolvida('portugues', { fetchFn: fetchFalha });
    expect(r1).toEqual({ status: 'vazio', filtro: 'portugues' });
  });

  it('para em maxPaginas sem varrer o filtro inteiro e sem voltar ao início', async () => {
    for (const id of [11, 12, 13, 14, 15, 16]) registrarResposta(id, 'A', true, 'portugues');
    const fetchPaginado = vi.fn(async (url: string) => {
      const depoisDe = Number(new URL(url, 'http://x').searchParams.get('depoisDe') ?? 10);
      return new Response(JSON.stringify({ itens: [depoisDe + 1, depoisDe + 2], proximoCursor: depoisDe + 2 }));
    }) as unknown as typeof fetch;

    const r = await buscarProximaNaoResolvida('portugues', {
      fetchFn: fetchPaginado,
      depoisDe: 10,
      maxPaginas: 2,
      voltarAoInicio: false,
    });

    expect(r).toEqual({ status: 'limite_atingido', filtro: 'portugues' });
    expect(fetchPaginado).toHaveBeenCalledTimes(2);
  });

  it('com voltarAoInicio false devolve sem_proxima e não consulta o início do filtro', async () => {
    registrarResposta(30, 'A', true, 'portugues');
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ itens: [30], proximoCursor: null })),
    ) as unknown as typeof fetch;

    const r = await buscarProximaNaoResolvida('portugues', {
      fetchFn: fetchMock,
      depoisDe: 20,
      voltarAoInicio: false,
    });

    expect(r).toEqual({ status: 'sem_proxima', filtro: 'portugues' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  describe('Caderno de erros (RF11 RN1–RN2, US04)', () => {
    const questoesExemplo: QuestaoCaderno[] = [
      {
        id: 25,
        slug: 'principios-administrativos',
        slugMateria: 'direito-administrativo',
        nomeMateria: 'Direito Administrativo',
        nomeAssunto: 'Princípios',
        fonte: 'FGV · TJ · 2025 · Técnico',
        resumoEnunciado: 'Não é princípio expresso no caput do art. 37...',
        anulada: false,
        desatualizada: false,
      },
      {
        id: 10,
        slug: 'concordancia-verbal',
        slugMateria: 'portugues',
        nomeMateria: 'Português',
        nomeAssunto: 'Concordância verbal',
        fonte: 'CEBRASPE · PRF · 2024 · Policial',
        resumoEnunciado: 'Assinale a frase correta quanto à concordância verbal.',
        anulada: false,
        desatualizada: false,
      },
      {
        id: 18,
        slug: 'uso-da-crase',
        slugMateria: 'portugues',
        nomeMateria: 'Português',
        nomeAssunto: 'Crase',
        fonte: 'Questão autoral',
        resumoEnunciado: 'Complete a frase com crase.',
        anulada: false,
        desatualizada: false,
      },
    ];

    it('adiciona questão ao caderno sempre que o visitante erra e a remove quando acerta ao refazer (RF11 RN1)', () => {
      expect(contarCadernoDeErros()).toBe(0);
      expect(listarIdsCadernoDeErros()).toEqual([]);
      expect(estaNoCadernoDeErros(10)).toBe(false);

      // Erra a #25 e a #10 e acerta a #15 -> entram apenas #10 e #25 em ordem crescente de id
      registrarResposta(25, 'A', false, 'direito-administrativo');
      registrarResposta(15, 'B', true, 'portugues');
      registrarResposta(10, 'D', false, 'portugues');

      expect(estaNoCadernoDeErros(10)).toBe(true);
      expect(estaNoCadernoDeErros(25)).toBe(true);
      expect(estaNoCadernoDeErros(15)).toBe(false);
      expect(contarCadernoDeErros()).toBe(2);
      expect(listarIdsCadernoDeErros()).toEqual([10, 25]);

      // Refaz a #10 e erra novamente -> continua no caderno
      registrarResposta(10, 'C', false, 'caderno');
      expect(estaNoCadernoDeErros(10)).toBe(true);
      expect(listarIdsCadernoDeErros()).toEqual([10, 25]);

      // Refaz a #10 e acerta -> sai do caderno (RF11 RN1, US04)
      registrarResposta(10, 'B', true, 'caderno');
      expect(estaNoCadernoDeErros(10)).toBe(false);
      expect(estaNoCadernoDeErros(25)).toBe(true);
      expect(contarCadernoDeErros()).toBe(1);
      expect(listarIdsCadernoDeErros()).toEqual([25]);

      // Se errar a #15 que antes estava certa -> entra no caderno (RF11 RN1)
      registrarResposta(15, 'A', false, 'portugues');
      expect(estaNoCadernoDeErros(15)).toBe(true);
      expect(listarIdsCadernoDeErros()).toEqual([15, 25]);
    });

    it('filtra o caderno por matéria, lista matérias com contagens e escolhe a próxima em sequência (RF11 RN2)', () => {
      const materias = listarMateriasDoCaderno(questoesExemplo);
      expect(materias).toEqual([
        { slug: 'direito-administrativo', nome: 'Direito Administrativo', quantidade: 1 },
        { slug: 'portugues', nome: 'Português', quantidade: 2 },
      ]);

      // Sem filtro ("todas"): ordena por id crescente [10, 18, 25]
      expect(filtrarCadernoPorMateria(questoesExemplo, null).map((q) => q.id)).toEqual([10, 18, 25]);
      expect(filtrarCadernoPorMateria(questoesExemplo, 'todas').map((q) => q.id)).toEqual([10, 18, 25]);

      // Filtrado por portugues: apenas [10, 18]
      expect(filtrarCadernoPorMateria(questoesExemplo, 'portugues').map((q) => q.id)).toEqual([10, 18]);

      // Percorrer em sequencia (sem filtro e com filtro de materia)
      expect(escolherProximaDoCaderno(questoesExemplo, null, null)?.id).toBe(10);
      expect(escolherProximaDoCaderno(questoesExemplo, 10, null)?.id).toBe(18);
      expect(escolherProximaDoCaderno(questoesExemplo, 18, null)?.id).toBe(25);
      expect(escolherProximaDoCaderno(questoesExemplo, 25, null)).toBeNull();

      expect(escolherProximaDoCaderno(questoesExemplo, 10, 'portugues')?.id).toBe(18);
      expect(escolherProximaDoCaderno(questoesExemplo, 18, 'portugues')).toBeNull();
    });

    it('monta URLs reais (/questao/{id}/{slug}?de=caderno) e salva/limpa filtro de matéria do caderno', () => {
      expect(urlQuestaoCaderno(10, 'concordancia-verbal')).toBe('/questao/10/concordancia-verbal?de=caderno');
      expect(urlQuestaoCaderno(10, '')).toBe('/questao/10?de=caderno');

      expect(ehFiltroCaderno('caderno')).toBe(true);
      expect(ehFiltroCaderno('/caderno/portugues/')).toBe(true);
      expect(ehFiltroCaderno('portugues/crase')).toBe(false);
      expect(ehFiltroCaderno(null)).toBe(false);

      expect(extrairMateriaFiltroCaderno('caderno')).toBeNull();
      expect(extrairMateriaFiltroCaderno('caderno/portugues')).toBe('portugues');
      expect(extrairMateriaFiltroCaderno('portugues')).toBeNull();

      expect(lerMateriaCaderno()).toBeNull();
      definirMateriaCaderno('portugues');
      expect(lerMateriaCaderno()).toBe('portugues');
      expect(localStorage.getItem(CHAVE_MATERIA_CADERNO)).toBe('portugues');

      definirMateriaCaderno('todas');
      expect(lerMateriaCaderno()).toBeNull();

      definirMateriaCaderno('direito-administrativo');
      limparProgresso();
      expect(lerMateriaCaderno()).toBeNull();
    });

    it('busca dados mínimos das questões erradas em /api/questoes/resumos e calcula a próxima questão do caderno', async () => {
      registrarResposta(25, 'A', false, 'direito-administrativo');
      registrarResposta(10, 'D', false, 'portugues');
      registrarResposta(18, 'C', false, 'portugues');
      registrarResposta(12, 'B', true, 'portugues'); // acertou -> nao vai para a requisicao

      const chamadas: string[] = [];
      const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        chamadas.push(url);
        return new Response(JSON.stringify(questoesExemplo), { status: 200 });
      }) as unknown as typeof fetch;

      const carregadas = await buscarQuestoesCadernoDeErros({ fetchFn: fetchMock });
      expect(chamadas).toEqual(['/api/questoes/resumos?ids=10,18,25']);
      expect(carregadas.map((q) => q.id)).toEqual([10, 18, 25]);

      // Proxima do caderno depois da #18 sem filtro de materia -> #25
      const proximaGeral = await buscarProximaQuestaoCaderno(18, { fetchFn: fetchMock });
      expect(proximaGeral).toEqual({
        questao: questoesExemplo[0],
        url: '/questao/25/principios-administrativos?de=caderno',
      });

      // Com filtro de materia "portugues" ativo, apos a #18 nao ha mais questoes de portugues no caderno
      definirMateriaCaderno('portugues');
      const proximaPortugues = await buscarProximaQuestaoCaderno(18, { fetchFn: fetchMock });
      expect(proximaPortugues).toBeNull();

      // Mas apos a #10 em "portugues", a proxima e a #18
      const proximaPortuguesDe10 = await buscarProximaQuestaoCaderno(10, { fetchFn: fetchMock });
      expect(proximaPortuguesDe10?.url).toBe('/questao/18/uso-da-crase?de=caderno');
    });
  });
});
