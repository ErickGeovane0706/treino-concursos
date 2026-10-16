import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { bancas } from '../api';
import { Campo, ListaPaginada } from '../componentes';
import { Formulario } from '../Formulario';

export function BancasLista() {
  return (
    <ListaPaginada
      titulo="Bancas"
      carregar={bancas.listar}
      colunas={[
        { titulo: 'Sigla', valor: (b) => b.sigla },
        { titulo: 'Nome', valor: (b) => b.nome },
        { titulo: 'Endereço', valor: (b) => b.slug },
      ]}
      linkNovo="/bancas/nova"
      textoNovo="Nova banca"
      linkItem={(b) => `/bancas/${b.id}`}
    />
  );
}

export function BancaForm() {
  const { id } = useParams();
  const nova = id === 'nova';
  const navegar = useNavigate();
  const [nome, setNome] = useState('');
  const [sigla, setSigla] = useState('');
  const [slug, setSlug] = useState('');

  useEffect(() => {
    if (nova) return;
    bancas.buscar(Number(id)).then((b) => {
      setNome(b.nome);
      setSigla(b.sigla);
      setSlug(b.slug);
    });
  }, [id, nova]);

  return (
    <Formulario
      titulo={nova ? 'Nova banca' : 'Editar banca'}
      voltarPara="/bancas"
      aoSalvar={async () => {
        const dados = { nome: nome.trim(), sigla: sigla.trim(), slug: slug.trim() };
        if (nova) {
          await bancas.criar(dados);
          navegar('/bancas');
          return;
        }
        setSlug((await bancas.atualizar(Number(id), dados)).slug);
        return 'Banca salva.';
      }}
      aoExcluir={
        nova
          ? undefined
          : async () => {
              await bancas.excluir(Number(id));
              navegar('/bancas');
            }
      }
    >
      <Campo rotulo="Nome" required maxLength={100} value={nome} onChange={(e) => setNome(e.target.value)} />
      <Campo rotulo="Sigla" required maxLength={30} value={sigla} onChange={(e) => setSigla(e.target.value)} />
      <Campo
        rotulo="Endereço (slug)"
        maxLength={120}
        value={slug}
        onChange={(e) => setSlug(e.target.value)}
        ajuda="Deixe vazio para gerar da sigla."
      />
    </Formulario>
  );
}
