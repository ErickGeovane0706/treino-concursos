import { useCallback, useEffect, useState, type KeyboardEvent, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import {
  assuntos,
  bancas,
  materias,
  orgaos,
  previaMarkdown,
  provas,
  questoes,
  type Assunto,
  type Banca,
  type FiltrosQuestao,
  type Materia,
  type Nivel,
  type Orgao,
  type Prova,
  type Questao,
  type StatusQuestao,
} from '../api';
import { Botao, BotaoExcluir, Campo, ListaPaginada, Mensagem } from '../componentes';
import { Selecao } from '../Formulario';
import { ordenarComoArvore } from './arvore';
import {
  classificacaoDe,
  deQuestao,
  estadoInicial,
  letraDoAtalho,
  letrasDo,
  montarPrevia,
  paraDados,
  rotuloDa,
  type Classificacao,
  type EstadoQuestao,
} from './questaoForm';

const STATUS: { valor: StatusQuestao; texto: string }[] = [
  { valor: 'RASCUNHO', texto: 'Rascunho' },
  { valor: 'PUBLICADA', texto: 'Publicada' },
  { valor: 'ARQUIVADA', texto: 'Arquivada' },
];

const textoStatus = (s: StatusQuestao) => STATUS.find((x) => x.valor === s)?.texto ?? s;

function avisos(q: { anulada: boolean; desatualizada: boolean }) {
  return [q.anulada && 'anulada', q.desatualizada && 'desatualizada'].filter(Boolean).join(', ');
}

export function QuestoesLista() {
  const [status, setStatus] = useState<StatusQuestao | ''>('');
  const [idMateria, setIdMateria] = useState('');
  const [listaMaterias, setListaMaterias] = useState<Materia[]>([]);

  useEffect(() => {
    materias
      .listar('', 0, 500)
      .then((p) => setListaMaterias(p.content))
      .catch(() => undefined);
  }, []);

  const carregar = useCallback(
    (busca: string, pagina: number) => {
      const filtros: FiltrosQuestao = { status, idMateria: idMateria ? Number(idMateria) : null, busca };
      return questoes.listar(filtros, pagina);
    },
    [status, idMateria],
  );

  return (
    <ListaPaginada
      titulo="Questões"
      carregar={carregar}
      colunas={[
        { titulo: 'Enunciado', valor: (q) => `#${q.id} — ${q.trecho}` },
        { titulo: 'Matéria', valor: (q) => q.nomeMateria },
        { titulo: 'Tipo', valor: (q) => (q.tipo === 'CERTO_ERRADO' ? 'C/E' : 'ME') },
        {
          titulo: 'Status',
          valor: (q) => textoStatus(q.status) + (avisos(q) ? ` (${avisos(q)})` : ''),
        },
      ]}
      linkNovo="/questoes/nova"
      textoNovo="Nova questão"
      linkItem={(q) => `/questoes/${q.id}`}
      filtros={
        <>
          <select
            aria-label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value as StatusQuestao | '')}
            className="rounded border border-slate-300 px-3 py-2"
          >
            <option value="">Todos os status</option>
            {STATUS.map((s) => (
              <option key={s.valor} value={s.valor}>
                {s.texto}
              </option>
            ))}
          </select>
          <select
            aria-label="Matéria"
            value={idMateria}
            onChange={(e) => setIdMateria(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2"
          >
            <option value="">Todas as matérias</option>
            {listaMaterias.map((m) => (
              <option key={m.id} value={m.id}>
                {m.nome}
              </option>
            ))}
          </select>
        </>
      }
    />
  );
}

interface EstadoNavegacao {
  classificacao?: Classificacao;
  mensagem?: string;
}

/**
 * Remonta o formulario a cada navegacao (location.key), inclusive de
 * /questoes/nova para /questoes/nova no "Salvar e cadastrar outra".
 */
export function QuestaoFormRota() {
  const { id } = useParams();
  const local = useLocation();
  return <QuestaoForm key={local.key} id={id ?? 'nova'} navegacao={(local.state as EstadoNavegacao | null) ?? {}} />;
}

/**
 * Editor de questao reutilizado ao lado da fila de reportes (US12, RF16),
 * permitindo corrigir a questao sem trocar de tela.
 */
export function QuestaoEditorPainel({
  idQuestao,
  aoAtualizar,
}: {
  idQuestao: number;
  aoAtualizar?: (questao: Questao) => void;
}) {
  return <QuestaoForm key={idQuestao} id={String(idQuestao)} navegacao={{}} embutido aoAtualizar={aoAtualizar} />;
}

function QuestaoForm({
  id,
  navegacao,
  embutido = false,
  aoAtualizar,
}: {
  id: string;
  navegacao: EstadoNavegacao;
  embutido?: boolean;
  aoAtualizar?: (questao: Questao) => void;
}) {
  const nova = id === 'nova';
  const navegar = useNavigate();
  const [estado, setEstado] = useState<EstadoQuestao>(() => estadoInicial(navegacao.classificacao));
  const [questao, setQuestao] = useState<Questao | null>(null);
  const [listaMaterias, setListaMaterias] = useState<Materia[]>([]);
  const [listaAssuntos, setListaAssuntos] = useState<Assunto[]>([]);
  const [listaProvas, setListaProvas] = useState<Prova[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState<string | null>(navegacao.mensagem ?? null);
  const [ocupado, setOcupado] = useState(false);
  const [previa, setPrevia] = useState('');

  const mudar = <K extends keyof EstadoQuestao>(campo: K, valor: EstadoQuestao[K]) =>
    setEstado((e) => ({ ...e, [campo]: valor }));

  useEffect(() => {
    materias
      .listar('', 0, 500)
      .then((p) => setListaMaterias(p.content))
      .catch(() => undefined);
    provas
      .listar('', 0, 500)
      .then((p) => setListaProvas(p.content))
      .catch(() => undefined);
    if (nova) return;
    questoes
      .buscar(Number(id))
      .then((q) => {
        setQuestao(q);
        setEstado(deQuestao(q));
      })
      .catch((e: Error) => setErro(e.message));
  }, [id, nova]);

  useEffect(() => {
    if (!estado.idMateria) {
      setListaAssuntos([]);
      return;
    }
    assuntos
      .listar(Number(estado.idMateria))
      .then((a) => setListaAssuntos(ordenarComoArvore(a)))
      .catch(() => undefined);
  }, [estado.idMateria]);

  // Previa com o mesmo MarkdownService das paginas publicas, sem chamar a API a cada tecla
  const textoPrevia = montarPrevia(estado);
  useEffect(() => {
    const espera = setTimeout(() => {
      previaMarkdown(textoPrevia)
        .then((r) => setPrevia(r.html))
        .catch(() => setPrevia(''));
    }, 400);
    return () => clearTimeout(espera);
  }, [textoPrevia]);

  async function executar(acao: () => Promise<string | void>) {
    setErro(null);
    setSucesso(null);
    setOcupado(true);
    try {
      const mensagem = await acao();
      if (mensagem) setSucesso(mensagem);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  function faltando(): string | null {
    if (!estado.idMateria) return 'Selecione a matéria.';
    if (estado.origem === 'PROVA' && !estado.idProva) return 'Selecione a prova (ou marque a questão como autoral).';
    if (!estado.enunciado.trim()) return 'Escreva o enunciado.';
    const letras = letrasDo(estado.tipo, estado.quantidade);
    if (estado.tipo === 'MULTIPLA_ESCOLHA') {
      const vazia = letras.find((l) => !estado.textos[l].trim());
      if (vazia) return `Escreva o texto da alternativa ${vazia}.`;
    }
    if (!estado.correta || !letras.includes(estado.correta)) return 'Marque o gabarito.';
    return null;
  }

  function salvar(eOutra: boolean) {
    const problema = faltando();
    if (problema) {
      setErro(problema);
      setSucesso(null);
      return;
    }
    executar(async () => {
      const dados = paraDados(estado);
      const salva = nova ? await questoes.criar(dados) : await questoes.atualizar(Number(id), dados);
      if (eOutra) {
        navegar('/questoes/nova', {
          state: { classificacao: classificacaoDe(estado), mensagem: `Questão #${salva.id} salva.` },
        });
        return;
      }
      if (nova) {
        navegar(`/questoes/${salva.id}`, { replace: true, state: { mensagem: 'Questão salva como rascunho.' } });
        return;
      }
      setQuestao(salva);
      aoAtualizar?.(salva);
      return 'Questão salva.';
    });
  }

  function aoTeclar(evento: KeyboardEvent) {
    if (evento.key === 'Enter' && (evento.ctrlKey || evento.metaKey)) {
      evento.preventDefault();
      salvar(!embutido && evento.shiftKey);
      return;
    }
    const letra = letraDoAtalho(evento, letrasDo(estado.tipo, estado.quantidade));
    if (letra) {
      evento.preventDefault();
      mudar('correta', letra);
    }
  }

  function acaoDoCiclo(acao: (id: number) => Promise<Questao>, mensagem: string) {
    executar(async () => {
      const atualizada = await acao(Number(id));
      setQuestao(atualizada);
      aoAtualizar?.(atualizada);
      return mensagem;
    });
  }

  const letras = letrasDo(estado.tipo, estado.quantidade);

  return (
    <section className="space-y-4">
      {!embutido && (
        <Link to="/questoes" className="text-sm text-blue-700 underline">
          ← Voltar
        </Link>
      )}
      <div className="flex flex-wrap items-baseline gap-3">
        <h2 className="text-xl font-semibold">{nova ? 'Nova questão' : `Questão #${id}`}</h2>
        {questao && (
          <span className="rounded bg-slate-100 px-2 py-1 text-sm">
            {textoStatus(questao.status)}
            {avisos(questao) && ` · ${avisos(questao)}`}
          </span>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          salvar(false);
        }}
        onKeyDown={aoTeclar}
        className={embutido ? 'space-y-4' : 'grid gap-6 lg:grid-cols-2'}
      >
        <div className="space-y-4">
          <Selecao
            rotulo="Matéria"
            valor={estado.idMateria}
            aoMudar={(v) => setEstado((e) => ({ ...e, idMateria: v, idAssunto: '' }))}
            opcoes={listaMaterias.map((m) => ({ valor: String(m.id), texto: m.nome }))}
          />
          <Selecao
            rotulo="Assunto"
            obrigatorio={false}
            valor={estado.idAssunto}
            aoMudar={(v) => mudar('idAssunto', v)}
            opcoes={listaAssuntos.map((a) => ({ valor: String(a.id), texto: `${'— '.repeat(a.nivel - 1)}${a.nome}` }))}
          />

          <Opcoes
            legenda="Tipo"
            nome="tipo"
            valor={estado.tipo}
            aoMudar={(v) => setEstado((e) => ({ ...e, tipo: v, correta: '' }))}
            opcoes={[
              { valor: 'MULTIPLA_ESCOLHA', texto: 'Múltipla escolha' },
              { valor: 'CERTO_ERRADO', texto: 'Certo ou errado' },
            ]}
          />
          <Opcoes
            legenda="Origem"
            nome="origem"
            valor={estado.origem}
            aoMudar={(v) => mudar('origem', v)}
            opcoes={[
              { valor: 'PROVA', texto: 'Prova (banca, órgão e ano)' },
              { valor: 'AUTORAL', texto: 'Autoral' },
            ]}
          />
          {estado.origem === 'PROVA' && (
            <div className="space-y-2">
              <Selecao
                rotulo="Prova"
                valor={estado.idProva}
                aoMudar={(v) => mudar('idProva', v)}
                opcoes={listaProvas.map((p) => ({
                  valor: String(p.id),
                  texto: `${p.siglaBanca} · ${p.siglaOrgao} · ${p.ano} · ${p.cargo}`,
                }))}
              />
              <NovaProvaRapida
                aoCriar={(p) => {
                  setListaProvas((lista) => [p, ...lista]);
                  mudar('idProva', String(p.id));
                }}
              />
            </div>
          )}

          <AreaTexto
            rotulo="Enunciado (Markdown)"
            required
            rows={6}
            value={estado.enunciado}
            onChange={(e) => mudar('enunciado', e.target.value)}
          />

          <fieldset className="space-y-3">
            <legend className="text-sm font-medium">
              {estado.tipo === 'CERTO_ERRADO' ? 'Gabarito' : 'Alternativas'}{' '}
              <span className="font-normal text-slate-500">(Alt+1 a Alt+{letras.length} marca a correta)</span>
            </legend>
            {estado.tipo === 'MULTIPLA_ESCOLHA' && (
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={estado.quantidade === 5}
                  onChange={(e) => mudar('quantidade', e.target.checked ? 5 : 4)}
                />
                5 alternativas (A a E)
              </label>
            )}
            {letras.map((letra) => (
              <div key={letra} className="flex items-start gap-3">
                <label className="flex items-center gap-1 pt-2 text-sm font-semibold">
                  <input
                    type="radio"
                    name="correta"
                    checked={estado.correta === letra}
                    onChange={() => mudar('correta', letra)}
                    aria-label={`Alternativa ${rotuloDa(estado.tipo, letra)} é a correta`}
                  />
                  {rotuloDa(estado.tipo, letra)}
                </label>
                {estado.tipo === 'MULTIPLA_ESCOLHA' && (
                  <textarea
                    aria-label={`Texto da alternativa ${letra}`}
                    required
                    rows={2}
                    value={estado.textos[letra]}
                    onChange={(e) => mudar('textos', { ...estado.textos, [letra]: e.target.value })}
                    className="w-full rounded border border-slate-300 px-3 py-2"
                  />
                )}
              </div>
            ))}
          </fieldset>

          <AreaTexto
            rotulo="Comentário (Markdown)"
            ajuda="Obrigatório para publicar. Explique por que a correta está certa e as outras, erradas."
            rows={8}
            value={estado.comentario}
            onChange={(e) => mudar('comentario', e.target.value)}
          />
          {questao?.status === 'PUBLICADA' && (
            <p className="text-xs text-amber-800">
              Questão publicada: trocar o gabarito zera as estatísticas de respostas.
            </p>
          )}

          <Mensagem erro={erro} sucesso={sucesso} />
          <div className="flex flex-wrap gap-3">
            <Botao type="submit" disabled={ocupado}>
              {ocupado ? 'Salvando...' : 'Salvar'}
            </Botao>
            {!embutido && (
              <button
                type="button"
                disabled={ocupado}
                onClick={() => salvar(true)}
                className="rounded border border-blue-700 px-4 py-2 font-medium text-blue-700 disabled:opacity-60"
              >
                Salvar e cadastrar outra
              </button>
            )}
          </div>
          {!embutido && <p className="text-xs text-slate-500">Ctrl+Enter salva; Ctrl+Shift+Enter salva e abre uma nova.</p>}
        </div>

        <aside aria-label="Pré-visualização" className="space-y-2 lg:sticky lg:top-4 lg:self-start">
          <h3 className="text-sm font-medium text-slate-600">Pré-visualização</h3>
          {/* HTML vindo do MarkdownService (commonmark + sanitizer), nunca o texto cru */}
          <div
            className="space-y-3 rounded border border-slate-200 bg-white p-4 text-sm leading-relaxed [&_a]:text-blue-700 [&_a]:underline [&_blockquote]:border-l-4 [&_blockquote]:pl-3 [&_code]:bg-slate-100 [&_h3]:font-semibold [&_hr]:border-slate-200 [&_ol]:list-decimal [&_ol]:pl-5 [&_pre]:bg-slate-100 [&_pre]:p-2 [&_td]:border [&_td]:px-2 [&_th]:border [&_th]:px-2 [&_ul]:list-disc [&_ul]:pl-5"
            dangerouslySetInnerHTML={{ __html: previa }}
          />
        </aside>
      </form>

      {questao && (
        <CicloDeVida
          questao={questao}
          ocupado={ocupado}
          aoPublicar={() => acaoDoCiclo(questoes.publicar, 'Questão publicada.')}
          aoArquivar={() => acaoDoCiclo(questoes.arquivar, 'Questão arquivada: o endereço passa a responder 410.')}
          aoAnular={() =>
            acaoDoCiclo((i) => questoes.anular(i, questao.anulada), questao.anulada ? 'Anulação desfeita.' : 'Questão marcada como anulada.')
          }
          aoDesatualizar={() =>
            acaoDoCiclo(
              (i) => questoes.marcarDesatualizada(i, questao.desatualizada),
              questao.desatualizada ? 'Marcação de desatualizada desfeita.' : 'Questão marcada como desatualizada.',
            )
          }
          aoExcluir={() =>
            executar(async () => {
              await questoes.excluir(questao.id);
              navegar('/questoes');
            })
          }
        />
      )}
    </section>
  );
}

/** Acoes do RF05. A API recusa o que nao vale (ex.: publicar sem comentario) e a mensagem aparece acima. */
function CicloDeVida({
  questao,
  ocupado,
  aoPublicar,
  aoArquivar,
  aoAnular,
  aoDesatualizar,
  aoExcluir,
}: {
  questao: Questao;
  ocupado: boolean;
  aoPublicar: () => void;
  aoArquivar: () => void;
  aoAnular: () => void;
  aoDesatualizar: () => void;
  aoExcluir: () => void;
}) {
  const classe = 'rounded border px-4 py-2 disabled:opacity-60';
  return (
    <div className="space-y-2 border-t border-slate-200 pt-4">
      <h3 className="text-sm font-medium text-slate-600">Ciclo de vida</h3>
      <p className="text-xs text-slate-500">
        Salve as alterações antes de publicar. Autor: {questao.nomeAutor}. Respostas: {questao.totalRespostas}.
      </p>
      <div className="flex flex-wrap gap-3">
        {questao.status !== 'PUBLICADA' && (
          <button type="button" disabled={ocupado} onClick={aoPublicar} className={`${classe} border-green-700 text-green-800`}>
            Publicar
          </button>
        )}
        {questao.status !== 'ARQUIVADA' && (
          <button type="button" disabled={ocupado} onClick={aoArquivar} className={classe}>
            Arquivar
          </button>
        )}
        <button type="button" disabled={ocupado} onClick={aoAnular} className={classe}>
          {questao.anulada ? 'Desfazer anulação' : 'Marcar como anulada'}
        </button>
        <button type="button" disabled={ocupado} onClick={aoDesatualizar} className={classe}>
          {questao.desatualizada ? 'Desfazer desatualizada' : 'Marcar como desatualizada'}
        </button>
        {questao.publicadoEm === null && <BotaoExcluir aoExcluir={aoExcluir} />}
      </div>
    </div>
  );
}

/** Atalho para cadastrar a prova sem sair da questao (fica dentro do form, por isso sem <form> proprio). */
function NovaProvaRapida({ aoCriar }: { aoCriar: (prova: Prova) => void }) {
  const [aberto, setAberto] = useState(false);
  const [listaBancas, setListaBancas] = useState<Banca[]>([]);
  const [listaOrgaos, setListaOrgaos] = useState<Orgao[]>([]);
  const [banca, setBanca] = useState('');
  const [orgao, setOrgao] = useState('');
  const [ano, setAno] = useState('');
  const [cargo, setCargo] = useState('');
  const [nivel, setNivel] = useState<Nivel | ''>('');
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!aberto) return;
    bancas
      .listar('', 0, 500)
      .then((p) => setListaBancas(p.content))
      .catch(() => undefined);
    orgaos
      .listar('', 0, 500)
      .then((p) => setListaOrgaos(p.content))
      .catch(() => undefined);
  }, [aberto]);

  if (!aberto) {
    return (
      <button type="button" onClick={() => setAberto(true)} className="text-sm text-blue-700 underline">
        + Cadastrar prova
      </button>
    );
  }

  async function criar() {
    setErro(null);
    if (!banca || !orgao || !ano || !cargo.trim() || !nivel) {
      setErro('Preencha banca, órgão, ano, cargo e nível.');
      return;
    }
    try {
      const prova = await provas.criar({
        idBanca: Number(banca),
        idOrgao: Number(orgao),
        ano: Number(ano),
        cargo: cargo.trim(),
        nivel,
      });
      aoCriar(prova);
      setAberto(false);
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <div className="space-y-3 rounded border border-slate-200 bg-slate-50 p-3">
      <Selecao
        rotulo="Banca"
        obrigatorio={false}
        valor={banca}
        aoMudar={setBanca}
        opcoes={listaBancas.map((b) => ({ valor: String(b.id), texto: `${b.sigla} — ${b.nome}` }))}
      />
      <Selecao
        rotulo="Órgão"
        obrigatorio={false}
        valor={orgao}
        aoMudar={setOrgao}
        opcoes={listaOrgaos.map((o) => ({ valor: String(o.id), texto: `${o.sigla} — ${o.nome}` }))}
      />
      <Campo rotulo="Ano" type="number" min={1990} max={2100} value={ano} onChange={(e) => setAno(e.target.value)} />
      <Campo rotulo="Cargo" maxLength={150} value={cargo} onChange={(e) => setCargo(e.target.value)} />
      <Selecao
        rotulo="Nível de escolaridade"
        obrigatorio={false}
        valor={nivel}
        aoMudar={(v) => setNivel(v as Nivel | '')}
        opcoes={[
          { valor: 'FUNDAMENTAL', texto: 'Fundamental' },
          { valor: 'MEDIO', texto: 'Médio' },
          { valor: 'SUPERIOR', texto: 'Superior' },
        ]}
      />
      <Mensagem erro={erro} />
      <div className="flex gap-3">
        <button type="button" onClick={criar} className="rounded bg-blue-700 px-3 py-1 text-white">
          Criar prova
        </button>
        <button type="button" onClick={() => setAberto(false)} className="rounded border px-3 py-1">
          Cancelar
        </button>
      </div>
    </div>
  );
}

function Opcoes<T extends string>({
  legenda,
  nome,
  valor,
  aoMudar,
  opcoes,
}: {
  legenda: string;
  nome: string;
  valor: T;
  aoMudar: (v: T) => void;
  opcoes: { valor: T; texto: string }[];
}) {
  return (
    <fieldset>
      <legend className="text-sm font-medium">{legenda}</legend>
      <div className="mt-1 flex flex-wrap gap-4">
        {opcoes.map((o) => (
          <label key={o.valor} className="flex items-center gap-2 text-sm">
            <input type="radio" name={nome} checked={valor === o.valor} onChange={() => aoMudar(o.valor)} />
            {o.texto}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function AreaTexto({
  rotulo,
  ajuda,
  ...props
}: { rotulo: string; ajuda?: ReactNode } & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <label className="block">
      <span className="text-sm font-medium">{rotulo}</span>
      <textarea {...props} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-sm" />
      {ajuda && <span className="mt-1 block text-xs text-slate-500">{ajuda}</span>}
    </label>
  );
}
