# 📚 Treino de Concursos

Banco de questões para concursos públicos **gratuito, sem limite diário e sem cadastro**. O visitante resolve questões de provas anteriores, recebe a correção na hora com um comentário explicativo, continua de onde parou sem repetir questões e treina com simulados. O projeto é mantido por publicidade (Google AdSense).

---

## 📌 Funcionalidades Principais

- **Modo Treino:** correção imediata e comentário em cada questão, cada uma com página própria indexável pelo Google.
- **Filtros em ordem fixa:** por matéria, assunto, banca, órgão, ano e nível.
- **Progresso sem login:** "Continuar de onde parei", "Ocultar questões já resolvidas" e caderno de erros, salvos no navegador.
- **Simulados:** correção no final, desempenho por matéria e gabarito grátis; comentários liberados por anúncio com recompensa.
- **Painel administrativo:** cadastro de questões em Markdown com pré-visualização, classificações, simulados e fila de reportes de erro.
- **Estatísticas anônimas:** "62% acertaram" e percentual por alternativa, sem coletar dados pessoais.

---

## 🛠️ Tecnologias Utilizadas

- **Front-end:** [React](https://react.dev/) (painel), [Preact](https://preactjs.com/) com `preact/compat` (ilhas públicas), [Vite](https://vite.dev/), [TypeScript](https://www.typescriptlang.org/), [Tailwind CSS](https://tailwindcss.com/)
- **Back-end:** [Java](https://www.java.com/), [Spring Boot](https://spring.io/projects/spring-boot) (Spring MVC + Thymeleaf, Spring Data JPA, Spring Security, Bean Validation, Flyway)
- **Banco de Dados:** [PostgreSQL](https://www.postgresql.org/)
- **Testes:** JUnit, Testcontainers, Vitest, Playwright

---

## 📄 Documentação

| Documento | Conteúdo |
|---|---|
| [doc/requisitos.md](doc/requisitos.md) | Especificação de requisitos: pontos de vista, casos de uso, RF, RNF e regras de negócio |
| [doc/diagramas/](doc/diagramas/) | Diagrama de casos de uso e modelo lógico do banco (PlantUML + PNG) |
| [doc/banco-de-dados.md](doc/banco-de-dados.md) | Dicionário de dados, restrições e índices |
| [doc/api.md](doc/api.md) | Convenções de rotas, JSON, erros, paginação e segurança |
| [doc/ambiente-de-desenvolvimento.md](doc/ambiente-de-desenvolvimento.md) | Ambiente, testes e integração Spring + Vite |
| [doc/deploy.md](doc/deploy.md) | Deploy em VPS, Docker de produção, Cloudflare, backup e monitoramento |
| [doc/plano-de-implementacao.md](doc/plano-de-implementacao.md) | Issues em ordem, com prompts para o Claude Code |
| [doc/prototipo.html](doc/prototipo.html) | Protótipo navegável (referência visual) |

---

## 🚀 Como Executar o Projeto (passo a passo)

O tutorial abaixo sobe o projeto do zero numa máquina nova: banco no Docker, backend Spring Boot e frontend Vite. Leva uns 10 minutos (a maior parte é download na primeira vez).

### 1. Instale os pré-requisitos

| Ferramenta | Versão | Como conferir |
|---|---|---|
| [Java (JDK)](https://adoptium.net/) | 21 ou mais nova | `java -version` |
| [Node.js](https://nodejs.org/) | 22.13 ou mais nova | `node -v` |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | qualquer recente | `docker compose version` |
| [Git](https://git-scm.com/) | qualquer | `git --version` |

> Não precisa instalar Maven nem PostgreSQL: o projeto traz o Maven Wrapper (`mvnw`) e o banco roda no Docker.

**Abra o Docker Desktop e espere ele ficar "running"** antes de seguir. Sem isso o passo 3 falha com `failed to connect to the docker API`.

### 2. Clone o repositório

```bash
git clone https://github.com/ErickGeovane0706/treino-concursos.git
cd treino-concursos
```

### 3. Suba o banco de dados (PostgreSQL no Docker)

Copie o modelo de variáveis da **raiz** e escolha uma senha para o banco:

```bash
cp .env.example .env          # no Windows (PowerShell): copy .env.example .env
```

Abra o `.env` da raiz e preencha **só** `DB_PASSWORD` (ex.: `DB_PASSWORD=senha123`). O resto pode ficar como está em desenvolvimento.

```bash
docker compose up -d
docker compose ps             # espere aparecer "healthy"
```

### 4. Rode o backend (Spring Boot)

```bash
cd backend
cp .env.example .env          # no Windows: copy .env.example .env
```

Abra o `backend/.env` e preencha:

```properties
DB_PASSWORD=senha123                    # a MESMA senha do .env da raiz
ADMIN_INICIAL_EMAIL=admin@exemplo.com   # login do painel
ADMIN_INICIAL_SENHA=Admin@12345         # senha do painel
```

Depois suba a aplicação:

```bash
./mvnw spring-boot:run        # no Windows (PowerShell/CMD): .\mvnw.cmd spring-boot:run
```

Na primeira vez o Maven baixa as dependências (pode demorar alguns minutos). Quando aparecer `Started ...Application`, está no ar:

- Site público: <http://localhost:8080>
- Swagger da API: <http://localhost:8080/swagger-ui.html>

O Flyway cria as tabelas sozinho e, no perfil `dev`, carrega **questões de exemplo** (`backend/src/main/resources/db/dev/R__dados_exemplo.sql`), então o site já abre com conteúdo. O administrador do `.env` é criado na primeira subida, quando ainda não há usuários.

### 5. Rode o frontend (Vite), em outro terminal

```bash
cd frontend
cp .env.example .env          # no Windows: copy .env.example .env
npm install
npm run dev
```

Pronto. Com os dois terminais rodando:

| O quê | Endereço |
|---|---|
| Site público (questões, filtros, simulados) | <http://localhost:8080> |
| Painel administrativo | <http://localhost:5173/admin/> (entre com `ADMIN_INICIAL_EMAIL` / `ADMIN_INICIAL_SENHA`) |

> O site público abre pela porta **8080** (Spring); o Vite na 5173 só fornece os scripts das ilhas e o painel, com hot reload.

### 6. O que testar

1. Abra uma questão, escolha uma alternativa e veja a correção e o comentário.
2. Clique em **Próxima questão** e depois em **Continuar de onde parei** na listagem.
3. Filtre por matéria, banca ou órgão.
4. Faça um **simulado** e veja o resultado por matéria.
5. No painel, cadastre uma questão em Markdown e publique.

### Rodando os testes

```bash
cd backend && ./mvnw test     # JUnit + Testcontainers (precisa do Docker aberto)
cd frontend && npm test       # Vitest
cd frontend && npm run build  # build de produção
```

Testes de ponta a ponta (Playwright, distância de 100 px dos anúncios):

```bash
docker compose -f docker-compose.e2e.yml up -d --build --wait
cd frontend && npx playwright install chromium   # só na primeira vez
npm run e2e
```

### Problemas comuns

| Sintoma | Solução |
|---|---|
| `failed to connect to the docker API` | O Docker Desktop não está aberto. Abra e espere ficar "running". |
| `Defina DB_PASSWORD no .env da raiz` | Faltou o passo 3: criar o `.env` na raiz com `DB_PASSWORD`. |
| `password authentication failed for user "treino"` | A senha do `backend/.env` é diferente da do `.env` da raiz. Se mudou a senha depois de criar o banco, rode `docker compose down -v` (apaga os dados) e `docker compose up -d`. |
| `Port 5432 is already in use` | Há outro PostgreSQL na máquina. Troque `DB_PORT` no `.env` da raiz (ex.: `5433`) e a porta em `DB_URL` no `backend/.env`. |
| `Port 8080 was already in use` | Feche o outro programa ou mude `SERVER_PORT` no `backend/.env`. |
| `./mvnw: Permission denied` (Linux/macOS) | `chmod +x mvnw` |
| Painel não aceita o login | O admin só é criado se a tabela de usuários estiver vazia. Confira o `backend/.env` e, se preciso, recrie o banco com `docker compose down -v`. |
| Página abre sem interatividade (não corrige a questão) | O `npm run dev` não está rodando no frontend. |

### Parar tudo

```bash
# Ctrl+C nos terminais do backend e do frontend, depois:
docker compose stop           # para o banco mantendo os dados
docker compose down -v        # remove o banco E APAGA os dados
```

### Estrutura do repositório

```
treino-concursos/
├── backend/             # Spring Boot: páginas públicas (Thymeleaf), API REST e painel
├── frontend/            # Vite: ilhas (Preact) das páginas públicas e SPA do painel (React)
├── doc/                 # Documentação e diagramas
├── deploy/              # Script de backup de produção
├── docker-compose.yml   # PostgreSQL de desenvolvimento
└── CLAUDE.md            # Contexto do projeto para o Claude Code
```

Detalhes de como as partes se encaixam estão em [doc/ambiente-de-desenvolvimento.md](doc/ambiente-de-desenvolvimento.md); produção em [doc/deploy.md](doc/deploy.md).

---
