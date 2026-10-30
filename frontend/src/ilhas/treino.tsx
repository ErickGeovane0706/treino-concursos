import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  armazenamentoDisponivel,
  buscarProximaNaoResolvida,
  buscarProximaQuestaoCaderno,
  ehFiltroCaderno,
  extrairMateriaFiltroCaderno,
  foiResolvida,
  lerOcultarResolvidas,
  registrarResposta,
  registrarUltimaVista,
} from '../progresso/progresso';
import { montarIlha } from './montar';

export type MotivoReporte =
  | 'GABARITO_ERRADO'
  | 'ENUNCIADO_COM_ERRO'
  | 'COMENTARIO_INCORRETO'
  | 'QUESTAO_DESATUALIZADA'
  | 'OUTRO';

export const MOTIVOS_REPORTE: { valor: MotivoReporte; texto: string }[] = [
  { valor: 'GABARITO_ERRADO', texto: 'Gabarito errado' },
  { valor: 'ENUNCIADO_COM_ERRO', texto: 'Enunciado com erro' },
  { valor: 'COMENTARIO_INCORRETO', texto: 'Comentário incorreto' },
  { valor: 'QUESTAO_DESATUALIZADA', texto: 'Questão desatualizada' },
  { valor: 'OUTRO', texto: 'Outro motivo' },
];

export interface AlternativaTreino {
  letra: string;
  rotulo: string;
  textoHtml: string;
}

export interface DadosTreino {
  id: number;
  correta: string;
  rotuloCorreta: string;
  proximaUrl: string | null;
  listagemUrl: string;
  filtro: string | null;
  alternativas: AlternativaTreino[];
  comentarioHtml: string;
}

export interface EstatisticasQuestao {
  disponivel: boolean;
  totalRespostas: number;
  percentualAcertos: number;
  alternativas: Record<string, number>;
}

const MINIMO_RESPOSTAS_ESTATISTICAS = 30;

/**
 * Le os dados que o Thymeleaf ja renderizou no HTML (ja sanitizados pelo
 * MarkdownService no servidor), antes da ilha React assumir o elemento.
 */
export function lerDadosTreino(elemento: HTMLElement): DadosTreino | null {
  const id = Number(elemento.dataset.id);
  const correta = elemento.dataset.correta?.trim() ?? '';
  if (!id || !correta) return null;

  const alternativas = [...elemento.querySelectorAll<HTMLButtonElement>('button.opt[data-letra]')].map((botao) => {
    const letra = botao.dataset.letra ?? '';
    const rotulo = botao.querySelector('.l')?.textContent?.trim() || letra;
    const textoHtml = botao.querySelector('.opt-texto')?.innerHTML ?? '';
    return { letra, rotulo, textoHtml };
  });
  if (alternativas.length === 0) return null;

  return {
    id,
    correta,
    rotuloCorreta: elemento.dataset.rotuloCorreta?.trim() || correta,
    proximaUrl: elemento.dataset.proximaUrl?.trim() || null,
    listagemUrl: elemento.dataset.listagemUrl?.trim() || '/questoes',
    filtro: elemento.dataset.filtro?.trim() || null,
    alternativas,
    comentarioHtml: elemento.querySelector('[data-comentario-html]')?.innerHTML ?? '',
  };
}

/** Paginas de /api/questoes/ids consultadas por visualizacao para pular resolvidas (RF10 RN4). */
export const MAX_PAGINAS_PROXIMA = 2;

function extrairIdDaUrlQuestao(url: string | null): number | null {
  if (!url) return null;
  const match = /^\/questao\/(\d+)(?:\/|\?|$)/.exec(url);
  return match ? Number(match[1]) : null;
}

function extrairEstatisticasValidas(json: unknown): EstatisticasQuestao | null {
  if (typeof json !== 'object' || json === null) return null;
  const obj = json as Record<string, unknown>;
  if (
    obj.disponivel !== true ||
    typeof obj.totalRespostas !== 'number' ||
    obj.totalRespostas < MINIMO_RESPOSTAS_ESTATISTICAS ||
    typeof obj.percentualAcertos !== 'number' ||
    !Array.isArray(obj.alternativas)
  ) {
    return null;
  }

  const alternativas: Record<string, number> = {};
  for (const item of obj.alternativas) {
    if (typeof item === 'object' && item !== null) {
      const alt = item as Record<string, unknown>;
      if (typeof alt.letra === 'string' && typeof alt.percentual === 'number') {
        alternativas[alt.letra] = alt.percentual;
      }
    }
  }

  return {
    disponivel: true,
    totalRespostas: obj.totalRespostas,
    percentualAcertos: obj.percentualAcertos,
    alternativas,
  };
}

export function IlhaTreino({ dados }: { dados: DadosTreino }) {
  const [escolha, setEscolha] = useState<string | null>(null);
  const [proximaUrl, setProximaUrl] = useState<string | null>(dados.proximaUrl);
  const [estatisticas, setEstatisticas] = useState<EstatisticasQuestao | null>(null);
  const proximaRef = useRef<HTMLAnchorElement>(null);
  const respondidaRef = useRef(false);
  const jaEnviouRespostaRef = useRef(false);
  const montadoRef = useRef(true);
  const semArmazenamento = useMemo(() => !armazenamentoDisponivel(), []);

  useEffect(() => {
    montadoRef.current = true;
    return () => {
      montadoRef.current = false;
    };
  }, []);

  useEffect(() => {
    setProximaUrl(dados.proximaUrl);
    setEstatisticas(null);
    respondidaRef.current = false;
    jaEnviouRespostaRef.current = false;
    registrarUltimaVista(dados.id, dados.filtro);
  }, [dados.id, dados.filtro, dados.proximaUrl]);

  // RF11 RN2, US04: quando a questao foi aberta pelo caderno de erros (?de=caderno),
  // o botao "Proxima questao" aponta para a proxima questao do caderno (/questao/{id}/{slug}?de=caderno).
  // RF10 RN4: nos demais filtros, quando "Ocultar questoes ja resolvidas" estiver ativa,
  // o botao "Proxima questao" e o link de navegacao pulam as questoes ja resolvidas.
  useEffect(() => {
    let cancelado = false;

    if (ehFiltroCaderno(dados.filtro)) {
      const materiaUrl = extrairMateriaFiltroCaderno(dados.filtro);
      const opcoes = materiaUrl !== null ? { materia: materiaUrl } : {};
      buscarProximaQuestaoCaderno(dados.id, opcoes)
        .then((resultado) => {
          if (cancelado) return;
          const linkNavProxima = document.querySelector<HTMLAnchorElement>('.navegacao-questao a[rel="next"]');
          const linkNavAnterior = document.querySelector<HTMLAnchorElement>('.navegacao-questao a[rel="prev"]');
          if (linkNavAnterior) {
            linkNavAnterior.hidden = true;
          }
          if (resultado) {
            setProximaUrl(resultado.url);
            if (linkNavProxima) {
              linkNavProxima.href = resultado.url;
              linkNavProxima.hidden = false;
            }
          } else {
            setProximaUrl(null);
            if (linkNavProxima) {
              linkNavProxima.hidden = true;
            }
          }
        })
        .catch(() => {
          // Em caso de falha de rede, mantem o link renderizado pelo servidor
        });

      return () => {
        cancelado = true;
      };
    }

    if (!lerOcultarResolvidas()) return;

        // Limita as consultas a /api/questoes/ids por visualizacao: se nao achar dentro
    // do limite, mantem o link renderizado pelo servidor (RNF03).
    buscarProximaNaoResolvida(dados.filtro, {
      depoisDe: dados.id,
      maxPaginas: MAX_PAGINAS_PROXIMA,
      voltarAoInicio: false,
    })
      .then((resultado) => {
        if (cancelado || resultado.status === 'limite_atingido') return;
        const linkNavProxima = document.querySelector<HTMLAnchorElement>('.navegacao-questao a[rel="next"]');
        if (resultado.status === 'encontrada') {
          const idOriginal = extrairIdDaUrlQuestao(dados.proximaUrl);
          const novaUrl = idOriginal === resultado.idQuestao && dados.proximaUrl ? dados.proximaUrl : resultado.url;
          setProximaUrl(novaUrl);
          if (linkNavProxima) {
            linkNavProxima.href = novaUrl;
            linkNavProxima.hidden = false;
          }
        } else {
          setProximaUrl(null);
          if (linkNavProxima) {
            linkNavProxima.hidden = true;
          }
        }
      })
      .catch(() => {
        // Em caso de falha de rede, mantem o link renderizado pelo servidor
      });

    return () => {
      cancelado = true;
    };
  }, [dados.id, dados.filtro, dados.proximaUrl]);

  const sincronizarEstatisticas = useCallback(async (idQuestao: number, letra: string, enviarPost: boolean) => {
    if (typeof globalThis.fetch !== 'function') return;

    if (enviarPost) {
      try {
        await globalThis.fetch(`/api/questoes/${idQuestao}/respostas`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ letra }),
        });
      } catch {
        // Falha de rede ou bloqueio: a correcao local continua funcionando normalmente
      }
    }

    try {
      const resposta = await globalThis.fetch(`/api/questoes/${idQuestao}/estatisticas`, {
        headers: { Accept: 'application/json' },
      });
      if (!resposta.ok) return;
      const json: unknown = await resposta.json();
      const lidas = extrairEstatisticasValidas(json);
      if (lidas && montadoRef.current) {
        setEstatisticas(lidas);
      }
    } catch {
      // Sem estatisticas disponiveis no momento
    }
  }, []);

  // RF06 RN2: a primeira escolha vale ate "Refazer". O ref evita registrar duas
  // vezes quando dois eventos chegam antes da nova renderizacao.
  const responder = useCallback(
    (letra: string) => {
      if (respondidaRef.current) return;
      respondidaRef.current = true;

      // RF17 RN2: so a primeira resposta da questao neste navegador e enviada ao servidor
      const jaResolvidaAntes = foiResolvida(dados.id) || jaEnviouRespostaRef.current;
      registrarResposta(dados.id, letra, letra === dados.correta, dados.filtro);
      setEscolha(letra);

      const deveEnviarPost = !jaResolvidaAntes;
      if (deveEnviarPost) {
        jaEnviouRespostaRef.current = true;
      }
      void sincronizarEstatisticas(dados.id, letra, deveEnviarPost);
    },
    [dados, sincronizarEstatisticas],
  );

  const refazer = useCallback(() => {
    respondidaRef.current = false;
    setEscolha(null);
  }, []);

  useEffect(() => {
    if (escolha !== null) {
      proximaRef.current?.focus();
    }
  }, [escolha]);

  // RNF02: teclas A a E (ou C e E no certo/errado) respondem sem tocar no mouse
  useEffect(() => {
    if (escolha !== null) return;
    const letrasValidas = new Set(dados.alternativas.map((a) => a.letra));
    function aoTeclar(evento: KeyboardEvent) {
      if (evento.ctrlKey || evento.altKey || evento.metaKey) return;
      const alvo = evento.target as HTMLElement | null;
      if (
        alvo &&
        (alvo.tagName === 'INPUT' ||
          alvo.tagName === 'TEXTAREA' ||
          alvo.tagName === 'SELECT' ||
          alvo.isContentEditable)
      ) {
        return;
      }
      const tecla = evento.key.toUpperCase();
      if (letrasValidas.has(tecla)) {
        evento.preventDefault();
        responder(tecla);
      }
    }
    window.addEventListener('keydown', aoTeclar);
    return () => window.removeEventListener('keydown', aoTeclar);
  }, [escolha, dados.alternativas, responder]);

  const respondida = escolha !== null;
  const acertou = escolha === dados.correta;

  function classeAlternativa(letra: string): string {
    if (!respondida) return 'opt';
    if (letra === dados.correta) return 'opt ok';
    if (letra === escolha) return 'opt bad';
    return 'opt dim';
  }

  return (
    <>
      <ul className="opts">
        {dados.alternativas.map((alt) => {
          const percentualAlternativa =
            respondida && estatisticas?.disponivel ? estatisticas.alternativas[alt.letra] : undefined;
          return (
            <li key={alt.letra}>
              <button
                type="button"
                className={classeAlternativa(alt.letra)}
                disabled={respondida}
                data-letra={alt.letra}
                onClick={() => responder(alt.letra)}
              >
                <span className="l">{alt.rotulo}</span>
                <span className="opt-texto" dangerouslySetInnerHTML={{ __html: alt.textoHtml }} />
                {percentualAlternativa !== undefined && (
                  <span className="opt-percentual" data-estatistica-alternativa={alt.letra}>
                    {percentualAlternativa}%
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>

      {semArmazenamento && (
        <p className="aviso-armazenamento" data-aviso-armazenamento>
          Armazenamento do navegador indisponível: seu progresso não será salvo nesta sessão.
        </p>
      )}

      {/* Antes da resposta repete o <details>, o espaco do anuncio e o link que o
          servidor renderizou, para a montagem da ilha nao deslocar a pagina (CLS, RNF03). */}
      {respondida ? (
        <div className={`fb ${acertou ? 'ok' : 'bad'}`} role="status">
          <div className="fb-topo">
            <strong>{acertou ? 'Você acertou' : `Você errou. A correta é a ${dados.rotuloCorreta}`}</strong>
            {estatisticas?.disponivel && (
              <span className="estatistica-acertos" data-estatistica-acertos>
                {estatisticas.percentualAcertos}% acertaram
              </span>
            )}
          </div>
          {ehFiltroCaderno(dados.filtro) && acertou && (
            <p className="aviso-caderno-removida" data-removida-caderno>
              Questão removida do caderno de erros.
            </p>
          )}
          <div className="comentario texto" dangerouslySetInnerHTML={{ __html: dados.comentarioHtml }} />
        </div>
      ) : (
        <details className="gabarito" data-gabarito>
          <summary>Ver resposta</summary>
          <div className="fb">
            <strong>Gabarito: {dados.rotuloCorreta}</strong>
            <div className="comentario texto" dangerouslySetInnerHTML={{ __html: dados.comentarioHtml }} />
          </div>
        </details>
      )}

      {/* RF12 RN2: 100 px de distancia entre o espaco do anuncio e os botoes */}
      <div className="espaco-anuncio-questao" data-espaco-anuncio aria-hidden="true" />

      <div className="acoes-treino">
        <a
          ref={proximaRef}
          className="btn btn-bloco"
          href={proximaUrl ?? dados.listagemUrl}
          data-proxima
        >
          {proximaUrl ? 'Próxima questão' : 'Ver mais questões'}
        </a>
        {respondida && (
          <button type="button" className="btn ghost btn-bloco" onClick={refazer} data-refazer>
            Refazer
          </button>
        )}
      </div>
    </>
  );
}

export function IlhaReporte({ idQuestao }: { idQuestao: number }) {
  const [aberto, setAberto] = useState(false);
  const [motivo, setMotivo] = useState<MotivoReporte>('GABARITO_ERRADO');
  const [descricao, setDescricao] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    setAberto(false);
    setMotivo('GABARITO_ERRADO');
    setDescricao('');
    setEnviando(false);
    setEnviado(false);
    setErro(null);
  }, [idQuestao]);

  async function enviarReporte(evento: FormEvent) {
    evento.preventDefault();
    if (enviando) return;
    setErro(null);
    setEnviando(true);
    try {
      const resposta = await globalThis.fetch(`/api/questoes/${idQuestao}/reportes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({
          motivo,
          descricao: descricao.trim() ? descricao.trim() : null,
        }),
      });
      if (!resposta.ok) {
        const problema = await resposta.json().catch(() => null);
        const mensagem =
          problema && typeof problema.detail === 'string'
            ? problema.detail
            : 'Não foi possível enviar o reporte agora. Tente novamente mais tarde.';
        setErro(mensagem);
        return;
      }
      setEnviado(true);
      setAberto(false);
      setDescricao('');
    } catch {
      setErro('Não foi possível enviar o reporte agora. Tente novamente mais tarde.');
    } finally {
      setEnviando(false);
    }
  }

  function cancelar() {
    setAberto(false);
    setErro(null);
  }

  if (enviado) {
    return (
      <p className="aviso-reporte-enviado" role="status" data-reporte-enviado>
        Reporte enviado. Obrigado por ajudar a melhorar o acervo!
      </p>
    );
  }

  if (!aberto) {
    return (
      <button type="button" className="btn-reportar" data-abrir-reporte onClick={() => setAberto(true)}>
        Reportar erro
      </button>
    );
  }

  return (
    <form className="form-reporte" data-form-reporte onSubmit={enviarReporte}>
      <label className="campo-reporte">
        <span>Motivo do reporte</span>
        <select
          required
          aria-label="Motivo do reporte"
          value={motivo}
          onChange={(e) => setMotivo(e.target.value as MotivoReporte)}
        >
          {MOTIVOS_REPORTE.map((m) => (
            <option key={m.valor} value={m.valor}>
              {m.texto}
            </option>
          ))}
        </select>
      </label>

      <label className="campo-reporte">
        <span>Descrição (opcional)</span>
        <textarea
          aria-label="Descrição (opcional)"
          rows={3}
          maxLength={1000}
          value={descricao}
          onChange={(e) => setDescricao(e.target.value)}
          placeholder="Descreva o problema encontrado (até 1.000 caracteres)"
        />
      </label>

      {erro && (
        <p role="alert" className="erro-reporte" data-erro-reporte>
          {erro}
        </p>
      )}

      <div className="acoes-reporte">
        <button type="submit" className="btn" disabled={enviando} data-enviar-reporte>
          {enviando ? 'Enviando...' : 'Enviar reporte'}
        </button>
        <button type="button" className="btn ghost" disabled={enviando} onClick={cancelar} data-cancelar-reporte>
          Cancelar
        </button>
      </div>
    </form>
  );
}

export function iniciarIlhaTreino(): void {
  montarIlha('treino', (elemento) => {
    const dados = lerDadosTreino(elemento);
    return dados ? <IlhaTreino dados={dados} /> : null;
  });
  montarIlha('reporte', (elemento) => {
    const id = Number(elemento.dataset.id);
    return id ? <IlhaReporte idQuestao={id} /> : null;
  });
}

iniciarIlhaTreino();
