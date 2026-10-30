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

- **Front-end:** [React](https://react.dev/), [Vite](https://vite.dev/), [TypeScript](https://www.typescriptlang.org/), [Tailwind CSS](https://tailwindcss.com/)
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

## 🚀 Como Executar o Projeto

### Pré-requisitos
- Java 21+
- Node.js 22+
- Docker Desktop (o PostgreSQL roda em container)

### Estrutura do repositório

```
treino-concursos/
├── backend/             # Spring Boot: páginas públicas (Thymeleaf), API REST e painel
├── frontend/            # Vite: ilhas React das páginas públicas e SPA do painel
├── doc/                 # Documentação e diagramas
├── CLAUDE.md            # Contexto do projeto para o Claude Code
└── docker-compose.yml   # PostgreSQL de desenvolvimento
```

### Banco de dados

```bash
docker compose up -d
```

### Backend

```bash
cd backend
cp .env.example .env
./mvnw spring-boot:run    # no Windows: mvnw.cmd spring-boot:run
./mvnw test               # testes com PostgreSQL temporário (Testcontainers)
```

O site sobe em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui.html`.

### Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

O painel fica em `http://localhost:5173/admin/`.

---
