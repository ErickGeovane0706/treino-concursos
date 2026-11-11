import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import {
  materias,
  questoes,
  simulados,
  type Materia,
  type Pagina,
  type QuestaoResumo,
  type Simulado,
  type StatusSimulado,
} from '../api';
import { Botao, BotaoExcluir, Campo, ListaPaginada, Mensagem } from '../componentes';
import {
  adicionar,
  MAXIMO_QUESTOES,
  motivoInelegivel,
  mover,
  pendenciasParaPublicar,
  remover,
  type ItemSimulado,
} from './simuladoForm';

const STATUS: { valor: StatusSimulado; texto: string }[] = [
  { valor: 'RASCUNHO', texto: 'Rascunho' },
  { valor: 'PUBLICADO', texto: 'Publicado' },
  { valor: 'ARQUIVADO', texto: 'Arquivado' },
];

const textoStatus = (s: StatusSimulado) => STATUS.find((x) => x.valor === s)?.texto ?? s;

/** Lista de simulados (US11), com destaque para os que precisam de revisao (RF14 RN4). */
export function SimuladosLista() {
  const [status, setStatus] = useState<StatusSimulado | ''>('');
  const carregar = useCallback(
    (busca: string, pagina: number) => simulados.listar(status, busca, pagina),
    [status],
  );

  return (
    <ListaPaginada
      titulo="Simulados"
      carregar={carregar}
      colunas={[
        { titulo: 'Título', valor: (s) => s.titulo },
        { titulo: 'Questões', valor: (s) => s.totalQuestoes },
        {
          titulo: 'Status',
          valor: (s) => (
            <>
              {textoStatus(s.status)}
              {s.precisaRevisao && (
                <span className="ml-2 rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900">
                  Precisa de revisão
                </span>
              )}
            </>
          ),
        },
      ]}
      linkNovo="/simulados/novo"
      textoNovo="Novo simulado"
      linkItem={(s) => `/simulados/${s.id}`}
      filtros={
        <select
          aria-label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as StatusSimulado | '')}
          className="rounded border border-slate-300 px-3 py-2"
        >
          <option value="">Todos os status</option>
          {STATUS.map((s) => (
            <option key={s.valor} value={s.valor}>
              {s.texto}
            </option>
          ))}
        </select>
      }
    />
  );
}

export function SimuladoFormRota() {
  const { id } = useParams();
  const novo = id === 'novo';
  const [simulado, setSimulado] = useState<Simulado | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (novo) return;
    simulados
      .buscar(Number(id))
      .then(setSimulado)
      .catch((e: Error) => setErro(e.message));
  }, [id, novo]);

  if (erro) return <Mensagem erro={erro} />;
  if (!novo && !simulado) return <p>Carregando...</p>;
  return <SimuladoEditor key={id} inicial={simulado} />;
}

function itemDe(q: { id: number; trecho: string; nomeMateria: string; status: string; anulada: boolean; desatualizada: boolean }): ItemSimulado {
  const motivo = motivoInelegivel(q);
  return { id: q.id, trecho: q.trecho, nomeMateria: q.nomeMateria, elegivel: motivo === null, motivo };
}

/** Formulario de montagem: dados, questoes selecionadas (em ordem) e busca de questoes publicadas. */
export function SimuladoEditor({ inicial }: { inicial: Simulado | null }) {
  const navegar = useNavigate();
  const [simulado, setSimulado] = useState<Simulado | null>(inicial);
  const [titulo, setTitulo] = useState(inicial?.titulo ?? '');
  const [slug, setSlug] = useState(inicial?.slug ?? '');
  const [descricao, setDescricao] = useState(inicial?.descricao ?? '');
  const [tempo, setTempo] = useState(inicial?.tempoSugeridoMin ? String(inicial.tempoSugeridoMin) : '');
  const [lista, setLista] = useState<ItemSimulado[]>(() => (inicial?.questoes ?? []).map(itemDe));
  const [arrastando, setArrastando] = useState<number | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

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

  function aplicar(s: Simulado) {
    setSimulado(s);
    setSlug(s.slug);
    setLista(s.questoes.map(itemDe));
  }

  function salvar(evento: FormEvent) {
    evento.preventDefault();
    executar(async () => {
      const dados = {
        titulo,
        slug,
        descricao: descricao.trim() ? descricao : null,
        tempoSugeridoMin: tempo ? Number(tempo) : null,
        idsQuestoes: lista.map((i) => i.id),
      };
      if (simulado) {
        aplicar(await simulados.atualizar(simulado.id, dados));
        return 'Simulado salvo.';
      }
      const criado = await simulados.criar(dados);
      navegar(`/simulados/${criado.id}`, { replace: true });
    });
  }

  const pendencias = pendenciasParaPublicar(lista);

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">{simulado ? `Simulado #${simulado.id}` : 'Novo simulado'}</h2>
        <Link to="/simulados" className="text-blue-700 underline">
          Voltar para a lista
        </Link>
      </div>

      {simulado && (
        <p className="text-sm text-slate-600">
          Status: <strong>{textoStatus(simulado.status)}</strong> · autor: {simulado.nomeAutor}
        </p>
      )}
      {simulado?.precisaRevisao && (
        <p role="status" className="rounded bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Precisa de revisão: alguma questão foi arquivada, anulada ou marcada como desatualizada depois da
          publicação. Ela não aparece no site. Troque ou remova a questão e salve.
        </p>
      )}
      <Mensagem erro={erro} sucesso={sucesso} />

      <form onSubmit={salvar} className="space-y-4">
        <Campo rotulo="Título" required maxLength={150} value={titulo} onChange={(e) => setTitulo(e.target.value)} />
        <Campo
          rotulo="Endereço (slug)"
          maxLength={150}
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          ajuda={
            simulado?.publicadoEm
              ? 'Não muda depois da primeira publicação.'
              : 'Opcional: vazio gera a partir do título.'
          }
          disabled={!!simulado?.publicadoEm}
        />
        <label className="block">
          <span className="text-sm font-medium">Descrição</span>
          <textarea
            value={descricao}
            maxLength={2000}
            rows={3}
            onChange={(e) => setDescricao(e.target.value)}
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
          />
        </label>
        <Campo
          rotulo="Tempo sugerido (minutos)"
          type="number"
          min={1}
          value={tempo}
          onChange={(e) => setTempo(e.target.value)}
          ajuda="Opcional: com valor, o simulado mostra um cronômetro."
        />

        <div>
          <h3 className="font-semibold">
            Questões do simulado ({lista.length} de no máximo {MAXIMO_QUESTOES})
          </h3>
          <p className="text-xs text-slate-500">Arraste para reordenar, ou use os botões ↑ e ↓.</p>
          {lista.length === 0 && <p className="mt-2 text-sm text-slate-600">Nenhuma questão. Use a busca abaixo.</p>}
          <ol className="mt-2 space-y-1" aria-label="Questões do simulado">
            {lista.map((item, i) => (
              <li
                key={item.id}
                draggable
                onDragStart={() => setArrastando(i)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => {
                  if (arrastando !== null) setLista(mover(lista, arrastando, i));
                  setArrastando(null);
                }}
                onDragEnd={() => setArrastando(null)}
                className={`flex flex-wrap items-center gap-2 rounded border px-3 py-2 text-sm ${
                  item.elegivel ? 'border-slate-200 bg-white' : 'border-red-300 bg-red-50'
                }`}
              >
                <span className="cursor-move select-none text-slate-400" aria-hidden="true">
                  ⋮⋮
                </span>
                <span className="w-8 font-medium">{i + 1}.</span>
                <span className="flex-1">
                  #{item.id} — {item.trecho} <span className="text-slate-500">({item.nomeMateria})</span>
                  {item.motivo && <strong className="ml-2 text-red-700">{item.motivo}</strong>}
                </span>
                <button
                  type="button"
                  aria-label={`Subir questão ${item.id}`}
                  disabled={i === 0}
                  onClick={() => setLista(mover(lista, i, i - 1))}
                  className="rounded border px-2 disabled:opacity-40"
                >
                  ↑
                </button>
                <button
                  type="button"
                  aria-label={`Descer questão ${item.id}`}
                  disabled={i === lista.length - 1}
                  onClick={() => setLista(mover(lista, i, i + 1))}
                  className="rounded border px-2 disabled:opacity-40"
                >
                  ↓
                </button>
                <button
                  type="button"
                  aria-label={`Remover questão ${item.id}`}
                  onClick={() => setLista(remover(lista, item.id))}
                  className="rounded border border-red-300 px-2 text-red-700"
                >
                  Remover
                </button>
              </li>
            ))}
          </ol>
        </div>

        <div className="flex flex-wrap gap-3">
          <Botao type="submit" disabled={ocupado}>
            Salvar
          </Botao>
          {simulado && simulado.status !== 'PUBLICADO' && (
            <button
              type="button"
              disabled={ocupado}
              onClick={() =>
                executar(async () => {
                  aplicar(await simulados.publicar(simulado.id));
                  return 'Simulado publicado.';
                })
              }
              className="rounded border border-green-600 px-4 py-2 text-green-800"
            >
              Publicar
            </button>
          )}
          {simulado && simulado.status !== 'ARQUIVADO' && simulado.publicadoEm && (
            <button
              type="button"
              disabled={ocupado}
              onClick={() =>
                executar(async () => {
                  aplicar(await simulados.arquivar(simulado.id));
                  return 'Simulado arquivado.';
                })
              }
              className="rounded border px-4 py-2"
            >
              Arquivar
            </button>
          )}
          {simulado && !simulado.publicadoEm && (
            <BotaoExcluir
              aoExcluir={() =>
                executar(async () => {
                  await simulados.excluir(simulado.id);
                  navegar('/simulados', { replace: true });
                })
              }
            />
          )}
        </div>
        {simulado && simulado.status !== 'PUBLICADO' && pendencias.length > 0 && (
          <ul className="list-disc pl-5 text-sm text-slate-600" aria-label="Pendências para publicar">
            {pendencias.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
      </form>

      <BuscaQuestoes
        selecionadas={lista}
        aoAdicionar={(q) => setLista((atual) => adicionar(atual, itemDe(q)))}
      />
    </section>
  );
}

/** Busca de questoes publicadas por materia e texto, para adicionar ao simulado (US11). */
function BuscaQuestoes({
  selecionadas,
  aoAdicionar,
}: {
  selecionadas: ItemSimulado[];
  aoAdicionar: (q: QuestaoResumo) => void;
}) {
  const [listaMaterias, setListaMaterias] = useState<Materia[]>([]);
  const [idMateria, setIdMateria] = useState('');
  const [busca, setBusca] = useState('');
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<QuestaoResumo> | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    materias
      .listar('', 0, 500)
      .then((p) => setListaMaterias(p.content))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    let ativo = true;
    const espera = setTimeout(() => {
      questoes
        .listar({ status: 'PUBLICADA', idMateria: idMateria ? Number(idMateria) : null, busca }, pagina)
        .then((d) => ativo && setDados(d))
        .catch((e: Error) => ativo && setErro(e.message));
    }, 250);
    return () => {
      ativo = false;
      clearTimeout(espera);
    };
  }, [idMateria, busca, pagina]);

  const cheio = selecionadas.length >= MAXIMO_QUESTOES;

  return (
    <section className="space-y-3 border-t border-slate-200 pt-4" aria-label="Adicionar questões">
      <h3 className="font-semibold">Adicionar questões publicadas</h3>
      <div className="flex flex-wrap gap-3">
        <select
          aria-label="Matéria das questões"
          value={idMateria}
          onChange={(e) => {
            setIdMateria(e.target.value);
            setPagina(0);
          }}
          className="rounded border border-slate-300 px-3 py-2"
        >
          <option value="">Todas as matérias</option>
          {listaMaterias.map((m) => (
            <option key={m.id} value={m.id}>
              {m.nome}
            </option>
          ))}
        </select>
        <input
          type="search"
          aria-label="Buscar no enunciado"
          placeholder="Buscar no enunciado..."
          value={busca}
          onChange={(e) => {
            setBusca(e.target.value);
            setPagina(0);
          }}
          className="w-full max-w-sm rounded border border-slate-300 px-3 py-2"
        />
      </div>
      <Mensagem erro={erro} />
      {dados && dados.content.length === 0 && <p className="text-sm text-slate-600">Nenhuma questão publicada.</p>}
      <ul className="space-y-1">
        {dados?.content.map((q) => {
          const jaEsta = selecionadas.some((s) => s.id === q.id);
          const motivo = motivoInelegivel(q);
          return (
            <li key={q.id} className="flex items-center gap-2 text-sm">
              <button
                type="button"
                aria-label={`Adicionar questão ${q.id}`}
                disabled={jaEsta || motivo !== null || cheio}
                onClick={() => aoAdicionar(q)}
                className="rounded border border-blue-600 px-2 text-blue-800 disabled:opacity-40"
              >
                {jaEsta ? 'Adicionada' : 'Adicionar'}
              </button>
              <span>
                #{q.id} — {q.trecho} <span className="text-slate-500">({q.nomeMateria})</span>
                {motivo && <strong className="ml-2 text-red-700">{motivo}</strong>}
              </span>
            </li>
          );
        })}
      </ul>
      {dados && dados.page.totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm">
          <button type="button" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)} className="underline disabled:opacity-40">
            Anterior
          </button>
          <span>
            Página {dados.page.number + 1} de {dados.page.totalPages}
          </span>
          <button
            type="button"
            disabled={pagina + 1 >= dados.page.totalPages}
            onClick={() => setPagina(pagina + 1)}
            className="underline disabled:opacity-40"
          >
            Próxima
          </button>
        </div>
      )}
    </section>
  );
}
