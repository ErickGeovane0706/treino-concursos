import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { bancas, orgaos, provas, type Banca, type Nivel, type Orgao } from '../api';
import { Campo, ListaPaginada } from '../componentes';
import { Formulario, Selecao } from '../Formulario';

const NIVEIS: { valor: Nivel; texto: string }[] = [
  { valor: 'FUNDAMENTAL', texto: 'Fundamental' },
  { valor: 'MEDIO', texto: 'Médio' },
  { valor: 'SUPERIOR', texto: 'Superior' },
];

export function ProvasLista() {
  return (
    <ListaPaginada
      titulo="Provas"
      carregar={provas.listar}
      colunas={[
        { titulo: 'Prova', valor: (p) => `${p.siglaBanca} · ${p.siglaOrgao} · ${p.ano}` },
        { titulo: 'Cargo', valor: (p) => p.cargo },
        { titulo: 'Nível', valor: (p) => NIVEIS.find((n) => n.valor === p.nivel)?.texto },
      ]}
      linkNovo="/provas/nova"
      textoNovo="Nova prova"
      linkItem={(p) => `/provas/${p.id}`}
    />
  );
}

export function ProvaForm() {
  const { id } = useParams();
  const nova = id === 'nova';
  const navegar = useNavigate();
  const [listaBancas, setListaBancas] = useState<Banca[]>([]);
  const [listaOrgaos, setListaOrgaos] = useState<Orgao[]>([]);
  const [banca, setBanca] = useState('');
  const [orgao, setOrgao] = useState('');
  const [ano, setAno] = useState('');
  const [cargo, setCargo] = useState('');
  const [nivel, setNivel] = useState<Nivel | ''>('');
  const [slug, setSlug] = useState('');

  useEffect(() => {
    bancas.listar('', 0, 500).then((p) => setListaBancas(p.content));
    orgaos.listar('', 0, 500).then((p) => setListaOrgaos(p.content));
    if (nova) return;
    provas.buscar(Number(id)).then((p) => {
      setBanca(String(p.idBanca));
      setOrgao(String(p.idOrgao));
      setAno(String(p.ano));
      setCargo(p.cargo);
      setNivel(p.nivel);
      setSlug(p.slug);
    });
  }, [id, nova]);

  return (
    <Formulario
      titulo={nova ? 'Nova prova' : 'Editar prova'}
      voltarPara="/provas"
      aoSalvar={async () => {
        const dados = {
          idBanca: Number(banca),
          idOrgao: Number(orgao),
          ano: Number(ano),
          cargo: cargo.trim(),
          nivel: nivel as Nivel,
        };
        if (nova) {
          await provas.criar(dados);
          navegar('/provas');
          return;
        }
        setSlug((await provas.atualizar(Number(id), dados)).slug);
        return 'Prova salva.';
      }}
      aoExcluir={
        nova
          ? undefined
          : async () => {
              await provas.excluir(Number(id));
              navegar('/provas');
            }
      }
    >
      <Selecao
        rotulo="Banca"
        valor={banca}
        aoMudar={setBanca}
        opcoes={listaBancas.map((b) => ({ valor: String(b.id), texto: `${b.sigla} — ${b.nome}` }))}
      />
      <Selecao
        rotulo="Órgão"
        valor={orgao}
        aoMudar={setOrgao}
        opcoes={listaOrgaos.map((o) => ({ valor: String(o.id), texto: `${o.sigla} — ${o.nome}` }))}
      />
      <Campo rotulo="Ano" type="number" required min={1990} max={2100} value={ano} onChange={(e) => setAno(e.target.value)} />
      <Campo rotulo="Cargo" required maxLength={150} value={cargo} onChange={(e) => setCargo(e.target.value)} />
      <Selecao rotulo="Nível de escolaridade" valor={nivel} aoMudar={(v) => setNivel(v as Nivel | '')} opcoes={NIVEIS} />
      {slug && <p className="text-xs text-slate-500">Endereço gerado: {slug}</p>}
    </Formulario>
  );
}
