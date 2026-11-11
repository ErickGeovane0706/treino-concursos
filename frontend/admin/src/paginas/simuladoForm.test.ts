import { describe, expect, it } from 'vitest';
import {
  adicionar,
  MAXIMO_QUESTOES,
  motivoInelegivel,
  mover,
  pendenciasParaPublicar,
  remover,
  type ItemSimulado,
} from './simuladoForm';

const item = (id: number, elegivel = true): ItemSimulado => ({
  id,
  trecho: `Questão ${id}`,
  nomeMateria: 'Português',
  elegivel,
  motivo: elegivel ? null : 'anulada',
});

const ids = (lista: ItemSimulado[]) => lista.map((i) => i.id);

describe('Montagem da lista do simulado (RF14)', () => {
  it('adiciona no fim sem repetir a mesma questão (RN2)', () => {
    const lista = adicionar(adicionar([item(1)], item(2)), item(1));
    expect(ids(lista)).toEqual([1, 2]);
  });

  it('não passa de 100 questões (RN1)', () => {
    const cheia = Array.from({ length: MAXIMO_QUESTOES }, (_, i) => item(i + 1));
    expect(adicionar(cheia, item(500))).toBe(cheia);
  });

  it('move para cima, para baixo e ignora posições inválidas', () => {
    const lista = [item(1), item(2), item(3), item(4)];
    expect(ids(mover(lista, 3, 0))).toEqual([4, 1, 2, 3]);
    expect(ids(mover(lista, 0, 2))).toEqual([2, 3, 1, 4]);
    expect(mover(lista, 0, 9)).toBe(lista);
    expect(ids(lista)).toEqual([1, 2, 3, 4]);
  });

  it('remove pelo id', () => {
    expect(ids(remover([item(1), item(2), item(3)], 2))).toEqual([1, 3]);
  });

  it('diz por que a questão não pode entrar (RN1)', () => {
    const base = { status: 'PUBLICADA', anulada: false, desatualizada: false };
    expect(motivoInelegivel(base)).toBeNull();
    expect(motivoInelegivel({ ...base, status: 'RASCUNHO' })).toBe('não publicada');
    expect(motivoInelegivel({ ...base, status: 'ARQUIVADA' })).toBe('arquivada');
    expect(motivoInelegivel({ ...base, anulada: true })).toBe('anulada');
    expect(motivoInelegivel({ ...base, desatualizada: true })).toBe('desatualizada');
  });

  it('lista as pendências para publicar: mínimo de 5 e questões inelegíveis (RN1)', () => {
    expect(pendenciasParaPublicar([item(1), item(2), item(3, false)])).toEqual([
      'Faltam 2 questões para o mínimo de 5.',
      'Remova as questões que não podem ficar no simulado: #3.',
    ]);
    expect(pendenciasParaPublicar([1, 2, 3, 4, 5].map((i) => item(i)))).toEqual([]);
  });
});
