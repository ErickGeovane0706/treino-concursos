import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router';
import { api, ErroApi, type Usuario } from './api';
import { Login } from './Login';
import { AssuntoForm, AssuntosLista } from './paginas/Assuntos';
import { BancaForm, BancasLista } from './paginas/Bancas';
import { MateriaForm, MateriasLista } from './paginas/Materias';
import { OrgaoForm, OrgaosLista } from './paginas/Orgaos';
import { ProvaForm, ProvasLista } from './paginas/Provas';
import { QuestaoFormRota, QuestoesLista } from './paginas/Questoes';
import { ReportesFila } from './paginas/Reportes';
import { Usuarios } from './Usuarios';

// SPA do painel: sem sessao mostra o login; com sessao, o painel com as rotas.
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

  const admin = usuario.perfil === 'ADMIN';
  const itens = [
    { para: '/questoes', texto: 'Questões' },
    { para: '/reportes', texto: 'Reportes' },
    { para: '/materias', texto: 'Matérias' },
    { para: '/bancas', texto: 'Bancas' },
    { para: '/orgaos', texto: 'Órgãos' },
    { para: '/provas', texto: 'Provas' },
    ...(admin ? [{ para: '/usuarios', texto: 'Usuários' }] : []),
  ];

  return (
    <div className="mx-auto max-w-5xl p-6">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-4">
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
      <nav aria-label="Seções do painel" className="mb-8 flex flex-wrap gap-4">
        {itens.map((i) => (
          <NavLink
            key={i.para}
            to={i.para}
            className={({ isActive }) => (isActive ? 'font-semibold text-blue-800 underline' : 'text-blue-700')}
          >
            {i.texto}
          </NavLink>
        ))}
      </nav>
      <Routes>
        <Route path="/" element={<Navigate to="/questoes" replace />} />
        <Route path="/questoes" element={<QuestoesLista />} />
        <Route path="/questoes/:id" element={<QuestaoFormRota />} />
        <Route path="/reportes" element={<ReportesFila />} />
        <Route path="/materias" element={<MateriasLista />} />
        <Route path="/materias/:id" element={<MateriaForm />} />
        <Route path="/materias/:idMateria/assuntos" element={<AssuntosLista />} />
        <Route path="/materias/:idMateria/assuntos/:id" element={<AssuntoForm />} />
        <Route path="/bancas" element={<BancasLista />} />
        <Route path="/bancas/:id" element={<BancaForm />} />
        <Route path="/orgaos" element={<OrgaosLista />} />
        <Route path="/orgaos/:id" element={<OrgaoForm />} />
        <Route path="/provas" element={<ProvasLista />} />
        <Route path="/provas/:id" element={<ProvaForm />} />
        {admin && <Route path="/usuarios" element={<Usuarios idLogado={usuario.id} />} />}
        <Route path="*" element={<p>Página não encontrada.</p>} />
      </Routes>
    </div>
  );
}
