import { useEffect, useMemo, useState } from 'react';
import {
  armazenamentoDisponivel,
  calcularResultado,
  finalizarSimulado,
  formatarTempo,
  iniciarSimulado,
  lerSimulado,
  marcarResposta,
  primeiraSemResposta,
  reiniciarSimulado,
  tempoRestante,
  type EstadoSimulado,
  type ItemGabarito,
} from '../progresso';
import { montarIlha } from './montar';

// Ilhas do modo simulado (RF13 RN1-RN5, US05). As paginas sao do servidor; cada
// questao e uma pagina propria e as respostas ficam no localStorage, uma chave por simulado.

function irPara(url: string): void {
  window.location.assign(url);
}

export function urlQuestaoDoSimulado(slug: string, n: number): string {
  return `/simulado/${slug}/${n}`;
}

// ---------------- questao n ----------------

export interface DadosQuestaoSimulado {
  slug: string;
  id: number;
  n: number;
  total: number;
  tempoMin: number | null;
  resultadoUrl: string;
  primeiraUrl: string;
  alternativas: { letra: string; rotulo: string; textoHtml: string }[];
}

export function lerDadosQuestaoSimulado(elemento: HTMLElement): DadosQuestaoSimulado | null {
  const { slug, resultadoUrl, primeiraUrl } = elemento.dataset;
  const id = Number(elemento.dataset.id);
  const n = Number(elemento.dataset.n);
  const total = Number(elemento.dataset.total);
  const tempo = Number(elemento.dataset.tempo);
  if (!slug || !id || !n || !total || !resultadoUrl || !primeiraUrl) return null;
  const alternativas = [...elemento.querySelectorAll<HTMLButtonElement>('button.opt[data-letra]')].map((b) => ({
    letra: b.dataset.letra ?? '',
    rotulo: b.querySelector('.l')?.textContent?.trim() || (b.dataset.letra ?? ''),
    // HTML do servidor, ja sanitizado pelo MarkdownService
    textoHtml: b.querySelector('.opt-texto')?.innerHTML ?? '',
  }));
  if (alternativas.length === 0) return null;
  return { slug, id, n, total, tempoMin: tempo > 0 ? tempo : null, resultadoUrl, primeiraUrl, alternativas };
}

/** RF13 RN5: cronometro do tempo sugerido; avisa quando acaba, sem finalizar. */
function Cronometro({ iniciadoEm, minutos }: { iniciadoEm: number; minutos: number }) {
  const [agora, setAgora] = useState(() => Date.now());
  useEffect(() => {
    const intervalo = setInterval(() => setAgora(Date.now()), 1000);
    return () => clearInterval(intervalo);
  }, []);
  const restante = tempoRestante(iniciadoEm, minutos, agora);
  const esgotado = restante <= 0;
  return (
    <>
      <span className={`cronometro${esgotado ? ' esgotado' : ''}`} aria-label="Tempo restante">
        ⏱ {formatarTempo(restante)}
      </span>
      {esgotado && (
        <p className="aviso-tempo" role="alert">
          O tempo sugerido acabou. Você pode continuar e finalizar quando quiser.
        </p>
      )}
    </>
  );
}

export function IlhaSimuladoQuestao({ dados }: { dados: DadosQuestaoSimulado }) {
  const semArmazenamento = useMemo(() => !armazenamentoDisponivel(), []);
  const [estado, setEstado] = useState<EstadoSimulado>(() => iniciarSimulado(dados.slug));
  const marcada = estado.respostas[String(dados.id)] ?? null;
  const finalizado = estado.finalizadoEm !== null;

  function marcar(letra: string | null) {
    setEstado(marcarResposta(dados.slug, dados.id, letra));
  }

  function refazer() {
    reiniciarSimulado(dados.slug);
    irPara(dados.primeiraUrl);
  }

  return (
    <>
      <ul className="opts">
        {dados.alternativas.map((alt) => (
          <li key={alt.letra}>
            <button
              type="button"
              className={`opt${marcada === alt.letra ? ' sel' : ''}`}
              aria-pressed={marcada === alt.letra}
              disabled={finalizado}
              onClick={() => marcar(alt.letra)}
              data-letra={alt.letra}
            >
              <span className="l">{alt.rotulo}</span>
              <span className="opt-texto" dangerouslySetInnerHTML={{ __html: alt.textoHtml }} />
            </button>
          </li>
        ))}
      </ul>

      <div className="situacao-simulado">
        <span role="status">
          {marcada ? `Resposta marcada: ${marcada}` : 'Sem resposta (em branco conta como erro)'}
        </span>
        {!finalizado && marcada && (
          <button type="button" className="link" onClick={() => marcar(null)}>
            Deixar em branco
          </button>
        )}
        {!finalizado && dados.tempoMin !== null && estado.iniciadoEm !== null && (
          <Cronometro iniciadoEm={estado.iniciadoEm} minutos={dados.tempoMin} />
        )}
      </div>

      {finalizado && (
        <div className="fb" role="status">
          <strong>Você já finalizou este simulado.</strong>
          <p>
            <a href={dados.resultadoUrl}>Ver resultado</a> ou{' '}
            <button type="button" className="link" onClick={refazer}>
              refazer do início
            </button>
            .
          </p>
        </div>
      )}
      {semArmazenamento && (
        <p className="aviso-armazenamento">
          Seu navegador não está guardando dados deste site, então as respostas não ficam salvas entre as questões.
        </p>
      )}
    </>
  );
}

// ---------------- resultado ----------------

export function lerGabarito(elemento: HTMLElement): (ItemGabarito & { rotuloCorreta: string; trecho: string })[] {
  return [...elemento.querySelectorAll<HTMLElement>('li[data-id]')].map((li) => ({
    id: Number(li.dataset.id),
    materia: li.dataset.materia ?? '',
    correta: li.dataset.correta ?? '',
    rotuloCorreta: li.dataset.rotuloCorreta || (li.dataset.correta ?? ''),
    trecho: li.querySelector('.rt span')?.textContent ?? '',
  }));
}

const TEXTO_SITUACAO = { certa: 'Certa', errada: 'Errada', branco: 'Em branco' } as const;

export function IlhaSimuladoResultado({
  slug,
  primeiraUrl,
  comentariosUrl,
  gabarito,
}: {
  slug: string;
  primeiraUrl: string;
  /** RF15 RN1: comentarios em pagina separada; o gabarito e o desempenho continuam gratis. */
  comentariosUrl?: string;
  gabarito: (ItemGabarito & { rotuloCorreta: string; trecho?: string })[];
}) {
  const [estado] = useState<EstadoSimulado>(() => {
    const atual = lerSimulado(slug);
    const comecou = atual.iniciadoEm !== null || Object.keys(atual.respostas).length > 0;
    return comecou ? finalizarSimulado(slug) : atual;
  });
  const comecou = estado.finalizadoEm !== null;
  const resultado = calcularResultado(gabarito, estado.respostas);
  const rotulos = new Map(gabarito.map((g) => [g.id, g]));

  function refazer() {
    reiniciarSimulado(slug);
    irPara(primeiraUrl);
  }

  return (
    <>
      {comecou ? (
        <>
          <div className="end">
            <div>Seu resultado</div>
            <div className="big">{resultado.percentual}%</div>
            <p>
              {resultado.acertos} {resultado.acertos === 1 ? 'acerto' : 'acertos'} em {resultado.total} questões
            </p>
          </div>
          <section className="mats" aria-label="Desempenho por matéria">
            {resultado.porMateria.map((m) => (
              <div className="row" key={m.materia}>
                <span>{m.materia}</span>
                <span className="mini" aria-hidden="true">
                  <i style={{ width: `${(m.acertos / m.total) * 100}%` }} />
                </span>
                <b>
                  {m.acertos}/{m.total}
                </b>
              </div>
            ))}
          </section>
        </>
      ) : (
        <p className="aviso-fim-filtro">
          Não encontramos respostas deste simulado neste navegador. <a href={primeiraUrl}>Começar o simulado</a>
        </p>
      )}

      <ol className="revs">
        {resultado.itens.map((item, i) => (
          <li
            key={item.id}
            className={`rev ${comecou ? (item.situacao === 'certa' ? 'ok' : 'bad') : ''}`}
            data-situacao={comecou ? item.situacao : undefined}
          >
            <div className="rt">
              {comecou && <span className="tag">{TEXTO_SITUACAO[item.situacao]}</span>}
              <span>{rotulos.get(item.id)?.trecho || `${i + 1}.`}</span>
            </div>
            <div className="ra">
              {item.materia} ·{' '}
              {comecou && (
                <>
                  Sua resposta: <b>{item.resposta ?? 'em branco'}</b> ·{' '}
                </>
              )}
              Gabarito: <b>{rotulos.get(item.id)?.rotuloCorreta ?? item.correta}</b>
            </div>
          </li>
        ))}
      </ol>

      <div className="acoes-resultado">
        {comentariosUrl && (
          <a className="btn" href={comentariosUrl}>
            Ver comentários
          </a>
        )}
        {comecou && (
          <button type="button" className="btn ghost" onClick={refazer}>
            Refazer simulado
          </button>
        )}
        <a className="btn ghost" href="/simulados">
          Outros simulados
        </a>
      </div>
    </>
  );
}

// ---------------- apresentacao ----------------

export function IlhaSimuladoApresentacao({
  slug,
  ids,
  resultadoUrl,
}: {
  slug: string;
  ids: number[];
  resultadoUrl: string;
}) {
  const [estado] = useState(() => lerSimulado(slug));
  const respondidas = ids.filter((id) => estado.respostas[String(id)] !== undefined).length;
  const primeira = urlQuestaoDoSimulado(slug, 1);

  function recomecar() {
    reiniciarSimulado(slug);
    irPara(primeira);
  }

  if (estado.finalizadoEm !== null) {
    return (
      <>
        <a className="btn" href={resultadoUrl}>
          Ver meu resultado
        </a>
        <button type="button" className="btn ghost" onClick={recomecar}>
          Refazer simulado
        </button>
      </>
    );
  }
  if (respondidas > 0) {
    const n = primeiraSemResposta(ids, estado.respostas) ?? ids.length;
    return (
      <>
        <a className="btn" href={urlQuestaoDoSimulado(slug, n)}>
          Continuar (questão {n})
        </a>
        <button type="button" className="btn ghost" onClick={recomecar}>
          Recomeçar
        </button>
        <p className="meta-simulado">
          {respondidas} de {ids.length} questões respondidas neste navegador.
        </p>
      </>
    );
  }
  return (
    <a className="btn" href={primeira}>
      Começar simulado
    </a>
  );
}

export function iniciarIlhasSimulado(): void {
  montarIlha('simulado-questao', (elemento) => {
    const dados = lerDadosQuestaoSimulado(elemento);
    return dados ? <IlhaSimuladoQuestao dados={dados} /> : null;
  });
  montarIlha('simulado-resultado', (elemento) => {
    const { slug, primeiraUrl, comentariosUrl } = elemento.dataset;
    const gabarito = lerGabarito(elemento);
    if (!slug || !primeiraUrl || gabarito.length === 0) return null;
    return (
      <IlhaSimuladoResultado
        slug={slug}
        primeiraUrl={primeiraUrl}
        comentariosUrl={comentariosUrl}
        gabarito={gabarito}
      />
    );
  });
  montarIlha('simulado-apresentacao', (elemento) => {
    const { slug, resultadoUrl } = elemento.dataset;
    const ids = (elemento.dataset.ids ?? '').split(',').map(Number).filter((n) => n > 0);
    if (!slug || !resultadoUrl || ids.length === 0) return null;
    return <IlhaSimuladoApresentacao slug={slug} ids={ids} resultadoUrl={resultadoUrl} />;
  });
}

iniciarIlhasSimulado();
