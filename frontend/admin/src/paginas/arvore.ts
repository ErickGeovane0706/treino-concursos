import type { Assunto } from '../api';

/**
 * Ordena os assuntos como arvore: cada pai seguido dos seus filhos, todos em
 * ordem alfabetica. Filhos cujo pai nao veio na lista entram como raiz.
 */
export function ordenarComoArvore(assuntos: Assunto[]): Assunto[] {
  const ids = new Set(assuntos.map((a) => a.id));
  const filhos = new Map<number | null, Assunto[]>();
  for (const a of assuntos) {
    const pai = a.idAssuntoPai !== null && ids.has(a.idAssuntoPai) ? a.idAssuntoPai : null;
    filhos.set(pai, [...(filhos.get(pai) ?? []), a]);
  }
  const resultado: Assunto[] = [];
  const visitar = (pai: number | null) => {
    const lista = [...(filhos.get(pai) ?? [])].sort((x, y) => x.nome.localeCompare(y.nome, 'pt-BR'));
    for (const a of lista) {
      resultado.push(a);
      visitar(a.id);
    }
  };
  visitar(null);
  return resultado;
}
