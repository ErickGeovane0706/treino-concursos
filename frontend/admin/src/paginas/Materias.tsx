import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { materias } from '../api';
import { Campo, ListaPaginada } from '../componentes';
import { Formulario, Marcador } from '../Formulario';

export function MateriasLista() {
  return (
    <ListaPaginada
      titulo="Matérias"
      carregar={materias.listar}
      colunas={[
        { titulo: 'Nome', valor: (m) => m.nome },
        { titulo: 'Endereço', valor: (m) => m.slug },
        { titulo: 'Situação', valor: (m) => (m.ativa ? 'Ativa' : 'Inativa') },
      ]}
      linkNovo="/materias/nova"
      textoNovo="Nova matéria"
      linkItem={(m) => `/materias/${m.id}`}
    />
  );
}

export function MateriaForm() {
  const { id } = useParams();
  const nova = id === 'nova';
  const navegar = useNavigate();
  const [nome, setNome] = useState('');
  const [slug, setSlug] = useState('');
  const [ativa, setAtiva] = useState(true);

  useEffect(() => {
    if (nova) return;
    materias.buscar(Number(id)).then((m) => {
      setNome(m.nome);
      setSlug(m.slug);
      setAtiva(m.ativa);
    });
  }, [id, nova]);

  return (
    <div className="space-y-6">
      <Formulario
        titulo={nova ? 'Nova matéria' : 'Editar matéria'}
        voltarPara="/materias"
        aoSalvar={async () => {
          const dados = { nome: nome.trim(), slug: slug.trim(), ativa };
          if (nova) {
            const criada = await materias.criar(dados);
            navegar(`/materias/${criada.id}`);
            return;
          }
          const salva = await materias.atualizar(Number(id), dados);
          setSlug(salva.slug);
          return 'Matéria salva.';
        }}
        aoExcluir={
          nova
            ? undefined
            : async () => {
                await materias.excluir(Number(id));
                navegar('/materias');
              }
        }
      >
        <Campo rotulo="Nome" required maxLength={100} value={nome} onChange={(e) => setNome(e.target.value)} />
        <Campo
          rotulo="Endereço (slug)"
          maxLength={120}
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          ajuda="Deixe vazio para gerar do nome. Depois que a matéria tiver questões publicadas, ele não pode mudar."
        />
        <Marcador rotulo="Ativa (inative em vez de excluir se já houver questões)" marcado={ativa} aoMudar={setAtiva} />
      </Formulario>
      {!nova && (
        <Link to={`/materias/${id}/assuntos`} className="inline-block text-blue-700 underline">
          Assuntos desta matéria →
        </Link>
      )}
    </div>
  );
}
