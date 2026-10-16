import { useEffect, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react';
import { Link } from 'react-router';
import type { Pagina } from './api';

export function Mensagem({ erro, sucesso }: { erro?: string | null; sucesso?: string | null }) {
  if (erro) {
    return (
      <p role="alert" className="rounded bg-red-50 px-3 py-2 text-sm text-red-700">
        {erro}
      </p>
    );
  }
  if (sucesso) {
    return (
      <p role="status" className="rounded bg-green-50 px-3 py-2 text-sm text-green-800">
        {sucesso}
      </p>
    );
  }
  return null;
}

/** Campo de formulario com rotulo e ajuda. As regras de validacao vao nos atributos HTML. */
export function Campo({ rotulo, ajuda, ...props }: { rotulo: string; ajuda?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="text-sm font-medium">{rotulo}</span>
      <input {...props} className="mt-1 w-full rounded border border-slate-300 px-3 py-2 invalid:border-red-400" />
      {ajuda && <span className="mt-1 block text-xs text-slate-500">{ajuda}</span>}
    </label>
  );
}

export function Botao({ children, ...props }: { children: ReactNode } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button {...props} className="rounded bg-blue-700 px-4 py-2 font-medium text-white disabled:opacity-60">
      {children}
    </button>
  );
}

/** Excluir em dois cliques, sem caixa de dialogo do navegador. */
export function BotaoExcluir({ aoExcluir }: { aoExcluir: () => void }) {
  const [confirmando, setConfirmando] = useState(false);
  if (!confirmando) {
    return (
      <button type="button" onClick={() => setConfirmando(true)} className="rounded border border-red-300 px-4 py-2 text-red-700">
        Excluir
      </button>
    );
  }
  return (
    <span className="flex gap-2">
      <button type="button" onClick={aoExcluir} className="rounded bg-red-700 px-4 py-2 font-medium text-white">
        Confirmar exclusão
      </button>
      <button type="button" onClick={() => setConfirmando(false)} className="rounded border px-4 py-2">
        Cancelar
      </button>
    </span>
  );
}

interface Coluna<T> {
  titulo: string;
  valor: (item: T) => ReactNode;
}

/** Lista paginada com busca, usada por materias, bancas, orgaos e provas. */
export function ListaPaginada<T extends { id: number }>({
  titulo,
  carregar,
  colunas,
  linkNovo,
  textoNovo,
  linkItem,
}: {
  titulo: string;
  carregar: (busca: string, pagina: number) => Promise<Pagina<T>>;
  colunas: Coluna<T>[];
  linkNovo: string;
  textoNovo: string;
  linkItem: (item: T) => string;
}) {
  const [busca, setBusca] = useState('');
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<T> | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let ativo = true;
    const espera = setTimeout(() => {
      carregar(busca, pagina)
        .then((d) => ativo && setDados(d))
        .catch((e: Error) => ativo && setErro(e.message));
    }, 250);
    return () => {
      ativo = false;
      clearTimeout(espera);
    };
  }, [busca, pagina, carregar]);

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">{titulo}</h2>
        <Link to={linkNovo} className="rounded bg-blue-700 px-4 py-2 font-medium text-white">
          {textoNovo}
        </Link>
      </div>
      <input
        type="search"
        placeholder="Buscar..."
        aria-label="Buscar"
        value={busca}
        onChange={(e) => {
          setBusca(e.target.value);
          setPagina(0);
        }}
        className="w-full max-w-sm rounded border border-slate-300 px-3 py-2"
      />
      <Mensagem erro={erro} />
      {dados && dados.content.length === 0 && <p className="text-slate-600">Nada encontrado.</p>}
      {dados && dados.content.length > 0 && (
        <>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-300">
              <tr>
                {colunas.map((c) => (
                  <th key={c.titulo} className="py-2 pr-4">
                    {c.titulo}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dados.content.map((item) => (
                <tr key={item.id} className="border-b border-slate-200">
                  {colunas.map((c, i) => (
                    <td key={c.titulo} className="py-2 pr-4">
                      {i === 0 ? (
                        <Link to={linkItem(item)} className="text-blue-700 underline">
                          {c.valor(item)}
                        </Link>
                      ) : (
                        c.valor(item)
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center gap-3 text-sm">
            <button disabled={pagina === 0} onClick={() => setPagina(pagina - 1)} className="underline disabled:opacity-40">
              Anterior
            </button>
            <span>
              Página {dados.page.number + 1} de {Math.max(1, dados.page.totalPages)}
            </span>
            <button
              disabled={pagina + 1 >= dados.page.totalPages}
              onClick={() => setPagina(pagina + 1)}
              className="underline disabled:opacity-40"
            >
              Próxima
            </button>
          </div>
        </>
      )}
    </section>
  );
}
