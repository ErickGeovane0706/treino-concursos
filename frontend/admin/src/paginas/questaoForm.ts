import type { DadosQuestao, Letra, OrigemQuestao, Questao, TipoQuestao } from '../api';

// Logica pura do formulario de questao (US09), separada da tela para testar.

export interface EstadoQuestao {
  idMateria: string;
  idAssunto: string;
  tipo: TipoQuestao;
  origem: OrigemQuestao;
  idProva: string;
  enunciado: string;
  comentario: string;
  /** Multipla escolha: 4 (A a D) ou 5 (A a E) alternativas (RF04 RN2). */
  quantidade: 4 | 5;
  textos: Record<Letra, string>;
  correta: Letra | '';
}

/** Classificacao mantida no "Salvar e cadastrar outra". */
export type Classificacao = Pick<EstadoQuestao, 'idMateria' | 'idAssunto' | 'tipo' | 'origem' | 'idProva' | 'quantidade'>;

const TEXTOS_VAZIOS: Record<Letra, string> = { A: '', B: '', C: '', D: '', E: '' };

export function estadoInicial(classificacao?: Partial<Classificacao>): EstadoQuestao {
  return {
    idMateria: '',
    idAssunto: '',
    tipo: 'MULTIPLA_ESCOLHA',
    origem: 'PROVA',
    idProva: '',
    quantidade: 5,
    ...classificacao,
    enunciado: '',
    comentario: '',
    textos: { ...TEXTOS_VAZIOS },
    correta: '',
  };
}

export function classificacaoDe(estado: EstadoQuestao): Classificacao {
  const { idMateria, idAssunto, tipo, origem, idProva, quantidade } = estado;
  return { idMateria, idAssunto, tipo, origem, idProva, quantidade };
}

/** Letras das alternativas: A a D/E na multipla escolha; C e E no certo/errado. */
export function letrasDo(tipo: TipoQuestao, quantidade: 4 | 5): Letra[] {
  if (tipo === 'CERTO_ERRADO') return ['C', 'E'];
  return (['A', 'B', 'C', 'D', 'E'] as Letra[]).slice(0, quantidade);
}

export function rotuloDa(tipo: TipoQuestao, letra: Letra): string {
  if (tipo === 'CERTO_ERRADO') return letra === 'C' ? 'Certo' : 'Errado';
  return letra;
}

export function deQuestao(q: Questao): EstadoQuestao {
  const textos = { ...TEXTOS_VAZIOS };
  for (const a of q.alternativas) textos[a.letra] = q.tipo === 'CERTO_ERRADO' ? '' : a.texto;
  return {
    idMateria: String(q.idMateria),
    idAssunto: q.idAssunto === null ? '' : String(q.idAssunto),
    tipo: q.tipo,
    origem: q.origem,
    idProva: q.idProva === null ? '' : String(q.idProva),
    enunciado: q.enunciado,
    comentario: q.comentario ?? '',
    quantidade: q.tipo === 'MULTIPLA_ESCOLHA' && q.alternativas.length === 4 ? 4 : 5,
    textos,
    correta: q.alternativas.find((a) => a.correta)?.letra ?? '',
  };
}

export function paraDados(e: EstadoQuestao): DadosQuestao {
  return {
    idMateria: Number(e.idMateria),
    idAssunto: e.idAssunto ? Number(e.idAssunto) : null,
    tipo: e.tipo,
    origem: e.origem,
    idProva: e.origem === 'PROVA' && e.idProva ? Number(e.idProva) : null,
    enunciado: e.enunciado.trim(),
    comentario: e.comentario.trim() ? e.comentario.trim() : null,
    alternativas: letrasDo(e.tipo, e.quantidade).map((letra) => ({
      letra,
      texto: e.tipo === 'CERTO_ERRADO' ? null : e.textos[letra].trim(),
      correta: e.correta === letra,
    })),
  };
}

/** Um documento so para a previa: enunciado, alternativas (correta marcada) e comentario. */
export function montarPrevia(e: EstadoQuestao): string {
  const alternativas = letrasDo(e.tipo, e.quantidade)
    .map((letra) => {
      const texto = e.tipo === 'CERTO_ERRADO' ? '' : ` ${e.textos[letra]}`;
      return `**${rotuloDa(e.tipo, letra)})**${texto}${e.correta === letra ? ' ✔' : ''}`;
    })
    .join('\n\n');
  const comentario = e.comentario.trim() ? e.comentario : '_(sem comentário)_';
  return `${e.enunciado}\n\n---\n\n${alternativas}\n\n---\n\n**Comentário**\n\n${comentario}`;
}

/**
 * Atalho Alt+1..5 marca a correta (A a E; no certo/errado, 1 = Certo e 2 = Errado).
 * Usa o codigo da tecla, pois o Alt muda o caractere em alguns teclados.
 */
export function letraDoAtalho(
  evento: { altKey: boolean; ctrlKey: boolean; metaKey: boolean; code: string },
  letras: Letra[],
): Letra | null {
  if (!evento.altKey || evento.ctrlKey || evento.metaKey) return null;
  const numero = /^(?:Digit|Numpad)([1-5])$/.exec(evento.code);
  if (!numero) return null;
  return letras[Number(numero[1]) - 1] ?? null;
}
