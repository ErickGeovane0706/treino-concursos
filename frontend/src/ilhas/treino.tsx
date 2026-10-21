import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  armazenamentoDisponivel,
  buscarProximaNaoResolvida,
  lerOcultarResolvidas,
  registrarResposta,
  registrarUltimaVista,
} from '../progresso/progresso';
import { montarIlha } from './montar';

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

function extrairIdDaUrlQuestao(url: string | null): number | null {
  if (!url) return null;
  const match = /^\/questao\/(\d+)(?:\/|\?|$)/.exec(url);
  return match ? Number(match[1]) : null;
}

export function IlhaTreino({ dados }: { dados: DadosTreino }) {
  const [escolha, setEscolha] = useState<string | null>(null);
  const [proximaUrl, setProximaUrl] = useState<string | null>(dados.proximaUrl);
  const proximaRef = useRef<HTMLAnchorElement>(null);
  const semArmazenamento = useMemo(() => !armazenamentoDisponivel(), []);

  useEffect(() => {
    setProximaUrl(dados.proximaUrl);
    registrarUltimaVista(dados.id, dados.filtro);
  }, [dados.id, dados.filtro, dados.proximaUrl]);

  // RF10 RN4: quando "Ocultar questoes ja resolvidas" estiver ativa, o botao
  // "Proxima questao" e o link de navegacao pulam as questoes ja resolvidas.
  useEffect(() => {
    if (!lerOcultarResolvidas()) return;
    let cancelado = false;

    buscarProximaNaoResolvida(dados.filtro, { depoisDe: dados.id })
      .then((resultado) => {
        if (cancelado) return;
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

  const responder = useCallback(
    (letra: string) => {
      setEscolha((atual) => {
        if (atual !== null) return atual;
        registrarResposta(dados.id, letra, letra === dados.correta, dados.filtro);
        return letra;
      });
    },
    [dados],
  );

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
        {dados.alternativas.map((alt) => (
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
            </button>
          </li>
        ))}
      </ul>

      {semArmazenamento && (
        <p className="aviso-armazenamento" data-aviso-armazenamento>
          Armazenamento do navegador indisponível: seu progresso não será salvo nesta sessão.
        </p>
      )}

      {respondida && (
        <>
          <div className={`fb ${acertou ? 'ok' : 'bad'}`} role="status">
            <strong>{acertou ? 'Você acertou' : `Você errou. A correta é a ${dados.rotuloCorreta}`}</strong>
            <div className="comentario texto" dangerouslySetInnerHTML={{ __html: dados.comentarioHtml }} />
          </div>

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
            <button
              type="button"
              className="btn ghost btn-bloco"
              onClick={() => setEscolha(null)}
              data-refazer
            >
              Refazer
            </button>
          </div>
        </>
      )}
    </>
  );
}

export function iniciarIlhaTreino(): void {
  montarIlha('treino', (elemento) => {
    const dados = lerDadosTreino(elemento);
    return dados ? <IlhaTreino dados={dados} /> : null;
  });
}

iniciarIlhaTreino();
