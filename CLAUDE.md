# Treino de Concursos

Banco de questões para concursos públicos, **gratuito, sem limite diário e sem cadastro**, mantido por anúncios (Google AdSense). O visitante chega pelo Google em uma questão, responde, vê o comentário e segue para a próxima.

## Onde está cada coisa

| Preciso de... | Leia |
|---|---|
| O que construir (US, RF, RNF, regras de negócio) | `doc/requisitos.md` |
| Tabelas, constraints, índices e consultas | `doc/banco-de-dados.md` e `doc/diagramas/bancoDeDados.plantuml` |
| Rotas, JSON, erros, paginação, segurança | `doc/api.md` |
| Como rodar, testar e como frontend e backend se encaixam | `doc/ambiente-de-desenvolvimento.md` |
| Deploy em VPS, Docker de produção, Cloudflare, backup e restauração | `doc/deploy.md` |
| Ordem de implementação e escopo de cada issue | `doc/plano-de-implementacao.md` |
| Referência visual (cores, modo treino, modo simulado) | `doc/prototipo.html` |

Antes de implementar uma issue, leia a seção dela no plano e os RF/RNF citados.

## Stack

Java 21, Spring Boot 4.1 (MVC, Thymeleaf, Data JPA, Security, Validation, Flyway), PostgreSQL 17, Testcontainers; React 19 + Vite 8 + TypeScript + Tailwind 4 + Vitest. Pacote base: `br.com.treinoconcursos`.

```
backend/    Spring Boot: páginas públicas (Thymeleaf), API REST, painel servido em /admin
frontend/   Vite com várias entradas: src/ilhas/* (ilhas públicas), admin/ (SPA do painel), src/progresso/ (lógica local)
doc/        Documentação e diagramas
```

## Comandos

```bash
docker compose up -d                 # banco de desenvolvimento
cd backend && ./mvnw spring-boot:run # http://localhost:8080
cd backend && ./mvnw test            # testes (precisa do Docker rodando)
cd frontend && npm run dev           # Vite em :5173 (painel em /admin/)
cd frontend && npm test              # Vitest
cd frontend && npm run build         # build de produção (gera manifest.json)
```

## Decisões de arquitetura (não mudar sem conversar)

1. **Páginas públicas renderizadas no servidor (Thymeleaf).** Enunciado, alternativas, gabarito e comentário vêm no HTML, para o Google indexar. React entra só como **ilha** pequena por cima. Nada de SPA na área pública.
2. **"Próxima questão" é um link para uma nova página**, nunca troca de estado no cliente: cada questão é uma visualização de página com anúncio.
3. **Visitante não tem login.** O progresso fica no `localStorage` (módulo `frontend/src/progresso/`), sempre com try/catch, e o site funciona sem ele.
4. **Ordem fixa por `id_questao` crescente e paginação por cursor** (`depoisDe`), nunca por número de página.
5. **Painel** é uma SPA React em `/admin`, autenticada por **sessão** do Spring Security com CSRF (não JWT). Perfis: `EDITOR` e `ADMIN`.
6. **Nenhum dado pessoal de visitante no banco** (sem IP, e-mail ou identificador). Limites por IP só em memória.

## Regras que nunca podem ser quebradas

- **Anúncios:** no máximo 1 bloco no conteúdo + 1 lateral (≥ 1024 px); **100 px de distância** de qualquer elemento clicável; altura reservada; rótulo "Publicidade"; nada que incentive clique; nenhum anúncio em página de erro, lista vazia ou painel; tudo desligado quando `ADS_ENABLED=false`. Violação pode suspender a conta do AdSense.
- **Banco:** schema só por Flyway; `ddl-auto=validate`; **nunca editar uma migration já aplicada**, crie uma nova `V<n>__descricao.sql`.
- **Markdown** do conteúdo sempre passa pelo `MarkdownService` (commonmark + sanitizer). Nunca renderize HTML do banco sem sanitizar (`th:utext` só com HTML que saiu do `MarkdownService`).
- **API:** nunca expor entidades JPA; DTOs `XxxRequest`/`XxxResponse`; erros em Problem Details via `GlobalExceptionHandler` (lance `RegraNegocioException` → 409, `RecursoNaoEncontradoException` → 404).
- **Segredos** só em `.env` (fora do Git).

## Convenções de código

- Nomes de domínio em português, sem acento no código (`QuestaoService`, `totalAcertos`); mensagens ao usuário em português com acento.
- Camadas: `controller` → `service` → `repository`; regras de negócio no `service`, nunca no controller nem na entidade.
- Cada `@RestController` com `@Tag` citando os RF, e `@Operation` com o perfil exigido.
- Contadores (`total_respostas` etc.) só com `UPDATE ... SET x = x + 1`.
- Teste junto com a implementação: JUnit + Testcontainers no backend (cada RN vira pelo menos um teste), Vitest no `progresso`. Meta: 70% nas regras de negócio.
- Commits: `feat(#7): página pública da questão (RF06, RF08)`.

## Ao terminar uma tarefa

1. Rode `./mvnw test` e, se mexeu no frontend, `npm test` e `npm run build`.
2. Se uma regra de negócio mudou, atualize `doc/requisitos.md` (e o diagrama, se for o caso) na mesma branch.
3. Resuma o que foi feito, quais RF/RN foram cobertos e o que ficou pendente.
