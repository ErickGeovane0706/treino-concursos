# 10. Ambiente de Desenvolvimento e Testes

Esta seção descreve como o ambiente do Treino de Concursos é padronizado com Docker e como as partes (Spring, Thymeleaf, ilhas React e painel React) funcionam juntas em desenvolvimento e em produção.

## 10.1 Visão geral

| Situação | Comando | O que roda no Docker |
|---|---|---|
| Desenvolvimento do dia a dia | `docker compose up -d` | Apenas o PostgreSQL |
| Testes automatizados do backend | `./mvnw test` | Um PostgreSQL **temporário** (Testcontainers) |
| Testes do frontend | `npm test` (em `frontend/`) | Nada (Vitest roda no Node) |
| Produção | `docker compose -f docker-compose.prod.yml up -d` | Aplicação (imagem única) + PostgreSQL + backup |

Em desenvolvimento, o backend roda pela IDE (IntelliJ) e o frontend pelo Vite (VS Code), mantendo hot reload e debug.

## 10.2 Tecnologias do ambiente

| Tecnologia | Versão | Finalidade |
|---|---|---|
| Docker Desktop / Docker Compose | 29 / v2 | Banco em container e imagem de produção |
| PostgreSQL | 17 (`postgres:17-alpine`) | Banco de dados |
| Flyway | 12 | Migrations |
| Testcontainers | 2.0 | Banco real e descartável nos testes |
| Java / Spring Boot | 21 / 4.1 | Backend: páginas (Thymeleaf), API REST, Spring Security |
| Thymeleaf | o do Spring Boot | Renderização das páginas públicas no servidor |
| commonmark-java + OWASP Java HTML Sanitizer | atuais | Markdown → HTML seguro (RF04 RN4, RNF01) |
| Node.js / React / Vite / TypeScript | 22 / 19 / 8 / 5 | Ilhas públicas e SPA do painel |
| Tailwind CSS | 4 | Estilos (tokens de cor do protótipo) |
| Vitest | atual | Testes da lógica de progresso local |

## 10.3 Como as partes se encaixam

```
Navegador ──► Cloudflare (cache das páginas públicas, HTTPS)
                 │
                 ▼
          Spring Boot (porta 8080, um único .jar)
          ├── Páginas públicas  → Thymeleaf  (/, /questoes/**, /questao/**, /simulado/**)
          │       └── carregam as ilhas React  (/assets/ilha-treino-[hash].js)
          ├── Painel            → SPA React   (/admin/**  → index.html do painel)
          ├── API JSON          → /api/**, /api/admin/**
          └── PostgreSQL (mesmo servidor)
```

O frontend é **um único projeto Vite com várias entradas** (`build.rollupOptions.input`):

| Entrada | Arquivo | Usada em |
|---|---|---|
| `ilha-treino` | `frontend/src/ilhas/treino.tsx` | Página da questão: responder, corrigir, progresso, "Próxima" |
| `ilha-listagem` | `frontend/src/ilhas/listagem.tsx` | Listagens: marcar resolvidas, "Continuar", "Ocultar resolvidas" |
| `ilha-simulado` | `frontend/src/ilhas/simulado.tsx` | Simulado e resultado (Fase 3) |
| `admin` | `frontend/admin/index.html` | SPA do painel |

A lógica de progresso fica em `frontend/src/progresso/` (TypeScript puro, sem React), testada com Vitest e compartilhada pelas ilhas.

### Desenvolvimento

- `npm run dev` sobe o Vite em `http://localhost:5173`.
- Páginas públicas: acesse **`http://localhost:8080`** (Spring). No perfil `dev`, o template base inclui os scripts direto do servidor do Vite (`http://localhost:5173/@vite/client` e `http://localhost:5173/src/ilhas/treino.tsx`), com hot reload.
- Painel: acesse **`http://localhost:5173/admin/`**. O Vite repassa `/api` para o Spring (proxy), então o cookie de sessão funciona como se fosse a mesma origem.

### Produção

- `npm run build` gera `frontend/dist/` com `manifest.json`.
- O Maven copia `frontend/dist/` para `backend/target/classes/static/` (plugin `maven-resources-plugin` ou `frontend-maven-plugin`).
- Um helper do Thymeleaf lê o `manifest.json` e escreve o nome com hash do arquivo (`/assets/ilha-treino-3f2a1c.js`), que é cacheado por 1 ano.
- O Spring devolve `static/admin/index.html` para qualquer rota `/admin/**` (fallback da SPA).

## 10.4 Banco de dados de desenvolvimento (Docker Compose)

O `docker-compose.yml` da raiz define o serviço `db` (`postgres:17-alpine`, porta `5432`, banco `treino_concursos`, usuário e senha `postgres`, volume `postgres-data`, healthcheck). Os comandos são os mesmos do Campus Event Hub:

| Comando | Efeito |
|---|---|
| `docker compose up -d` | Sobe o banco |
| `docker compose ps` | Estado (deve aparecer *healthy*) |
| `docker compose logs -f db` | Logs |
| `docker compose stop` | Para, mantendo os dados |
| `docker compose down -v` | Remove o container **e apaga os dados** |

Dados de exemplo para desenvolvimento ficam em `backend/src/main/resources/db/dev/R__dados_exemplo.sql` (migration repetível do Flyway), carregada **somente no perfil `dev`** (`spring.flyway.locations=classpath:db/migration,classpath:db/dev`). Use as 8 questões do protótipo como ponto de partida.

## 10.5 Variáveis de ambiente

Nenhuma senha ou chave fica no código. O repositório tem só os modelos `.env.example`.

| Arquivo | Variáveis |
|---|---|
| `.env` (raiz, opcional) | `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_PORT` (Docker Compose) |
| `backend/.env` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE` (`dev` ou `prod`), `SITE_URL` (ex.: `https://www.seudominio.com.br`, usado em canonical e sitemap), `ADMIN_INICIAL_EMAIL`, `ADMIN_INICIAL_SENHA` (RF01 RN5), `ADS_ENABLED` (`false` em dev), `ADSENSE_PUBLISHER_ID` (`pub-...`), `ADSENSE_SLOT_QUESTAO`, `ADSENSE_SLOT_LATERAL`, `VITE_DEV_SERVER` (só em dev: `http://localhost:5173`) |
| `frontend/.env` | `VITE_API_URL=http://localhost:8080` (proxy do Vite). Nunca coloque senhas: variáveis `VITE_` vão para o navegador |

O schema é criado e atualizado só pelo **Flyway**; o Hibernate apenas valida (`spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`).

## 10.6 Testes automatizados

**Backend (JUnit + Testcontainers):** igual ao Campus Event Hub. `./mvnw test` sobe um PostgreSQL 17 temporário, roda todas as migrations e executa os testes contra um banco real. Isso importa aqui porque várias regras dependem do PostgreSQL: índice único parcial da alternativa correta, `CHECK`s, CTE recursiva dos subassuntos e incremento atômico das estatísticas.

Prioridades de teste (meta de 70% nas regras, RNF05):

| Alvo | Exemplos |
|---|---|
| `QuestaoService` | RF04 RN2 (alternativas), RN3 (origem × prova), RN5 (zera estatística), RF05 (transições de status) |
| Navegação | ordem por id, cursor, assunto incluindo subassuntos, só PUBLICADA |
| Páginas | `301` de slug antigo, `410` de arquivada, `noindex` onde deve, HTML contém enunciado e comentário (MockMvc) |
| Segurança | `401`/`403` no painel, bloqueio após 5 tentativas, CSRF |
| Estatísticas e reportes | incremento, limite por IP (`429`) |

**Frontend (Vitest):** `frontend/src/progresso/` com cobertura ≥ 70% (registrar resposta, continuar de onde parou, ocultar resolvidas, caderno de erros, limite de 20.000, `localStorage` indisponível).

**CI:** GitHub Actions roda `./mvnw verify` e `npm ci && npm test && npm run build` em cada push.

## 10.7 Passo a passo para começar

1. Instalar Java 21, Node.js 22 e Docker Desktop.
2. Clonar o repositório e, na raiz, executar `docker compose up -d`.
3. Em `backend/`, copiar `.env.example` para `.env` e executar `./mvnw spring-boot:run`.
4. Em `frontend/`, copiar `.env.example` para `.env` e executar `npm install` e `npm run dev`.
5. Abrir o site em `http://localhost:8080`, o painel em `http://localhost:5173/admin/` (login com `ADMIN_INICIAL_EMAIL`) e o Swagger em `http://localhost:8080/swagger-ui.html`.
