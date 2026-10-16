// Hook PreToolUse (Edit|Write|MultiEdit) que barra o que o CLAUDE.md proíbe:
//  1. editar uma migration Flyway que já existe (crie uma nova V<n>__descricao.sql);
//  2. escrever segredo literal no código (segredos só em .env).
// Sai com código 2 para bloquear; a mensagem em stderr volta para o Claude.
import { existsSync, readFileSync } from 'node:fs';

const entrada = JSON.parse(readFileSync(0, 'utf8'));
const params = entrada.tool_input ?? {};
const caminho = (params.file_path ?? '').replaceAll('\\', '/');
const nome = caminho.split('/').pop();

function bloquear(motivo) {
  process.stderr.write(`Bloqueado pelo hook do projeto: ${motivo}\n`);
  process.exit(2);
}

if (/\/db\/migration\/V\d+__[^/]+\.sql$/.test(caminho) && existsSync(caminho)) {
  bloquear(`${nome} já existe. Nunca edite uma migration aplicada: crie uma nova V<n>__descricao.sql.`);
}

const textos = [params.content, params.new_string, ...(params.edits ?? []).map((e) => e.new_string)]
  .filter(Boolean)
  .join('\n');

const ignorar =
  /(^|\/)\.env(\.example)?$/.test(caminho) ||
  /\.(md|txt|plantuml)$/.test(caminho) ||
  /\/src\/test\//.test(caminho) ||
  /\.(test|spec)\.[jt]sx?$/.test(caminho) ||
  /\/\.claude\//.test(caminho);

if (!ignorar && textos) {
  const padroes = [
    [/-----BEGIN [A-Z ]*PRIVATE KEY-----/, 'chave privada'],
    [/\bca-pub-\d{16}\b|\bpub-\d{16}\b/, 'ID de publisher do AdSense (use ADSENSE_PUBLISHER_ID)'],
    [/\bAKIA[0-9A-Z]{16}\b/, 'chave da AWS'],
    [/\b(gh[pousr]_[A-Za-z0-9]{30,}|sk-[A-Za-z0-9_-]{20,})\b/, 'token de API'],
  ];
  const nomeSegredo = String.raw`\b(password|passwd|senha|secret|segredo|token|api[_-]?key)\w*`;
  const ehConfig = /\.(properties|ya?ml|conf|toml|ini)$/.test(caminho);
  // Em configuração o valor pode vir sem aspas; em código só conta string literal,
  // para não confundir com `senha = request.senha()` ou `senha: string`.
  const literal = ehConfig
    ? new RegExp(nomeSegredo + String.raw`\s*[:=]\s*["']?(?!\$\{)[^\s"'$]{6,}`, 'i')
    : new RegExp(nomeSegredo + String.raw`\s*[:=]\s*["'\x60](?!\$\{)[^"'\x60$]{6,}["'\x60]`, 'i');
  padroes.push([literal, 'senha ou chave literal (use ${VARIAVEL} vinda do .env)']);
  for (const [regex, tipo] of padroes) {
    if (regex.test(textos)) bloquear(`${tipo} em ${nome}. Segredos só no .env, nunca no código.`);
  }
}
