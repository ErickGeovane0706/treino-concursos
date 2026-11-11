import { beforeEach, describe, expect, it } from 'vitest';
import {
  calcularResultado,
  finalizarSimulado,
  formatarTempo,
  iniciarSimulado,
  lerSimulado,
  marcarResposta,
  PREFIXO_SIMULADO,
  primeiraSemResposta,
  reiniciarSimulado,
  tempoRestante,
  type ItemGabarito,
} from './simulado';

const GABARITO: ItemGabarito[] = [
  { id: 10, materia: 'Português', correta: 'B' },
  { id: 11, materia: 'Português', correta: 'C' },
  { id: 12, materia: 'Matemática', correta: 'A' },
  { id: 13, materia: 'Português', correta: 'E' },
];

describe('Andamento do simulado no navegador (RF13)', () => {
  beforeEach(() => localStorage.clear());

  it('guarda as respostas por simulado e sobrevive a recarregar a página (RN1)', () => {
    marcarResposta('pt-1', 10, 'B', 1000);
    marcarResposta('pt-1', 12, 'D', 2000);
    marcarResposta('outro', 10, 'A', 3000);

    // "Recarregar": um novo ler a partir do localStorage
    const estado = lerSimulado('pt-1');
    expect(estado.respostas).toEqual({ 10: 'B', 12: 'D' });
    expect(estado.iniciadoEm).toBe(1000);
    expect(lerSimulado('outro').respostas).toEqual({ 10: 'A' });
  });

  it('permite trocar a resposta e deixar em branco até finalizar, e depois não muda mais (RN2)', () => {
    marcarResposta('pt-1', 10, 'B');
    marcarResposta('pt-1', 10, 'C');
    expect(lerSimulado('pt-1').respostas).toEqual({ 10: 'C' });
    marcarResposta('pt-1', 10, null);
    expect(lerSimulado('pt-1').respostas).toEqual({});

    marcarResposta('pt-1', 11, 'A');
    finalizarSimulado('pt-1', 5000);
    marcarResposta('pt-1', 11, 'B');
    expect(lerSimulado('pt-1')).toMatchObject({ respostas: { 11: 'A' }, finalizadoEm: 5000 });
  });

  it('inicia o cronômetro uma vez só e refazer apaga o andamento', () => {
    expect(iniciarSimulado('pt-1', 1000).iniciadoEm).toBe(1000);
    expect(iniciarSimulado('pt-1', 9000).iniciadoEm).toBe(1000);
    reiniciarSimulado('pt-1');
    expect(lerSimulado('pt-1')).toEqual({ respostas: {}, iniciadoEm: null, finalizadoEm: null });
  });

  it('ignora dado corrompido ou inválido', () => {
    localStorage.setItem(PREFIXO_SIMULADO + 'pt-1', '{nao e json');
    expect(lerSimulado('pt-1').respostas).toEqual({});
    localStorage.setItem(
      PREFIXO_SIMULADO + 'pt-1',
      JSON.stringify({ respostas: { 10: 'B', 11: 'Z', x: 'A' }, iniciadoEm: 'ontem' }),
    );
    expect(lerSimulado('pt-1')).toEqual({ respostas: { 10: 'B' }, iniciadoEm: null, finalizadoEm: null });
  });

  it('funciona sem localStorage, sem lançar erro', () => {
    const quebrado = {
      getItem: () => {
        throw new Error('bloqueado');
      },
      setItem: () => {
        throw new Error('bloqueado');
      },
      removeItem: () => {
        throw new Error('bloqueado');
      },
    } as unknown as Storage;
    expect(marcarResposta('pt-1', 10, 'B', 1, quebrado).respostas).toEqual({ 10: 'B' });
    expect(() => reiniciarSimulado('pt-1', quebrado)).not.toThrow();
    expect(lerSimulado('pt-1', null).respostas).toEqual({});
  });
});

describe('Resultado do simulado (RF13 RN3-RN4)', () => {
  it('calcula percentual, acertos e desempenho por matéria; em branco conta como erro', () => {
    const resultado = calcularResultado(GABARITO, { 10: 'B', 11: 'A', 12: 'A' });

    expect(resultado.total).toBe(4);
    expect(resultado.acertos).toBe(2);
    expect(resultado.percentual).toBe(50);
    expect(resultado.itens.map((i) => i.situacao)).toEqual(['certa', 'errada', 'certa', 'branco']);
    expect(resultado.itens[3].resposta).toBeNull();
    expect(resultado.porMateria).toEqual([
      { materia: 'Português', acertos: 1, total: 3 },
      { materia: 'Matemática', acertos: 1, total: 1 },
    ]);
  });

  it('sem nenhuma resposta, tudo em branco e 0%', () => {
    const resultado = calcularResultado(GABARITO, {});
    expect(resultado.percentual).toBe(0);
    expect(resultado.itens.every((i) => i.situacao === 'branco')).toBe(true);
    expect(calcularResultado([], {}).percentual).toBe(0);
  });

  it('arredonda o percentual', () => {
    expect(calcularResultado(GABARITO.slice(0, 3), { 10: 'B' }).percentual).toBe(33);
  });

  it('acha a primeira questão sem resposta para continuar', () => {
    expect(primeiraSemResposta([10, 11, 12], { 10: 'B' })).toBe(2);
    expect(primeiraSemResposta([10, 11], { 10: 'B', 11: 'C' })).toBeNull();
  });
});

describe('Cronômetro (RF13 RN5)', () => {
  it('conta o tempo que falta e para em 00:00', () => {
    expect(tempoRestante(0, 30, 60_000)).toBe(29 * 60_000);
    expect(formatarTempo(29 * 60_000 + 5_500)).toBe('29:06');
    expect(formatarTempo(2 * 3600_000 + 61_000)).toBe('2:01:01');
    expect(formatarTempo(tempoRestante(0, 1, 120_000))).toBe('00:00');
  });
});
