// Logica da lista de questoes do simulado no painel (RF14 RN1 e RN2), sem React,
// para testar sem renderizar.

export const MINIMO_QUESTOES = 5;
export const MAXIMO_QUESTOES = 100;

/** Item da lista em montagem: o que o painel precisa mostrar de cada questao. */
export interface ItemSimulado {
  id: number;
  trecho: string;
  nomeMateria: string;
  elegivel: boolean;
  motivo: string | null;
}

/** RF14 RN2: nao repete; RF14 RN1: no maximo 100. Devolve a mesma lista se nao couber. */
export function adicionar(lista: ItemSimulado[], item: ItemSimulado): ItemSimulado[] {
  if (lista.some((i) => i.id === item.id) || lista.length >= MAXIMO_QUESTOES) return lista;
  return [...lista, item];
}

export function remover(lista: ItemSimulado[], id: number): ItemSimulado[] {
  return lista.filter((i) => i.id !== id);
}

/** Move o item da posicao `de` para a posicao `para` (arrastar ou botoes subir/descer). */
export function mover(lista: ItemSimulado[], de: number, para: number): ItemSimulado[] {
  if (de === para || de < 0 || para < 0 || de >= lista.length || para >= lista.length) return lista;
  const nova = [...lista];
  const [item] = nova.splice(de, 1);
  nova.splice(para, 0, item);
  return nova;
}

/** Motivo de uma questao nao poder entrar no simulado, ou null se ela pode (RF14 RN1). */
export function motivoInelegivel(q: { status: string; anulada: boolean; desatualizada: boolean }): string | null {
  if (q.status === 'ARQUIVADA') return 'arquivada';
  if (q.status !== 'PUBLICADA') return 'não publicada';
  if (q.anulada) return 'anulada';
  if (q.desatualizada) return 'desatualizada';
  return null;
}

/** Avisos antes de publicar: o servidor valida de novo, mas o editor ve o problema antes. */
export function pendenciasParaPublicar(lista: ItemSimulado[]): string[] {
  const pendencias: string[] = [];
  if (lista.length < MINIMO_QUESTOES) {
    pendencias.push(`Faltam ${MINIMO_QUESTOES - lista.length} questões para o mínimo de ${MINIMO_QUESTOES}.`);
  }
  const inelegiveis = lista.filter((i) => !i.elegivel);
  if (inelegiveis.length > 0) {
    pendencias.push(`Remova as questões que não podem ficar no simulado: ${inelegiveis.map((i) => `#${i.id}`).join(', ')}.`);
  }
  return pendencias;
}
