import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { finalizarSimulado, lerSimulado, marcarResposta } from '../progresso';
import {
  iniciarIlhasSimulado,
  IlhaSimuladoApresentacao,
  IlhaSimuladoQuestao,
  IlhaSimuladoResultado,
  lerDadosQuestaoSimulado,
  type DadosQuestaoSimulado,
} from './simulado';

const DADOS: DadosQuestaoSimulado = {
  slug: 'pt-1',
  id: 10,
  n: 1,
  total: 3,
  tempoMin: null,
  resultadoUrl: '/simulado/pt-1/resultado',
  primeiraUrl: '/simulado/pt-1/1',
  alternativas: ['A', 'B', 'C'].map((letra) => ({ letra, rotulo: letra, textoHtml: `<p>Texto ${letra}</p>` })),
};

const GABARITO = [
  { id: 10, materia: 'Português', correta: 'B', rotuloCorreta: 'B', trecho: '1. Crase' },
  { id: 11, materia: 'Português', correta: 'C', rotuloCorreta: 'C', trecho: '2. Vírgula' },
  { id: 12, materia: 'Matemática', correta: 'E', rotuloCorreta: 'E (Errado)', trecho: '3. Juros' },
];

describe('Ilhas do simulado (RF13, US05)', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useRealTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('lê os dados da página do servidor e não traz gabarito', () => {
    document.body.innerHTML = `
      <div data-ilha="simulado-questao" data-slug="pt-1" data-id="10" data-n="2" data-total="3" data-tempo="30"
           data-resultado-url="/simulado/pt-1/resultado" data-primeira-url="/simulado/pt-1/1">
        <button class="opt" data-letra="A"><span class="l">A</span><span class="opt-texto"><p>Um</p></span></button>
      </div>`;
    const dados = lerDadosQuestaoSimulado(document.querySelector('[data-ilha]')!);
    expect(dados).toMatchObject({ slug: 'pt-1', id: 10, n: 2, total: 3, tempoMin: 30 });
    expect(dados!.alternativas[0].textoHtml).toBe('<p>Um</p>');
    expect(dados).not.toHaveProperty('correta');
  });

  it('marca sem corrigir, troca a resposta, deixa em branco e guarda no navegador (RN1, RN2)', async () => {
    const user = userEvent.setup();
    render(<IlhaSimuladoQuestao dados={DADOS} />);

    await user.click(screen.getByRole('button', { name: /Texto B/ }));
    expect(screen.getByRole('button', { name: /Texto B/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('status')).toHaveTextContent('Resposta marcada: B');
    expect(document.querySelector('.opt.ok, .opt.bad')).toBeNull();

    await user.click(screen.getByRole('button', { name: /Texto C/ }));
    expect(lerSimulado('pt-1').respostas).toEqual({ 10: 'C' });

    await user.click(screen.getByRole('button', { name: 'Deixar em branco' }));
    expect(lerSimulado('pt-1').respostas).toEqual({});
    expect(screen.getByRole('status')).toHaveTextContent('em branco conta como erro');
  });

  it('ao recarregar, mostra a resposta já marcada', () => {
    marcarResposta('pt-1', 10, 'A');
    render(<IlhaSimuladoQuestao dados={DADOS} />);
    expect(screen.getByRole('button', { name: /Texto A/ })).toHaveClass('sel');
  });

  it('depois de finalizado, trava as alternativas e oferece ver o resultado', () => {
    marcarResposta('pt-1', 10, 'A');
    finalizarSimulado('pt-1');
    render(<IlhaSimuladoQuestao dados={DADOS} />);
    expect(screen.getByRole('button', { name: /Texto A/ })).toBeDisabled();
    expect(screen.getByRole('link', { name: 'Ver resultado' })).toHaveAttribute('href', '/simulado/pt-1/resultado');
  });

  it('mostra o cronômetro e avisa quando o tempo acaba, sem finalizar (RN5)', () => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(new Date('2026-11-11T10:00:00'));
    render(<IlhaSimuladoQuestao dados={{ ...DADOS, tempoMin: 1 }} />);
    expect(screen.getByLabelText('Tempo restante')).toHaveTextContent('01:00');
    expect(screen.queryByRole('alert')).toBeNull();

    act(() => {
      vi.advanceTimersByTime(61_000);
    });
    expect(screen.getByRole('alert')).toHaveTextContent('O tempo sugerido acabou');
    expect(lerSimulado('pt-1').finalizadoEm).toBeNull();
    expect(screen.getByRole('button', { name: /Texto A/ })).toBeEnabled();
  });

  it('calcula o resultado, mostra "em branco" e o desempenho por matéria, e finaliza (RN3, RN4)', () => {
    marcarResposta('pt-1', 10, 'B');
    marcarResposta('pt-1', 12, 'A');
    render(<IlhaSimuladoResultado slug="pt-1" primeiraUrl="/simulado/pt-1/1" gabarito={GABARITO} />);

    expect(screen.getByText('33%')).toBeInTheDocument();
    expect(screen.getByText('1 acerto em 3 questões')).toBeInTheDocument();
    expect(screen.getByText('1/2')).toBeInTheDocument();
    expect(screen.getByText('0/1')).toBeInTheDocument();
    const situacoes = [...document.querySelectorAll('[data-situacao]')].map((li) => li.getAttribute('data-situacao'));
    expect(situacoes).toEqual(['certa', 'branco', 'errada']);
    expect(screen.getByText('Em branco')).toBeInTheDocument();
    expect(screen.getByText('E (Errado)')).toBeInTheDocument();
    expect(lerSimulado('pt-1').finalizadoEm).not.toBeNull();
  });

  it('sem respostas neste navegador, mostra só o gabarito e não finaliza', () => {
    render(<IlhaSimuladoResultado slug="pt-1" primeiraUrl="/simulado/pt-1/1" gabarito={GABARITO} />);
    expect(screen.getByText(/Não encontramos respostas/)).toBeInTheDocument();
    expect(screen.queryByText('Em branco')).toBeNull();
    expect(lerSimulado('pt-1').finalizadoEm).toBeNull();
  });

  it('refazer apaga o andamento e volta para a questão 1', async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    marcarResposta('pt-1', 10, 'B');
    render(<IlhaSimuladoResultado slug="pt-1" primeiraUrl="/simulado/pt-1/1" gabarito={GABARITO} />);

    await user.click(screen.getByRole('button', { name: 'Refazer simulado' }));
    expect(lerSimulado('pt-1').respostas).toEqual({});
    expect(assign).toHaveBeenCalledWith('/simulado/pt-1/1');
  });

  it('na apresentação, continua da primeira questão sem resposta ou mostra o resultado', () => {
    const { unmount } = render(<IlhaSimuladoApresentacao slug="pt-1" ids={[10, 11, 12]} resultadoUrl="/simulado/pt-1/resultado" />);
    expect(screen.getByRole('link', { name: 'Começar simulado' })).toHaveAttribute('href', '/simulado/pt-1/1');
    unmount();

    marcarResposta('pt-1', 10, 'B');
    const segundo = render(<IlhaSimuladoApresentacao slug="pt-1" ids={[10, 11, 12]} resultadoUrl="/simulado/pt-1/resultado" />);
    expect(screen.getByRole('link', { name: 'Continuar (questão 2)' })).toHaveAttribute('href', '/simulado/pt-1/2');
    segundo.unmount();

    finalizarSimulado('pt-1');
    render(<IlhaSimuladoApresentacao slug="pt-1" ids={[10, 11, 12]} resultadoUrl="/simulado/pt-1/resultado" />);
    expect(screen.getByRole('link', { name: 'Ver meu resultado' })).toBeInTheDocument();
  });

  it('monta as ilhas a partir do HTML do servidor', async () => {
    document.body.innerHTML = `
      <div data-ilha="simulado-resultado" data-slug="pt-1" data-primeira-url="/simulado/pt-1/1"
           data-comentarios-url="/simulado/pt-1/comentarios">
        <ol><li data-id="10" data-materia="Português" data-correta="B" data-rotulo-correta="B">
          <div class="rt"><span>1. Crase</span></div></li></ol>
      </div>`;
    marcarResposta('pt-1', 10, 'B');
    await act(async () => iniciarIlhasSimulado());
    expect(screen.getByText('100%')).toBeInTheDocument();
    expect(screen.getByText('1. Crase')).toBeInTheDocument();
    // RF15 RN1: comentarios em pagina separada, sem anuncio simulado no codigo (RN3)
    expect(screen.getByRole('link', { name: 'Ver comentários' })).toHaveAttribute('href', '/simulado/pt-1/comentarios');
    expect(document.body.textContent).not.toMatch(/Assistir anúncio|Liberar comentários/);
  });
});
