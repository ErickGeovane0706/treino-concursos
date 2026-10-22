import { StrictMode, useCallback, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  armazenamentoDisponivel,
  buscarProximaNaoResolvida,
  calcularResumoProgresso,
  definirOcultarResolvidas,
  lerOcultarResolvidas,
  lerProgresso,
  limparProgresso,
  limparUltimaVista,
  normalizarChaveFiltro,
  type DadosProgresso,
  type ResultadoContinuar,
} from '../progresso/progresso';
import { montarIlha } from './montar';

export interface PropriedadesIlhaListagem {
  raiz: HTMLElement;
  filtro: string;
  temListaQuestoes: boolean;
  aoNavegar?: (url: string) => void;
  fetchFn?: typeof fetch;
}

export function navegarParaUrl(url: string): void {
  try {
    window.location.assign(url);
  } catch {
    // Ignorado em ambientes de teste sem navegacao implementada
  }
}

/**
 * Aplica nos itens <li data-id-questao="..."> ja renderizados pelo Thymeleaf
 * a marcacao visual de resolvido (acertou/errou) e a ocultacao quando ativa (RF10 RN2, RN4).
 */
export function aplicarProgressoNosItens(
  raiz: ParentNode,
  progresso: DadosProgresso,
  ocultarResolvidas: boolean,
): { totalItens: number; visiveis: number; resolvidasNaPagina: number } {
  const itens = [...raiz.querySelectorAll<HTMLElement>('[data-id-questao]')];
  let visiveis = 0;
  let resolvidasNaPagina = 0;

  for (const item of itens) {
    const id = Number(item.dataset.idQuestao);
    const registro = id > 0 ? progresso.respostas[String(id)] : undefined;
    const tagExistente = item.querySelector<HTMLElement>('[data-tag-resolvida]');

    if (registro) {
      resolvidasNaPagina++;
      const acertou = registro.acertou;
      item.dataset.resolvida = 'true';
      item.dataset.resultado = acertou ? 'acertou' : 'errou';
      item.classList.add('item-resolvida');
      item.classList.toggle('item-acertou', acertou);
      item.classList.toggle('item-errou', !acertou);

      const textoTag = acertou ? 'Resolvida · Acertou' : 'Resolvida · Errou';
      const classeTag = `tag-resolvida ${acertou ? 'ok' : 'bad'}`;
      if (tagExistente) {
        tagExistente.className = classeTag;
        tagExistente.textContent = textoTag;
      } else {
        const tag = document.createElement('span');
        tag.setAttribute('data-tag-resolvida', '');
        tag.className = classeTag;
        tag.textContent = textoTag;
        const meta = item.querySelector('.meta') ?? item;
        meta.appendChild(tag);
      }

      if (ocultarResolvidas) {
        item.hidden = true;
        item.dataset.oculta = 'true';
      } else {
        item.hidden = false;
        delete item.dataset.oculta;
        visiveis++;
      }
    } else {
      delete item.dataset.resolvida;
      delete item.dataset.resultado;
      delete item.dataset.oculta;
      item.classList.remove('item-resolvida', 'item-acertou', 'item-errou');
      if (tagExistente) {
        tagExistente.remove();
      }
      item.hidden = false;
      visiveis++;
    }
  }

  return { totalItens: itens.length, visiveis, resolvidasNaPagina };
}

export function IlhaListagem({
  raiz,
  filtro,
  temListaQuestoes,
  aoNavegar = navegarParaUrl,
  fetchFn,
}: PropriedadesIlhaListagem) {
  const disponivel = useMemo(() => armazenamentoDisponivel(), []);
  const [progresso, setProgresso] = useState<DadosProgresso>(() => lerProgresso());
  const [ocultar, setOcultar] = useState<boolean>(() => lerOcultarResolvidas());
  const [buscando, setBuscando] = useState(false);
  const [fimFiltro, setFimFiltro] = useState<Extract<ResultadoContinuar, { status: 'fim_do_filtro' }> | null>(null);
  const [avisoVazio, setAvisoVazio] = useState(false);
  const [estadoPagina, setEstadoPagina] = useState({ totalItens: 0, visiveis: 0, resolvidasNaPagina: 0 });

  useEffect(() => {
    const resultado = aplicarProgressoNosItens(raiz, progresso, ocultar);
    setEstadoPagina(resultado);
  }, [raiz, progresso, ocultar]);

  const aoAlternarOcultar = useCallback((novoValor: boolean) => {
    definirOcultarResolvidas(novoValor);
    setOcultar(novoValor);
    setProgresso(lerProgresso());
  }, []);

  const aoContinuar = useCallback(async () => {
    setBuscando(true);
    setFimFiltro(null);
    setAvisoVazio(false);
    try {
      const resultado = await buscarProximaNaoResolvida(filtro, { fetchFn });
      if (resultado.status === 'encontrada') {
        aoNavegar(resultado.url);
      } else if (resultado.status === 'fim_do_filtro') {
        setFimFiltro(resultado);
      } else {
        setAvisoVazio(true);
      }
    } finally {
      setBuscando(false);
    }
  }, [filtro, aoNavegar, fetchFn]);

  const aoReiniciarFiltro = useCallback(() => {
    if (!fimFiltro) return;
    limparUltimaVista(filtro);
    setProgresso(lerProgresso());
    aoNavegar(fimFiltro.urlReiniciar);
  }, [fimFiltro, filtro, aoNavegar]);

  if (!disponivel) {
    return (
      <p className="aviso-armazenamento" data-aviso-armazenamento>
        Armazenamento do navegador indisponível: seu progresso não será salvo nesta sessão.
      </p>
    );
  }

  const resumo = calcularResumoProgresso(progresso);
  const todasDaPaginaOcultas =
    temListaQuestoes && ocultar && estadoPagina.totalItens > 0 && estadoPagina.visiveis === 0;

  return (
    <div className="controles-progresso" data-barra-progresso>
      <div className="controles-progresso-linha">
        <button
          type="button"
          className="btn"
          onClick={aoContinuar}
          disabled={buscando}
          data-continuar
        >
          {buscando ? 'Buscando próxima questão…' : 'Continuar de onde parei'}
        </button>

        {temListaQuestoes && (
          <label className="chave-ocultar">
            <input
              type="checkbox"
              checked={ocultar}
              onChange={(e) => aoAlternarOcultar(e.target.checked)}
              data-ocultar-resolvidas
            />
            <span>Ocultar questões já resolvidas</span>
          </label>
        )}

        <a className="link-meu-progresso" href="/meu-progresso">
          {resumo.totalResolvidas > 0
            ? `Meu progresso (${resumo.totalResolvidas} ${resumo.totalResolvidas === 1 ? 'resolvida' : 'resolvidas'})`
            : 'Meu progresso'}
        </a>
      </div>

      {fimFiltro && (
        <div className="aviso-fim-filtro" role="status" data-fim-filtro>
          <p>
            Você já viu as questões finais deste filtro. Deseja voltar ao início e reiniciar?
          </p>
          <div className="acoes">
            <button
              type="button"
              className="btn"
              onClick={aoReiniciarFiltro}
              data-reiniciar-filtro
            >
              Reiniciar do início
            </button>
            <a className="btn ghost" href={fimFiltro.urlInicioFiltro}>
              Ver início do filtro
            </a>
          </div>
        </div>
      )}

      {avisoVazio && (
        <p className="aviso-fim-filtro" role="status" data-filtro-sem-questoes>
          Nenhuma questão disponível neste filtro no momento.
        </p>
      )}

      {todasDaPaginaOcultas && (
        <p className="aviso-todas-ocultas" data-todas-ocultas>
          Todas as questões desta página já foram resolvidas e estão ocultas. Use &ldquo;Continuar de onde parei&rdquo;
          ou avance para mais questões.
        </p>
      )}
    </div>
  );
}

export function IlhaMeuProgresso() {
  const disponivel = useMemo(() => armazenamentoDisponivel(), []);
  const [progresso, setProgresso] = useState<DadosProgresso>(() => lerProgresso());
  const [apagado, setApagado] = useState(false);

  const resumo = useMemo(() => calcularResumoProgresso(progresso), [progresso]);

  const aoAlternarOcultar = useCallback((novoValor: boolean) => {
    definirOcultarResolvidas(novoValor);
    setProgresso(lerProgresso());
  }, []);

  const aoApagar = useCallback(() => {
    limparProgresso();
    setProgresso(lerProgresso());
    setApagado(true);
  }, []);

  if (!disponivel) {
    return (
      <p className="aviso-armazenamento" data-aviso-armazenamento>
        Armazenamento do navegador indisponível (aba anônima ou bloqueio): seu progresso não pode ser salvo neste
        navegador, mas você pode resolver todas as questões normalmente.
      </p>
    );
  }

  return (
    <div className="painel-meu-progresso" data-painel-meu-progresso>
      <div className="resumo-progresso-grade" data-resumo-progresso>
        <div className="resumo-item">
          <span className="resumo-rotulo">Resolvidas</span>
          <strong className="resumo-valor" data-total-resolvidas>
            {resumo.totalResolvidas}
          </strong>
        </div>
        <div className="resumo-item">
          <span className="resumo-rotulo">Acertos</span>
          <strong className="resumo-valor ok" data-total-acertos>
            {resumo.totalAcertos}
          </strong>
        </div>
        <div className="resumo-item">
          <span className="resumo-rotulo">Erros</span>
          <strong className="resumo-valor bad" data-total-erros>
            {resumo.totalErros}
          </strong>
        </div>
        <div className="resumo-item">
          <span className="resumo-rotulo">Aproveitamento</span>
          <strong className="resumo-valor" data-percentual-acertos>
            {resumo.percentualAcertos}%
          </strong>
        </div>
      </div>

      <div className="preferencias-progresso">
        <label className="chave-ocultar">
          <input
            type="checkbox"
            checked={progresso.ocultarResolvidas}
            onChange={(e) => aoAlternarOcultar(e.target.checked)}
            data-ocultar-resolvidas
          />
          <span>Ocultar questões já resolvidas nas listagens e ao avançar</span>
        </label>
      </div>

      {apagado && (
        <p className="fb ok" role="status" data-progresso-apagado>
          Todo o progresso salvo neste navegador foi apagado.
        </p>
      )}

      <div className="acoes">
        <button
          type="button"
          className="btn ghost"
          onClick={aoApagar}
          disabled={resumo.totalResolvidas === 0 && Object.keys(progresso.ultimaVista).length === 0}
          data-limpar-progresso
        >
          Apagar todo o progresso
        </button>
        <a className="btn" href="/questoes">
          Ir para as questões
        </a>
      </div>
    </div>
  );
}

/**
 * Monta os controles da ilha de listagem preservando o HTML renderizado pelo
 * servidor (titulo, chips de filtro e lista de questoes para SEO).
 */
export function iniciarIlhaListagem(): void {
  const elementoListagem = document.querySelector<HTMLElement>('[data-ilha="listagem"]');
  if (elementoListagem && !elementoListagem.querySelector('[data-lista-vazia]')) {
    const filtro = normalizarChaveFiltro(elementoListagem.dataset.filtro);
    const listaQuestoes = elementoListagem.querySelector<HTMLElement>('[data-lista-questoes]');
    let hospedeiro = elementoListagem.querySelector<HTMLElement>('[data-controles-listagem]');
    if (!hospedeiro) {
      hospedeiro = document.createElement('div');
      hospedeiro.setAttribute('data-controles-listagem', '');
      if (listaQuestoes && listaQuestoes.parentElement) {
        listaQuestoes.parentElement.insertBefore(hospedeiro, listaQuestoes);
      } else {
        elementoListagem.appendChild(hospedeiro);
      }
    }
    createRoot(hospedeiro).render(
      <StrictMode>
        <IlhaListagem
          raiz={elementoListagem}
          filtro={filtro}
          temListaQuestoes={Boolean(listaQuestoes)}
        />
      </StrictMode>,
    );
  }

  montarIlha('meu-progresso', () => <IlhaMeuProgresso />);
}

iniciarIlhaListagem();
