import { useEffect, useState } from 'react';
import { api, ErroApi, type Usuario } from './api';
import { Login } from './Login';
import { Usuarios } from './Usuarios';

// SPA do painel: sem sessao mostra o login; com sessao, o painel.
// As telas de conteudo (classificacoes, questoes) chegam a partir da issue #4.
export function App() {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    api
      .eu()
      .then(setUsuario)
      .catch((e) => {
        if (!(e instanceof ErroApi && e.status === 401)) setErro((e as Error).message);
      })
      .finally(() => setCarregando(false));
  }, []);

  async function sair() {
    await api.sair().catch(() => undefined);
    setUsuario(null);
  }

  if (carregando) return <p className="p-6">Carregando...</p>;
  if (erro) return <p role="alert" className="p-6 text-red-700">{erro}</p>;
  if (!usuario) return <Login aoEntrar={setUsuario} />;

  return (
    <div className="mx-auto max-w-5xl p-6">
      <header className="mb-8 flex items-center justify-between border-b border-slate-200 pb-4">
        <h1 className="text-2xl font-semibold">Painel do Treino de Concursos</h1>
        <div className="flex items-center gap-3 text-sm">
          <span>
            {usuario.nome} ({usuario.perfil})
          </span>
          <button onClick={sair} className="rounded border px-3 py-1">
            Sair
          </button>
        </div>
      </header>
      {usuario.perfil === 'ADMIN' ? (
        <Usuarios idLogado={usuario.id} />
      ) : (
        <p>Bem-vindo! As telas de cadastro chegam nas próximas versões.</p>
      )}
    </div>
  );
}
