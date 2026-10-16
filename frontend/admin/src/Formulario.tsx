import { useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { BotaoExcluir, Botao, Mensagem } from './componentes';

/**
 * Casco dos formularios do painel: salvar, excluir, voltar e mensagens.
 * O erro mostrado e o "detail" do Problem Details devolvido pela API.
 */
export function Formulario({
  titulo,
  voltarPara,
  aoSalvar,
  aoExcluir,
  children,
}: {
  titulo: string;
  voltarPara: string;
  aoSalvar: () => Promise<string | void>;
  aoExcluir?: () => Promise<void>;
  children: ReactNode;
}) {
  const [erro, setErro] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  async function executar(acao: () => Promise<string | void>) {
    setErro(null);
    setSucesso(null);
    setSalvando(true);
    try {
      const mensagem = await acao();
      if (mensagem) setSucesso(mensagem);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    executar(aoSalvar);
  }

  return (
    <section className="max-w-xl space-y-4">
      <Link to={voltarPara} className="text-sm text-blue-700 underline">
        ← Voltar
      </Link>
      <h2 className="text-xl font-semibold">{titulo}</h2>
      <form onSubmit={enviar} className="space-y-4">
        {children}
        <Mensagem erro={erro} sucesso={sucesso} />
        <div className="flex flex-wrap gap-3">
          <Botao type="submit" disabled={salvando}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </Botao>
          {aoExcluir && <BotaoExcluir aoExcluir={() => executar(aoExcluir)} />}
        </div>
      </form>
    </section>
  );
}

export function Selecao({
  rotulo,
  valor,
  aoMudar,
  opcoes,
  obrigatorio = true,
}: {
  rotulo: string;
  valor: string;
  aoMudar: (valor: string) => void;
  opcoes: { valor: string; texto: string }[];
  obrigatorio?: boolean;
}) {
  return (
    <label className="block">
      <span className="text-sm font-medium">{rotulo}</span>
      <select
        required={obrigatorio}
        value={valor}
        onChange={(e) => aoMudar(e.target.value)}
        className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
      >
        <option value="">{obrigatorio ? 'Selecione...' : '(nenhum)'}</option>
        {opcoes.map((o) => (
          <option key={o.valor} value={o.valor}>
            {o.texto}
          </option>
        ))}
      </select>
    </label>
  );
}

export function Marcador({ rotulo, marcado, aoMudar }: { rotulo: string; marcado: boolean; aoMudar: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-2">
      <input type="checkbox" checked={marcado} onChange={(e) => aoMudar(e.target.checked)} />
      <span className="text-sm">{rotulo}</span>
    </label>
  );
}
