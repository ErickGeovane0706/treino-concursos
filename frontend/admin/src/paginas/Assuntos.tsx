import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { assuntos, materias, type Assunto, type Materia } from '../api';
import { Campo, Mensagem } from '../componentes';
import { Formulario, Marcador, Selecao } from '../Formulario';
import { ordenarComoArvore } from './arvore';

/** Assuntos de uma materia em arvore (no maximo 3 niveis, RF02 RN2). */
export function AssuntosLista() {
  const idMateria = Number(useParams().idMateria);
  const [materia, setMateria] = useState<Materia | null>(null);
  const [lista, setLista] = useState<Assunto[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    materias.buscar(idMateria).then(setMateria).catch((e: Error) => setErro(e.message));
    assuntos.listar(idMateria).then((a) => setLista(ordenarComoArvore(a))).catch((e: Error) => setErro(e.message));
  }, [idMateria]);

  return (
    <section className="space-y-4">
      <Link to={`/materias/${idMateria}`} className="text-sm text-blue-700 underline">
        ← {materia?.nome ?? 'Matéria'}
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Assuntos de {materia?.nome}</h2>
        <Link to={`/materias/${idMateria}/assuntos/novo`} className="rounded bg-blue-700 px-4 py-2 font-medium text-white">
          Novo assunto
        </Link>
      </div>
      <Mensagem erro={erro} />
      {lista.length === 0 && !erro && <p className="text-slate-600">Nenhum assunto cadastrado.</p>}
      <ul className="space-y-1">
        {lista.map((a) => (
          <li key={a.id} style={{ paddingLeft: `${(a.nivel - 1) * 1.5}rem` }}>
            <Link to={`/materias/${idMateria}/assuntos/${a.id}`} className="text-blue-700 underline">
              {a.nome}
            </Link>{' '}
            <span className="text-xs text-slate-500">
              {a.slug}
              {!a.ativo && ' · inativo'}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function AssuntoForm() {
  const params = useParams();
  const idMateria = Number(params.idMateria);
  const novo = params.id === 'novo';
  const navegar = useNavigate();
  const voltar = `/materias/${idMateria}/assuntos`;
  const [todos, setTodos] = useState<Assunto[]>([]);
  const [nome, setNome] = useState('');
  const [slug, setSlug] = useState('');
  const [pai, setPai] = useState('');
  const [ativo, setAtivo] = useState(true);

  useEffect(() => {
    assuntos.listar(idMateria).then((a) => setTodos(ordenarComoArvore(a)));
    if (novo) return;
    assuntos.buscar(idMateria, Number(params.id)).then((a) => {
      setNome(a.nome);
      setSlug(a.slug);
      setPai(a.idAssuntoPai === null ? '' : String(a.idAssuntoPai));
      setAtivo(a.ativo);
    });
  }, [idMateria, params.id, novo]);

  // Pai possivel: outro assunto ate o nivel 2 (o filho fica no maximo no nivel 3).
  const opcoesPai = todos
    .filter((a) => a.id !== Number(params.id) && a.nivel < 3)
    .map((a) => ({ valor: String(a.id), texto: `${'— '.repeat(a.nivel - 1)}${a.nome}` }));

  return (
    <Formulario
      titulo={novo ? 'Novo assunto' : 'Editar assunto'}
      voltarPara={voltar}
      aoSalvar={async () => {
        const dados = { nome: nome.trim(), slug: slug.trim(), idAssuntoPai: pai ? Number(pai) : null, ativo };
        if (novo) {
          await assuntos.criar(idMateria, dados);
          navegar(voltar);
          return;
        }
        const salvo = await assuntos.atualizar(idMateria, Number(params.id), dados);
        setSlug(salvo.slug);
        return 'Assunto salvo.';
      }}
      aoExcluir={
        novo
          ? undefined
          : async () => {
              await assuntos.excluir(idMateria, Number(params.id));
              navegar(voltar);
            }
      }
    >
      <Campo rotulo="Nome" required maxLength={150} value={nome} onChange={(e) => setNome(e.target.value)} />
      <Campo
        rotulo="Endereço (slug)"
        maxLength={170}
        value={slug}
        onChange={(e) => setSlug(e.target.value)}
        ajuda="Deixe vazio para gerar do nome. Depois que o assunto tiver questões publicadas, ele não pode mudar."
      />
      <Selecao rotulo="Assunto pai" obrigatorio={false} valor={pai} aoMudar={setPai} opcoes={opcoesPai} />
      <Marcador rotulo="Ativo" marcado={ativo} aoMudar={setAtivo} />
    </Formulario>
  );
}
