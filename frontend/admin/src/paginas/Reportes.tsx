import { useCallback, useEffect, useState } from 'react';
import {
  reportesErro,
  type MotivoReporte,
  type Pagina,
  type ReporteErro,
  type StatusReporte,
} from '../api';
import { Mensagem } from '../componentes';
import { QuestaoEditorPainel } from './Questoes';

const MOTIVOS: Record<MotivoReporte, string> = {
  GABARITO_ERRADO: 'Gabarito errado',
  ENUNCIADO_COM_ERRO: 'Enunciado com erro',
  COMENTARIO_INCORRETO: 'Comentário incorreto',
  QUESTAO_DESATUALIZADA: 'Questão desatualizada',
  OUTRO: 'Outro motivo',
};

const STATUS: { valor: StatusReporte; texto: string }[] = [
  { valor: 'ABERTO', texto: 'Aberto' },
  { valor: 'RESOLVIDO', texto: 'Resolvido' },
  { valor: 'DESCARTADO', texto: 'Descartado' },
];

const textoStatus = (s: StatusReporte) => STATUS.find((x) => x.valor === s)?.texto ?? s;

/**
 * Fila de reportes de erro (RF16 RN4-RN5, US12).
 * Exibe no topo as questoes com 3+ reportes abertos de gabarito errado e abre
 * a questao ao lado do reporte para o editor corrigir sem trocar de tela.
 */
export function ReportesFila() {
  const [statusFiltro, setStatusFiltro] = useState<StatusReporte | ''>('ABERTO');
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<ReporteErro> | null>(null);
  const [selecionado, setSelecionado] = useState<ReporteErro | null>(null);
  const [observacao, setObservacao] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState<string | null>(null);

  const carregarFila = useCallback(
    async (manterIdSelecionado?: number | null) => {
      try {
        const resposta = await reportesErro.listar(statusFiltro, pagina);
        setDados(resposta);
        if (manterIdSelecionado !== undefined) {
          const atualizado = resposta.content.find((r) => r.id === manterIdSelecionado) ?? null;
          setSelecionado(atualizado);
        }
      } catch (e) {
        setErro((e as Error).message);
      }
    },
    [statusFiltro, pagina],
  );

  useEffect(() => {
    void carregarFila();
  }, [carregarFila]);

  function abrirReporte(reporte: ReporteErro) {
    setSelecionado(reporte);
    setObservacao(reporte.observacaoResolucao ?? '');
    setErro(null);
    setSucesso(null);
  }

  async function encerrar(acao: 'resolver' | 'descartar') {
    if (!selecionado || ocupado) return;
    setErro(null);
    setSucesso(null);
    setOcupado(true);
    try {
      const obs = observacao.trim() ? observacao.trim() : null;
      const atualizado =
        acao === 'resolver'
          ? await reportesErro.resolver(selecionado.id, obs)
          : await reportesErro.descartar(selecionado.id, obs);
      setSucesso(
        acao === 'resolver'
          ? `Reporte #${atualizado.id} marcado como resolvido.`
          : `Reporte #${atualizado.id} descartado.`,
      );
      setObservacao('');
      setSelecionado(atualizado);
      const resposta = await reportesErro.listar(statusFiltro, pagina);
      setDados(resposta);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Fila de reportes de erro</h2>
        <select
          aria-label="Status dos reportes"
          value={statusFiltro}
          onChange={(e) => {
            setStatusFiltro(e.target.value as StatusReporte | '');
            setPagina(0);
            setSelecionado(null);
          }}
          className="rounded border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="ABERTO">Abertos (fila)</option>
          <option value="RESOLVIDO">Resolvidos</option>
          <option value="DESCARTADO">Descartados</option>
          <option value="">Todos os status</option>
        </select>
      </div>

      <Mensagem erro={erro} sucesso={sucesso} />

      {dados && dados.content.length === 0 && <p className="text-slate-600">Nenhum reporte encontrado.</p>}

      {dados && dados.content.length > 0 && (
        <>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-300">
              <tr>
                <th className="py-2 pr-4">Questão</th>
                <th className="py-2 pr-4">Motivo</th>
                <th className="py-2 pr-4">Prioridade</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 pr-4">Ação</th>
              </tr>
            </thead>
            <tbody>
              {dados.content.map((r) => {
                const ativo = selecionado?.id === r.id;
                return (
                  <tr
                    key={r.id}
                    className={`border-b border-slate-200 ${ativo ? 'bg-blue-50' : ''}`}
                    data-reporte-linha={r.id}
                  >
                    <td className="py-2 pr-4">
                      <button
                        type="button"
                        onClick={() => abrirReporte(r)}
                        className="text-left font-medium text-blue-700 underline"
                      >
                        #{r.idQuestao} — {r.trechoQuestao}
                      </button>
                      <span className="block text-xs text-slate-500">{r.nomeMateria}</span>
                    </td>
                    <td className="py-2 pr-4">
                      <span>{MOTIVOS[r.motivo]}</span>
                      {r.descricao && <span className="block text-xs text-slate-600">{r.descricao}</span>}
                    </td>
                    <td className="py-2 pr-4">
                      {r.prioritario ? (
                        <span
                          className="inline-block rounded bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800"
                          data-prioridade={r.id}
                        >
                          Prioridade ({r.reportesGabaritoAbertos} de gabarito)
                        </span>
                      ) : (
                        <span className="text-xs text-slate-500">Normal</span>
                      )}
                    </td>
                    <td className="py-2 pr-4">{textoStatus(r.status)}</td>
                    <td className="py-2 pr-4">
                      <button
                        type="button"
                        onClick={() => abrirReporte(r)}
                        className="rounded border border-blue-700 px-3 py-1 text-xs font-medium text-blue-700"
                      >
                        {ativo ? 'Aberto ao lado' : 'Abrir questão'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          <div className="flex items-center gap-3 text-sm">
            <button
              type="button"
              disabled={pagina === 0}
              onClick={() => setPagina(pagina - 1)}
              className="underline disabled:opacity-40"
            >
              Anterior
            </button>
            <span>
              Página {dados.page.number + 1} de {Math.max(1, dados.page.totalPages)}
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
        </>
      )}

      {selecionado && (
        <div
          className="grid gap-6 border-t border-slate-300 pt-6 lg:grid-cols-2"
          data-painel-tratamento
        >
          <div className="space-y-4 rounded border border-slate-200 bg-slate-50 p-4 self-start">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-lg font-semibold">Reporte #{selecionado.id}</h3>
              {selecionado.prioritario && (
                <span className="rounded bg-red-100 px-2 py-1 text-xs font-semibold text-red-800">
                  Prioridade: {selecionado.reportesGabaritoAbertos} reportes de gabarito errado
                </span>
              )}
            </div>

            <dl className="space-y-2 text-sm">
              <div>
                <dt className="font-medium text-slate-600">Questão</dt>
                <dd>
                  #{selecionado.idQuestao} ({selecionado.nomeMateria})
                </dd>
              </div>
              <div>
                <dt className="font-medium text-slate-600">Motivo informado</dt>
                <dd>{MOTIVOS[selecionado.motivo]}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-600">Descrição do visitante</dt>
                <dd>{selecionado.descricao ?? '(sem descrição)'}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-600">Status</dt>
                <dd>{textoStatus(selecionado.status)}</dd>
              </div>
              {selecionado.status !== 'ABERTO' && (
                <>
                  <div>
                    <dt className="font-medium text-slate-600">Encerrado por</dt>
                    <dd>{selecionado.nomeResolvidoPor ?? '—'}</dd>
                  </div>
                  <div>
                    <dt className="font-medium text-slate-600">Observação da resolução</dt>
                    <dd>{selecionado.observacaoResolucao ?? '(sem observação)'}</dd>
                  </div>
                </>
              )}
            </dl>

            {selecionado.status === 'ABERTO' && (
              <div className="space-y-3 border-t border-slate-200 pt-3">
                <label className="block">
                  <span className="text-sm font-medium">Observação da resolução</span>
                  <textarea
                    aria-label="Observação da resolução"
                    rows={3}
                    maxLength={1000}
                    value={observacao}
                    onChange={(e) => setObservacao(e.target.value)}
                    placeholder="Anote o que foi corrigido ou por que o reporte foi descartado"
                    className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm"
                  />
                </label>

                <div className="flex flex-wrap gap-3">
                  <button
                    type="button"
                    disabled={ocupado}
                    onClick={() => void encerrar('resolver')}
                    className="rounded bg-green-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                  >
                    Resolver reporte
                  </button>
                  <button
                    type="button"
                    disabled={ocupado}
                    onClick={() => void encerrar('descartar')}
                    className="rounded border border-slate-400 bg-white px-4 py-2 text-sm font-medium text-slate-800 disabled:opacity-60"
                  >
                    Descartar reporte
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="rounded border border-slate-200 bg-white p-4" data-editor-lado-a-lado>
            <QuestaoEditorPainel
              idQuestao={selecionado.idQuestao}
              aoAtualizar={() => {
                void carregarFila(selecionado.id);
              }}
            />
          </div>
        </div>
      )}
    </section>
  );
}
