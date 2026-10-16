# Banco de Dados — Dicionário, Restrições e Índices

Complementa o diagrama [`diagramas/bancoDeDados.plantuml`](diagramas/bancoDeDados.plantuml). Este documento é a referência para escrever as migrations do Flyway (`backend/src/main/resources/db/migration`).

## 1. Convenções

| Regra | Exemplo |
|---|---|
| Tabelas no plural, `snake_case`, sem acento | `questoes`, `simulado_questoes` |
| Chave primária `id_<entidade>` com `GENERATED ALWAYS AS IDENTITY` | `id_questao INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` |
| Chave estrangeira com o mesmo nome da PK referenciada | `id_materia INT NOT NULL REFERENCES materias(id_materia)` |
| Enums gravados como `VARCHAR(30)` + `CHECK` (e `@Enumerated(EnumType.STRING)` no JPA) | `status VARCHAR(30) NOT NULL CHECK (status IN ('RASCUNHO','PUBLICADA','ARQUIVADA'))` |
| Datas e horas em `TIMESTAMP` (horário de Brasília), mapeadas para `LocalDateTime` | `criado_em TIMESTAMP NOT NULL DEFAULT now()` |
| Booleanos com valor padrão | `ativo BOOLEAN NOT NULL DEFAULT TRUE` |
| Exclusão: `ON DELETE RESTRICT` (padrão) para classificações; `ON DELETE CASCADE` só de `questoes` → `alternativas` e `simulados` → `simulado_questoes` | |
| Nomes de constraints explícitos | `uk_assuntos_materia_slug`, `fk_questoes_materia`, `ck_questoes_status` |

> Enum como `VARCHAR + CHECK` (e não `CREATE TYPE ... AS ENUM`) facilita adicionar valores depois: basta uma migration trocando o `CHECK`.

## 2. Dicionário de dados

### usuarios — contas do painel (RF01)

| Coluna | Tipo | Regras |
|---|---|---|
| id_usuario | INT | PK |
| nome | VARCHAR(100) | obrigatório |
| email | VARCHAR(150) | obrigatório, único, guardado em minúsculas |
| senha_hash | VARCHAR(100) | BCrypt (RNF01) |
| perfil | VARCHAR(30) | `ADMIN`, `EDITOR` |
| ativo | BOOLEAN | padrão `TRUE` |
| tentativas_login | INT | padrão `0`; zera no login correto (RF01 RN3) |
| bloqueado_ate | TIMESTAMP | nulo quando não bloqueado |
| criado_em | TIMESTAMP | padrão `now()` |

### materias (RF02)

| Coluna | Tipo | Regras |
|---|---|---|
| id_materia | INT | PK |
| nome | VARCHAR(100) | único |
| slug | VARCHAR(120) | único; imutável após ter questão publicada (RF02 RN4) |
| ativa | BOOLEAN | padrão `TRUE` |

### assuntos (RF02)

| Coluna | Tipo | Regras |
|---|---|---|
| id_assunto | INT | PK |
| id_materia | INT | FK `materias` |
| id_assunto_pai | INT | FK `assuntos`, opcional; deve ser da mesma matéria; máximo de 3 níveis (validado no service) |
| nome | VARCHAR(150) | obrigatório |
| slug | VARCHAR(170) | único por matéria: `uk_assuntos_materia_slug (id_materia, slug)` |
| ativo | BOOLEAN | padrão `TRUE` |

### bancas, orgaos, provas (RF03)

| Tabela.Coluna | Tipo | Regras |
|---|---|---|
| bancas.nome / sigla / slug | VARCHAR | todos únicos |
| orgaos.slug | VARCHAR(120) | único |
| orgaos.esfera | VARCHAR(30) | `FEDERAL`, `ESTADUAL`, `MUNICIPAL` |
| orgaos.uf | CHAR(2) | `CHECK (esfera = 'FEDERAL' OR uf IS NOT NULL)` |
| provas.ano | INT | `CHECK (ano BETWEEN 1990 AND 2100)` |
| provas.cargo | VARCHAR(150) | obrigatório |
| provas.nivel | VARCHAR(30) | `FUNDAMENTAL`, `MEDIO`, `SUPERIOR` |
| provas.slug | VARCHAR(200) | único (ex.: `fgv-tce-go-2026-tecnico-de-controle-externo`) |
| provas | — | `uk_provas_banca_orgao_ano_cargo (id_banca, id_orgao, ano, cargo)` |

### questoes (RF04, RF05, RF17)

| Coluna | Tipo | Regras |
|---|---|---|
| id_questao | INT | PK; **define a ordem fixa** de navegação (RF07 RN2) |
| id_materia | INT | FK, obrigatório |
| id_assunto | INT | FK, opcional; deve pertencer à mesma matéria (service) |
| id_prova | INT | FK; `CHECK ((origem = 'PROVA') = (id_prova IS NOT NULL))` (RF04 RN3) |
| id_autor | INT | FK `usuarios` |
| tipo | VARCHAR(30) | `MULTIPLA_ESCOLHA`, `CERTO_ERRADO` |
| origem | VARCHAR(30) | `PROVA`, `AUTORAL` |
| enunciado | TEXT | Markdown |
| comentario | TEXT | Markdown; `CHECK (status <> 'PUBLICADA' OR comentario IS NOT NULL)` (RF04 RN1) |
| slug | VARCHAR(120) | gerado do início do enunciado; não precisa ser único (a URL usa o id) |
| status | VARCHAR(30) | `RASCUNHO`, `PUBLICADA`, `ARQUIVADA` |
| anulada / desatualizada | BOOLEAN | padrão `FALSE` |
| total_respostas / total_acertos | INT | padrão `0`; atualizados com `UPDATE ... SET x = x + 1` (atômico) |
| criado_em / atualizado_em | TIMESTAMP | padrão `now()`; `atualizado_em` alimenta o `lastmod` do sitemap |
| publicado_em | TIMESTAMP | preenchido na primeira publicação |

### alternativas (RF04)

| Coluna | Tipo | Regras |
|---|---|---|
| id_alternativa | INT | PK |
| id_questao | INT | FK com `ON DELETE CASCADE` |
| letra | CHAR(1) | `CHECK (letra IN ('A','B','C','D','E'))`; único por questão |
| texto | TEXT | Markdown |
| correta | BOOLEAN | **exatamente uma por questão**: índice único parcial `CREATE UNIQUE INDEX uk_alternativas_correta ON alternativas(id_questao) WHERE correta;` (o "pelo menos uma" é validado no service) |
| total_marcacoes | INT | padrão `0` (RF17) |

> Certo/Errado usa as letras `C` e `E` (RF04 RN2).

### simulados e simulado_questoes (RF13, RF14)

| Tabela.Coluna | Tipo | Regras |
|---|---|---|
| simulados.slug | VARCHAR(150) | único |
| simulados.tempo_sugerido_min | INT | opcional, `CHECK (> 0)` |
| simulados.status | VARCHAR(30) | `RASCUNHO`, `PUBLICADO`, `ARQUIVADO` |
| simulados.precisa_revisao | BOOLEAN | padrão `FALSE` (RF14 RN4) |
| simulado_questoes | — | PK `(id_simulado, id_questao)` impede repetição (RF14 RN2); `uk_simulado_questoes_ordem (id_simulado, ordem)` |

### reportes_erro (RF16)

| Coluna | Tipo | Regras |
|---|---|---|
| motivo | VARCHAR(30) | `GABARITO_ERRADO`, `ENUNCIADO_COM_ERRO`, `COMENTARIO_INCORRETO`, `QUESTAO_DESATUALIZADA`, `OUTRO` |
| descricao | TEXT | opcional, até 1.000 caracteres (`CHECK (length(descricao) <= 1000)`) |
| status | VARCHAR(30) | `ABERTO`, `RESOLVIDO`, `DESCARTADO`; padrão `ABERTO` |
| id_resolvido_por / resolvido_em / observacao_resolucao | — | preenchidos ao encerrar |

> **Sem nenhuma coluna de IP, e-mail ou identificador do visitante** (RNF08).

## 3. Índices

Criados na mesma migration das tabelas. A navegação pública sempre filtra por `status = 'PUBLICADA'` e ordena por `id_questao`, então os índices são **parciais** e terminam em `id_questao`:

```sql
-- Navegação por matéria e por assunto (RF07, Fase 1)
CREATE INDEX ix_questoes_pub_materia ON questoes (id_materia, id_questao) WHERE status = 'PUBLICADA';
CREATE INDEX ix_questoes_pub_assunto ON questoes (id_assunto, id_questao) WHERE status = 'PUBLICADA';

-- Navegação por prova (banca, órgão, ano e nível passam por provas) (RF07, Fase 3)
CREATE INDEX ix_questoes_pub_prova   ON questoes (id_prova, id_questao)   WHERE status = 'PUBLICADA';
CREATE INDEX ix_provas_banca ON provas (id_banca);
CREATE INDEX ix_provas_orgao ON provas (id_orgao);

-- Sitemap (RF09)
CREATE INDEX ix_questoes_pub_atualizado ON questoes (atualizado_em) WHERE status = 'PUBLICADA';

-- Painel
CREATE INDEX ix_assuntos_materia ON assuntos (id_materia);
CREATE INDEX ix_alternativas_questao ON alternativas (id_questao);
CREATE INDEX ix_reportes_abertos ON reportes_erro (id_questao, motivo) WHERE status = 'ABERTO';
CREATE INDEX ix_simulado_questoes_questao ON simulado_questoes (id_questao);
```

## 4. Consultas-chave

**Próxima página do filtro (cursor, RF07 RN3):**

```sql
SELECT id_questao, slug, ...
FROM questoes
WHERE status = 'PUBLICADA'
  AND id_assunto = ANY(:idsAssuntoESubassuntos)
  AND id_questao > :depoisDe
ORDER BY id_questao
LIMIT :limite;
```

**Próxima questão a partir da atual (botão "Próxima questão", RF06 RN3):** a mesma consulta com `LIMIT 1` e `:depoisDe = id da questão atual`.

**Assunto com subassuntos:** carregar os ids com uma CTE recursiva (`WITH RECURSIVE`) ou, como são poucos, montar a árvore em memória com cache.

**Registrar resposta anônima (RF17), em uma transação:**

```sql
UPDATE questoes SET total_respostas = total_respostas + 1,
                    total_acertos   = total_acertos + CASE WHEN :correta THEN 1 ELSE 0 END
WHERE id_questao = :id AND status = 'PUBLICADA';
UPDATE alternativas SET total_marcacoes = total_marcacoes + 1
WHERE id_questao = :id AND letra = :letra;
```

## 5. Ordem sugerida das migrations

| Arquivo | Conteúdo | Issue |
|---|---|---|
| `V1__usuarios.sql` | `usuarios` | #2 |
| `V2__classificacoes.sql` | `materias`, `assuntos`, `bancas`, `orgaos`, `provas` + índices | #3 |
| `V3__questoes.sql` | `questoes`, `alternativas` + índices | #3 |
| `V4__reportes.sql` | `reportes_erro` | #14 |
| `V5__simulados.sql` | `simulados`, `simulado_questoes` | #18 |

> Uma migration já aplicada **nunca** é editada: qualquer mudança vira um novo arquivo `V<n>__descricao.sql`.
