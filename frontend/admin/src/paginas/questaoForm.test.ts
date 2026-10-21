import { describe, expect, it } from 'vitest';
import type { Questao } from '../api';
import { deQuestao, estadoInicial, letraDoAtalho, letrasDo, montarPrevia, paraDados } from './questaoForm';

const tecla = (code: string, extra: Partial<{ altKey: boolean; ctrlKey: boolean; metaKey: boolean }> = {}) => ({
  altKey: true,
  ctrlKey: false,
  metaKey: false,
  code,
  ...extra,
});

describe('formulário de questão', () => {
  it('usa A a D/E na múltipla escolha e C e E no certo/errado (RF04 RN2)', () => {
    expect(letrasDo('MULTIPLA_ESCOLHA', 5)).toEqual(['A', 'B', 'C', 'D', 'E']);
    expect(letrasDo('MULTIPLA_ESCOLHA', 4)).toEqual(['A', 'B', 'C', 'D']);
    expect(letrasDo('CERTO_ERRADO', 5)).toEqual(['C', 'E']);
  });

  it('monta o JSON da API só com as letras do tipo e sem prova na autoral', () => {
    const estado = {
      ...estadoInicial(),
      idMateria: '3',
      origem: 'AUTORAL' as const,
      idProva: '9',
      quantidade: 4 as const,
      enunciado: '  Assinale.  ',
      comentario: '   ',
      textos: { A: 'um', B: 'dois', C: 'três', D: 'quatro', E: 'sobrou' },
      correta: 'B' as const,
    };

    expect(paraDados(estado)).toEqual({
      idMateria: 3,
      idAssunto: null,
      tipo: 'MULTIPLA_ESCOLHA',
      origem: 'AUTORAL',
      idProva: null,
      enunciado: 'Assinale.',
      comentario: null,
      alternativas: [
        { letra: 'A', texto: 'um', correta: false },
        { letra: 'B', texto: 'dois', correta: true },
        { letra: 'C', texto: 'três', correta: false },
        { letra: 'D', texto: 'quatro', correta: false },
      ],
    });
  });

  it('no certo/errado envia C e E sem texto', () => {
    const dados = paraDados({ ...estadoInicial(), idMateria: '1', tipo: 'CERTO_ERRADO', correta: 'E' });
    expect(dados.alternativas).toEqual([
      { letra: 'C', texto: null, correta: false },
      { letra: 'E', texto: null, correta: true },
    ]);
  });

  it('mantém a classificação e limpa os textos no "cadastrar outra"', () => {
    const estado = estadoInicial({ idMateria: '2', idProva: '7', tipo: 'CERTO_ERRADO' });
    expect(estado).toMatchObject({ idMateria: '2', idProva: '7', tipo: 'CERTO_ERRADO', enunciado: '', correta: '' });
  });

  it('carrega uma questão de 4 alternativas da API', () => {
    const questao = {
      idMateria: 1,
      idAssunto: null,
      idProva: 5,
      tipo: 'MULTIPLA_ESCOLHA',
      origem: 'PROVA',
      enunciado: 'E',
      comentario: null,
      alternativas: ['A', 'B', 'C', 'D'].map((letra, i) => ({ id: i, letra, texto: `t${letra}`, correta: letra === 'C', totalMarcacoes: 0 })),
    } as unknown as Questao;

    expect(deQuestao(questao)).toMatchObject({ idProva: '5', quantidade: 4, correta: 'C', comentario: '' });
  });

  it('marca a correta na prévia', () => {
    const previa = montarPrevia({
      ...estadoInicial(),
      enunciado: 'Enunciado',
      textos: { A: 'um', B: 'dois', C: '', D: '', E: '' },
      correta: 'B',
    });
    expect(previa).toContain('**B)** dois ✔');
    expect(previa).toContain('_(sem comentário)_');
  });

  it('Alt+1..5 vira a letra; outras combinações são ignoradas', () => {
    const letras = letrasDo('MULTIPLA_ESCOLHA', 5);
    expect(letraDoAtalho(tecla('Digit3'), letras)).toBe('C');
    expect(letraDoAtalho(tecla('Numpad5'), letras)).toBe('E');
    expect(letraDoAtalho(tecla('Digit5'), letrasDo('MULTIPLA_ESCOLHA', 4))).toBeNull();
    expect(letraDoAtalho(tecla('Digit2'), letrasDo('CERTO_ERRADO', 5))).toBe('E');
    expect(letraDoAtalho(tecla('Digit1', { altKey: false }), letras)).toBeNull();
    // Ctrl+Alt e o AltGr no Windows: nao e atalho
    expect(letraDoAtalho(tecla('Digit1', { ctrlKey: true }), letras)).toBeNull();
  });
});
