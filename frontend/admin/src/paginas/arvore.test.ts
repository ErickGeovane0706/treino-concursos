import { describe, expect, it } from 'vitest';
import type { Assunto } from '../api';
import { ordenarComoArvore } from './arvore';

function assunto(id: number, nome: string, idAssuntoPai: number | null, nivel: number): Assunto {
  return { id, idMateria: 1, idAssuntoPai, nome, slug: nome.toLowerCase(), ativo: true, nivel };
}

describe('ordenarComoArvore', () => {
  it('coloca cada pai seguido dos filhos, em ordem alfabética', () => {
    const lista = [
      assunto(1, 'Pontuação', null, 1),
      assunto(2, 'Concordância', null, 1),
      assunto(3, 'Verbal', 2, 2),
      assunto(4, 'Nominal', 2, 2),
      assunto(5, 'Verbo haver', 3, 3),
    ];

    expect(ordenarComoArvore(lista).map((a) => a.nome)).toEqual([
      'Concordância',
      'Nominal',
      'Verbal',
      'Verbo haver',
      'Pontuação',
    ]);
  });

  it('trata como raiz o filho cujo pai não veio na lista', () => {
    expect(ordenarComoArvore([assunto(9, 'Órfão', 99, 2)]).map((a) => a.id)).toEqual([9]);
  });
});
