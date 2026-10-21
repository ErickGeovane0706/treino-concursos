// Progresso local do visitante no navegador (RF10, RNF06).
// Toda leitura e escrita fica em try/catch: em aba anonima ou com
// armazenamento bloqueado a resolucao continua funcionando.

export const CHAVE_PROGRESSO = 'treino-concursos:progresso';
const LIMITE_QUESTOES = 20_000;

export interface RegistroResposta {
  alternativa: string;
  acertou: boolean;
  data: string;
}

export interface DadosProgresso {
  v: 1;
  respostas: Record<string, RegistroResposta>;
  ultimaVista: Record<string, number>;
}

function vazio(): DadosProgresso {
  return { v: 1, respostas: {}, ultimaVista: {} };
}

export function lerProgresso(storage: Storage | undefined = globalThis.localStorage): DadosProgresso {
  try {
    const bruto = storage?.getItem(CHAVE_PROGRESSO);
    if (!bruto) return vazio();
    const lido = JSON.parse(bruto) as Partial<DadosProgresso>;
    if (lido?.v !== 1 || typeof lido.respostas !== 'object' || lido.respostas === null) {
      return vazio();
    }
    return {
      v: 1,
      respostas: { ...lido.respostas },
      ultimaVista: typeof lido.ultimaVista === 'object' && lido.ultimaVista !== null ? { ...lido.ultimaVista } : {},
    };
  } catch {
    return vazio();
  }
}

export function registrarResposta(
  idQuestao: number,
  alternativa: string,
  acertou: boolean,
  filtro: string | null = null,
  storage: Storage | undefined = globalThis.localStorage,
): void {
  try {
    if (!storage) return;
    const atual = lerProgresso(storage);
    const chave = String(idQuestao);
    delete atual.respostas[chave];
    atual.respostas[chave] = {
      alternativa,
      acertou,
      data: new Date().toISOString(),
    };
    const chaves = Object.keys(atual.respostas);
    if (chaves.length > LIMITE_QUESTOES) {
      for (const antiga of chaves.slice(0, chaves.length - LIMITE_QUESTOES)) {
        delete atual.respostas[antiga];
      }
    }
    const chaveFiltro = filtro && filtro.trim() ? filtro.trim() : 'todas';
    atual.ultimaVista[chaveFiltro] = idQuestao;
    storage.setItem(CHAVE_PROGRESSO, JSON.stringify(atual));
  } catch {
    // Sem localStorage: o treino funciona normalmente sem salvar (RF10 RN5, RNF06)
  }
}
