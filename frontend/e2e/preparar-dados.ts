import { request, type APIRequestContext, type FullConfig } from '@playwright/test';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * Cria pela API do painel os dados que os testes usam: uma materia, questoes publicadas
 * e um simulado publicado. Nunca apaga nada; cada execucao cria questoes e um simulado novos.
 * Usa o admin inicial do docker-compose.e2e.yml (valores so de teste).
 */
export const ARQUIVO_DADOS = fileURLToPath(new URL('./.dados.json', import.meta.url));

export interface DadosE2E {
  questoes: { id: number; slug: string }[];
  simulado: string;
}

const EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'e2e-admin@exemplo.invalid';
const SENHA = process.env.E2E_ADMIN_SENHA ?? 'senha-e2e-local-123';

const ENUNCIADO =
  'De acordo com a norma-padrão da língua portuguesa, assinale a alternativa em que o emprego do sinal ' +
  'indicativo de crase está **correto**, considerando a regência dos termos e o contexto apresentado.';
const COMENTARIO =
  'A crase ocorre quando a preposição "a" exigida pelo termo regente se funde com o artigo "a" do termo ' +
  'regido. Nas demais alternativas falta a preposição ou o artigo, por isso não há crase.';

async function csrf(api: APIRequestContext): Promise<Record<string, string>> {
  await api.get('/api/auth/eu');
  const cookie = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN');
  return cookie ? { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) } : {};
}

async function enviar<T>(api: APIRequestContext, metodo: 'post' | 'patch', caminho: string, dados?: unknown): Promise<T> {
  const resposta = await api[metodo](caminho, { headers: await csrf(api), data: dados });
  if (!resposta.ok()) {
    throw new Error(`${metodo.toUpperCase()} ${caminho}: ${resposta.status()} ${await resposta.text()}`);
  }
  return (await resposta.json()) as T;
}

export default async function prepararDados(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0].use.baseURL!;
  const api = await request.newContext({ baseURL });
  await enviar(api, 'post', '/api/auth/login', { email: EMAIL, senha: SENHA });

  const sufixo = Date.now().toString(36);
  const materia = await enviar<{ id: number }>(api, 'post', '/api/admin/materias', {
    nome: `Português E2E ${sufixo}`,
    slug: '',
    ativa: true,
  });

  const questoes: { id: number; slug: string }[] = [];
  for (let i = 1; i <= 5; i++) {
    const criada = await enviar<{ id: number }>(api, 'post', '/api/admin/questoes', {
      idMateria: materia.id,
      idAssunto: null,
      tipo: 'MULTIPLA_ESCOLHA',
      origem: 'AUTORAL',
      idProva: null,
      enunciado: `${ENUNCIADO} (${i})`,
      comentario: COMENTARIO,
      alternativas: ['A', 'B', 'C', 'D', 'E'].map((letra) => ({
        letra,
        texto: `Refiro-me à pessoa que chegou ontem e entregou o documento à secretária (${letra}).`,
        correta: letra === 'C',
      })),
    });
    const publicada = await enviar<{ id: number; slug: string }>(api, 'patch', `/api/admin/questoes/${criada.id}/publicar`);
    questoes.push({ id: publicada.id, slug: publicada.slug });
  }

  const simulado = await enviar<{ id: number; slug: string }>(api, 'post', '/api/admin/simulados', {
    titulo: `Simulado E2E ${sufixo}`,
    slug: '',
    descricao: null,
    tempoSugeridoMin: 30,
    idsQuestoes: questoes.map((q) => q.id),
  });
  await enviar(api, 'patch', `/api/admin/simulados/${simulado.id}/publicar`);

  const dados: DadosE2E = { questoes, simulado: simulado.slug };
  writeFileSync(ARQUIVO_DADOS, JSON.stringify(dados, null, 2));
  await api.dispose();
}
