import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, type Pagina, type Perfil, type Usuario } from './api';

/** Tela de usuarios do painel (US13). So aparece para ADMIN. */
export function Usuarios({ idLogado }: { idLogado: number }) {
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<Usuario> | null>(null);
  const [mensagem, setMensagem] = useState<{ tipo: 'erro' | 'ok'; texto: string } | null>(null);

  const carregar = useCallback(async () => {
    try {
      setDados(await api.listarUsuarios(pagina));
    } catch (e) {
      setMensagem({ tipo: 'erro', texto: (e as Error).message });
    }
  }, [pagina]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function executar(acao: () => Promise<unknown>, sucesso: string) {
    setMensagem(null);
    try {
      await acao();
      setMensagem({ tipo: 'ok', texto: sucesso });
      await carregar();
    } catch (e) {
      setMensagem({ tipo: 'erro', texto: (e as Error).message });
    }
  }

  return (
    <section className="space-y-6">
      <h2 className="text-xl font-semibold">Usuários do painel</h2>

      {mensagem && (
        <p
          role={mensagem.tipo === 'erro' ? 'alert' : 'status'}
          className={`rounded px-3 py-2 text-sm ${mensagem.tipo === 'erro' ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-800'}`}
        >
          {mensagem.texto}
        </p>
      )}

      <NovoUsuario aoCriar={(d) => executar(() => api.criarUsuario(d), 'Usuário criado.')} />

      {dados && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-300">
              <tr>
                <th className="py-2 pr-4">Nome</th>
                <th className="py-2 pr-4">E-mail</th>
                <th className="py-2 pr-4">Perfil</th>
                <th className="py-2 pr-4">Situação</th>
                <th className="py-2">Ações</th>
              </tr>
            </thead>
            <tbody>
              {dados.content.map((u) => (
                <LinhaUsuario key={u.id} usuario={u} ehVoce={u.id === idLogado} executar={executar} />
              ))}
            </tbody>
          </table>
          <div className="mt-3 flex items-center gap-3 text-sm">
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
        </div>
      )}
    </section>
  );
}

function LinhaUsuario({
  usuario,
  ehVoce,
  executar,
}: {
  usuario: Usuario;
  ehVoce: boolean;
  executar: (acao: () => Promise<unknown>, sucesso: string) => Promise<void>;
}) {
  const [novaSenha, setNovaSenha] = useState('');
  const bloqueado = usuario.bloqueadoAte !== null && new Date(usuario.bloqueadoAte) > new Date();
  const outroPerfil: Perfil = usuario.perfil === 'ADMIN' ? 'EDITOR' : 'ADMIN';

  return (
    <tr className="border-b border-slate-200 align-top">
      <td className="py-2 pr-4">
        {usuario.nome}
        {ehVoce && <span className="ml-1 text-slate-500">(você)</span>}
      </td>
      <td className="py-2 pr-4">{usuario.email}</td>
      <td className="py-2 pr-4">{usuario.perfil}</td>
      <td className="py-2 pr-4">{!usuario.ativo ? 'Inativo' : bloqueado ? 'Bloqueado' : 'Ativo'}</td>
      <td className="space-y-1 py-2">
        <div className="flex flex-wrap gap-2">
          <button
            className="rounded border px-2 py-1"
            onClick={() =>
              executar(
                () => api.atualizarUsuario(usuario.id, { nome: usuario.nome, email: usuario.email, perfil: outroPerfil }),
                `Perfil alterado para ${outroPerfil}.`,
              )
            }
          >
            Tornar {outroPerfil}
          </button>
          {usuario.ativo ? (
            <button className="rounded border px-2 py-1" onClick={() => executar(() => api.inativarUsuario(usuario.id), 'Usuário inativado.')}>
              Inativar
            </button>
          ) : (
            <button className="rounded border px-2 py-1" onClick={() => executar(() => api.reativarUsuario(usuario.id), 'Usuário reativado.')}>
              Reativar
            </button>
          )}
          {bloqueado && (
            <button className="rounded border px-2 py-1" onClick={() => executar(() => api.desbloquearUsuario(usuario.id), 'Conta desbloqueada.')}>
              Desbloquear
            </button>
          )}
        </div>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            executar(() => api.redefinirSenha(usuario.id, novaSenha), 'Senha redefinida.').then(() => setNovaSenha(''));
          }}
        >
          <input
            type="password"
            autoComplete="new-password"
            placeholder="Nova senha"
            minLength={8}
            maxLength={72}
            required
            value={novaSenha}
            onChange={(e) => setNovaSenha(e.target.value)}
            aria-label={`Nova senha de ${usuario.nome}`}
            className="w-36 rounded border border-slate-300 px-2 py-1"
          />
          <button type="submit" className="rounded border px-2 py-1">
            Redefinir senha
          </button>
        </form>
      </td>
    </tr>
  );
}

function NovoUsuario({ aoCriar }: { aoCriar: (d: { nome: string; email: string; senha: string; perfil: Perfil }) => Promise<void> }) {
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [perfil, setPerfil] = useState<Perfil>('EDITOR');

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    await aoCriar({ nome: nome.trim(), email: email.trim(), senha, perfil });
    setNome('');
    setEmail('');
    setSenha('');
  }

  return (
    <form onSubmit={enviar} className="grid gap-3 rounded border border-slate-200 p-4 sm:grid-cols-5">
      <input required maxLength={100} placeholder="Nome" aria-label="Nome" value={nome} onChange={(e) => setNome(e.target.value)} className="rounded border border-slate-300 px-2 py-1" />
      <input required type="email" maxLength={150} placeholder="E-mail" aria-label="E-mail" value={email} onChange={(e) => setEmail(e.target.value)} className="rounded border border-slate-300 px-2 py-1" />
      <input required type="password" autoComplete="new-password" minLength={8} maxLength={72} placeholder="Senha (mín. 8)" aria-label="Senha" value={senha} onChange={(e) => setSenha(e.target.value)} className="rounded border border-slate-300 px-2 py-1" />
      <select aria-label="Perfil" value={perfil} onChange={(e) => setPerfil(e.target.value as Perfil)} className="rounded border border-slate-300 px-2 py-1">
        <option value="EDITOR">EDITOR</option>
        <option value="ADMIN">ADMIN</option>
      </select>
      <button type="submit" className="rounded bg-blue-700 px-3 py-1 font-medium text-white">
        Criar usuário
      </button>
    </form>
  );
}
