import { useState, type FormEvent } from 'react';
import { api, type Usuario } from './api';

interface Props {
  aoEntrar: (usuario: Usuario) => void;
}

export function Login({ aoEntrar }: Props) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      aoEntrar(await api.entrar(email.trim(), senha));
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Erro inesperado. Tente novamente.');
      setSenha('');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="mx-auto mt-16 max-w-sm p-6">
      <h1 className="mb-6 text-2xl font-semibold">Painel do Treino de Concursos</h1>
      <form onSubmit={enviar} className="space-y-4">
        <label className="block">
          <span className="text-sm font-medium">E-mail</span>
          <input
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
          />
        </label>
        <label className="block">
          <span className="text-sm font-medium">Senha</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
          />
        </label>
        {erro && (
          <p role="alert" className="rounded bg-red-50 px-3 py-2 text-sm text-red-700">
            {erro}
          </p>
        )}
        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white disabled:opacity-60"
        >
          {enviando ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </main>
  );
}
