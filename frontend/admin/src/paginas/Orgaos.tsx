import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { orgaos, type Esfera } from '../api';
import { Campo, ListaPaginada } from '../componentes';
import { Formulario, Selecao } from '../Formulario';

const ESFERAS: { valor: Esfera; texto: string }[] = [
  { valor: 'FEDERAL', texto: 'Federal' },
  { valor: 'ESTADUAL', texto: 'Estadual' },
  { valor: 'MUNICIPAL', texto: 'Municipal' },
];

export function OrgaosLista() {
  return (
    <ListaPaginada
      titulo="Órgãos"
      carregar={orgaos.listar}
      colunas={[
        { titulo: 'Sigla', valor: (o) => o.sigla },
        { titulo: 'Nome', valor: (o) => o.nome },
        { titulo: 'Esfera', valor: (o) => `${o.esfera}${o.uf ? ` (${o.uf})` : ''}` },
      ]}
      linkNovo="/orgaos/novo"
      textoNovo="Novo órgão"
      linkItem={(o) => `/orgaos/${o.id}`}
    />
  );
}

export function OrgaoForm() {
  const { id } = useParams();
  const novo = id === 'novo';
  const navegar = useNavigate();
  const [nome, setNome] = useState('');
  const [sigla, setSigla] = useState('');
  const [slug, setSlug] = useState('');
  const [esfera, setEsfera] = useState<Esfera | ''>('');
  const [uf, setUf] = useState('');

  useEffect(() => {
    if (novo) return;
    orgaos.buscar(Number(id)).then((o) => {
      setNome(o.nome);
      setSigla(o.sigla);
      setSlug(o.slug);
      setEsfera(o.esfera);
      setUf(o.uf ?? '');
    });
  }, [id, novo]);

  const federal = esfera === 'FEDERAL';

  return (
    <Formulario
      titulo={novo ? 'Novo órgão' : 'Editar órgão'}
      voltarPara="/orgaos"
      aoSalvar={async () => {
        const dados = {
          nome: nome.trim(),
          sigla: sigla.trim(),
          slug: slug.trim(),
          esfera: esfera as Esfera,
          uf: federal || !uf.trim() ? null : uf.trim().toUpperCase(),
        };
        if (novo) {
          await orgaos.criar(dados);
          navegar('/orgaos');
          return;
        }
        setSlug((await orgaos.atualizar(Number(id), dados)).slug);
        return 'Órgão salvo.';
      }}
      aoExcluir={
        novo
          ? undefined
          : async () => {
              await orgaos.excluir(Number(id));
              navegar('/orgaos');
            }
      }
    >
      <Campo rotulo="Nome" required maxLength={150} value={nome} onChange={(e) => setNome(e.target.value)} />
      <Campo rotulo="Sigla" required maxLength={30} value={sigla} onChange={(e) => setSigla(e.target.value)} />
      <Selecao rotulo="Esfera" valor={esfera} aoMudar={(v) => setEsfera(v as Esfera | '')} opcoes={ESFERAS} />
      {!federal && (
        <Campo
          rotulo="UF"
          required
          pattern="[A-Za-z]{2}"
          maxLength={2}
          title="Duas letras, como GO"
          value={uf}
          onChange={(e) => setUf(e.target.value)}
          ajuda="Obrigatória para órgãos estaduais e municipais."
        />
      )}
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
