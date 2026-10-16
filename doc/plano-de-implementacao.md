# Plano de Implementação com o Claude Code

Este plano quebra o projeto em **20 issues pequenas**, na ordem em que devem ser feitas. Cada issue entrega uma fatia que funciona de ponta a ponta, cita os requisitos de [`requisitos.md`](requisitos.md) e traz um **prompt pronto** para colar no Claude Code.

Prazos estimados para uma pessoa com 10 a 15 horas por semana.

| Fase | Issues | Prazo | Resultado |
|---|---|---|---|
| 0. Preparação | — | semana 1 | Nicho, domínio, repositório, documentação no repo |
| 1. MVP técnico | #1 a #11 | semanas 2 a 6 | Site no ar, com painel, questões indexáveis e progresso local |
| 2. Conteúdo e monetização | #12 a #16 | semanas 6 a 9 | AdSense, estatísticas, reportes, caderno de erros |
| 3. Simulados e filtros | #17 a #20 | meses 3 a 5 | Filtros por banca/órgão/ano e simulados com Offerwall |
| 4. Conta e premium | fora deste plano | a partir do mês 6, se as métricas pedirem | Login com Google, sincronização, premium |

---

## Como trabalhar com o Claude Code

1. **Coloque estes arquivos no repositório antes de tudo** (`CLAUDE.md` na raiz e a pasta `doc/`). O Claude Code lê o `CLAUDE.md` sozinho em toda sessão e, a partir dele, encontra os requisitos.
2. **Uma issue por vez, uma branch por issue** (`feat/07-pagina-questao`). Crie as issues no GitHub com o texto de cada seção abaixo, assim o histórico fica rastreável como no Campus Event Hub.
3. **Comece cada issue no modo de planejamento (plan mode)** e cole o prompt. Leia o plano que ele propuser, corrija o que não fizer sentido e só então aprove.
4. **Exija os testes junto**: os prompts já pedem, mas confira se ele rodou `./mvnw test` e `npm test` e se passou.
5. **Revise o diff antes do commit.** Peça o commit no formato `feat(#7): página pública da questão (RF06, RF08)`.
6. **Se uma regra mudar, mude o documento primeiro.** Peça ao Claude Code para atualizar `doc/requisitos.md` (e o diagrama, se for o caso) na mesma branch.
7. **Sessões curtas**: ao terminar uma issue, use `/clear` antes da próxima. O contexto limpo deixa o Claude Code mais preciso.

---

## Fase 0 — Preparação (sem código)

- [ ] Escolher o recorte inicial (ex.: concursos administrativos de nível médio; Português, Informática e Raciocínio Lógico).
- [ ] Definir a origem das questões: provas públicas com banca, órgão e ano citados, ou questões autorais. Nunca copiar de apostilas e livros.
- [ ] Registrar nome e domínio `.com.br`; criar o repositório no GitHub.
- [ ] Copiar `CLAUDE.md`, `README.md` e `doc/` para o repositório e fazer o primeiro commit.
- [ ] Consultar um contador sobre a receita do AdSense (CPF agora, MEI depois).

---

## Fase 1 — MVP técnico

### #1 Esqueleto do repositório
**Requisitos:** RNF05, RNF09
**Entregáveis:** `backend/` (Spring Boot 4.1, Java 21, web MVC, Thymeleaf, Data JPA, Validation, Flyway, Security, springdoc, Lombok, Testcontainers), `frontend/` (Vite 8 + React 19 + TypeScript + Tailwind 4 + Vitest, com as entradas `ilha-treino`, `ilha-listagem` e `admin`), `docker-compose.yml`, `.env.example`, `GlobalExceptionHandler` com Problem Details, `.github/workflows/ci.yml`.
**Pronto quando:** `docker compose up -d`, `./mvnw test`, `npm test` e `npm run build` passam, e o CI fica verde.

> **Prompt:**
> Leia `CLAUDE.md`, `doc/ambiente-de-desenvolvimento.md` e `doc/api.md`. Implemente a issue #1 do `doc/plano-de-implementacao.md`: crie o esqueleto do monorepo seguindo exatamente as versões e a estrutura descritas. Reaproveite o padrão do projeto anterior: `docker-compose.yml` só com o PostgreSQL, `.env` carregado via `spring.config.import`, `ddl-auto=validate`, `GlobalExceptionHandler` com `ProblemDetail` e um `TestcontainersConfiguration` com `@ServiceConnection`. No frontend, configure o Vite com múltiplas entradas e `build.manifest = true`. Crie o workflow do GitHub Actions. Ainda não crie tabelas nem telas. Ao final, rode todos os comandos de verificação e me mostre o resultado.

### #2 Autenticação do painel
**Requisitos:** RF01 (RN1–RN5), RNF01
**Entregáveis:** migration `V1__usuarios.sql`; Spring Security com sessão, CSRF por cookie, BCrypt; `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/eu`; bloqueio após 5 tentativas (423); criação do primeiro ADMIN por variável de ambiente; CRUD `/api/admin/usuarios` (só ADMIN); tela de login e de usuários na SPA.
**Pronto quando:** testes cobrem login certo e errado, bloqueio e desbloqueio após 15 min, 401/403, CSRF e "não inativar o último ADMIN".

> **Prompt:**
> Implemente a issue #2 do plano (RF01 e RNF01 em `doc/requisitos.md`). Use sessão do Spring Security (não JWT) com cookie `HttpOnly`, `Secure` e `SameSite=Lax`, CSRF com `CookieCsrfTokenRepository` e o cabeçalho `X-XSRF-TOKEN` na SPA. Rotas públicas continuam abertas; só `/admin/**`, `/api/admin/**` e `/api/auth/eu` exigem login. Mova o CORS para a configuração de segurança. Escreva os testes com Testcontainers antes de considerar pronto.

### #3 Schema de classificações e questões
**Requisitos:** RF02–RF05 (estrutura), modelo em `doc/banco-de-dados.md`
**Entregáveis:** `V2__classificacoes.sql`, `V3__questoes.sql` com todos os `CHECK`s, o índice único parcial e os índices parciais; entidades JPA, enums e repositórios; seed de desenvolvimento `db/dev/R__dados_exemplo.sql` com as 8 questões do protótipo.
**Pronto quando:** o Hibernate valida o schema, e há testes que provam que o banco recusa duas alternativas corretas, prova obrigatória para origem PROVA e questão publicada sem comentário.

> **Prompt:**
> Implemente a issue #3. Siga `doc/diagramas/bancoDeDados.plantuml` e `doc/banco-de-dados.md` à risca (nomes, tipos, constraints com nome explícito, índices parciais). Enums como `VARCHAR + CHECK` e `@Enumerated(EnumType.STRING)`. Crie o seed de desenvolvimento a partir das questões do protótipo (`doc/prototipo.html`), só no perfil `dev`. Não crie controllers ainda. Escreva testes de repositório que comprovem as restrições do banco.

### #4 Painel: matérias, assuntos, bancas, órgãos e provas
**Requisitos:** RF02 (RN1–RN4), RF03 (RN1–RN3), US10
**Entregáveis:** services e controllers em `/api/admin/...` com Swagger; geração de slug; telas de listagem e formulário na SPA (React Router, formulários com validação no cliente).
**Pronto quando:** CRUD completo funciona pelo painel; excluir algo com questões retorna 409 com mensagem clara; slug não muda quando já há questão publicada.

> **Prompt:**
> Implemente a issue #4 (RF02, RF03, US10). Comece pelo esqueleto (DTOs + controllers no Swagger), depois a lógica e os testes, depois as telas do painel. Siga as convenções de `doc/api.md`. Assuntos são aninhados em matérias (`/api/admin/materias/{id}/assuntos`) e têm no máximo 3 níveis. No painel, use um layout simples e funcional: lista com busca, formulário em página própria, mensagens de erro vindas do `detail` do Problem Details.

### #5 Painel: cadastro de questões
**Requisitos:** RF04 (RN1–RN5), RF05 (RN1–RN5), US09
**Entregáveis:** CRUD de questões com alternativas aninhadas; ações publicar, arquivar, anular e marcar desatualizada; `POST /api/admin/previa-markdown` (Markdown → HTML sanitizado com commonmark-java + OWASP HTML Sanitizer); formulário com atalho para criar prova, pré-visualização lado a lado e troca entre múltipla escolha e certo/errado; listagem com filtros (status, matéria, texto).
**Pronto quando:** testes cobrem cada RN; não é possível publicar sem comentário nem com gabarito inválido; HTML ou script no Markdown não aparece na prévia.

> **Prompt:**
> Implemente a issue #5 (RF04, RF05, US09). A regra mais importante é a validação das alternativas (RF04 RN2) no service, com mensagens em português. Crie um componente único `MarkdownService` (commonmark-java + OWASP Java HTML Sanitizer com allowlist) que será reutilizado pelas páginas públicas. No formulário, priorize a velocidade de cadastro: teclas de atalho para marcar a correta, prévia ao lado e "Salvar e cadastrar outra". Teste cada regra de negócio.

### #6 Layout público e páginas institucionais
**Requisitos:** RF18 (RN1–RN4), RNF02, RNF06
**Entregáveis:** template base Thymeleaf (cabeçalho, rodapé, tokens de cor e modo escuro do protótipo, fonte Public Sans), helper do `manifest.json` do Vite (com modo dev apontando para o servidor do Vite), páginas Sobre, Contato, Privacidade, Termos e Cookies, aviso de cookies, páginas de erro 404/410/500.
**Pronto quando:** o layout funciona em 360 px e 1920 px e em modo escuro; o aviso de cookies aparece só no primeiro acesso; as páginas legais têm texto real (rascunho revisável) explicando `localStorage`, AdSense e estatísticas anônimas.

> **Prompt:**
> Implemente a issue #6 (RF18, RNF02). Use o visual de `doc/prototipo.html` como referência (tokens de cor em `:root`, modo escuro, botões grandes). Crie o helper que lê `frontend/dist/.vite/manifest.json` em produção e aponta para `VITE_DEV_SERVER` no perfil `dev`. Escreva rascunhos reais das páginas legais em português, adequados à LGPD e às exigências do AdSense, deixando marcados com `TODO` os dados que eu preciso preencher (nome, e-mail de contato). Ainda não coloque anúncios.

### #7 Página pública da questão + ilha de treino
**Requisitos:** RF06 (RN1–RN4), RF08 (RN1–RN4), US01
**Entregáveis:** `GET /questao/{id}/{slug}` renderizado no servidor (enunciado, alternativas como botões, comentário e gabarito no HTML dentro de `<details>`, fonte da prova, avisos de anulada/desatualizada, links anterior/próxima/listagem); `301` para slug errado; `410` para arquivada; ilha `treino.tsx` que intercepta o clique, corrige sem recarregar, salva no progresso e mostra o botão "Próxima questão" (link real para a próxima URL, mantendo `?de=`).
**Pronto quando:** sem JavaScript dá para ler a resposta; com JavaScript a experiência é igual à do protótipo; testes MockMvc verificam o HTML, o 301 e o 410.

> **Prompt:**
> Implemente a issue #7 (RF06, RF08, US01). A página é Thymeleaf e deve funcionar sem JavaScript (o gabarito e o comentário ficam em `<details>`). A ilha React só melhora a experiência: corrige ao tocar, aplica as classes `ok`/`bad`/`dim` do protótipo, foca o botão "Próxima questão" e aceita as teclas A–E. "Próxima questão" é um `<a href>` para a próxima URL (nova página), nunca uma troca de estado na SPA. Deixe reservado, sem anúncio ainda, o lugar do bloco de publicidade entre o comentário e o botão, com 100 px de espaço (RF12 RN2). Mantenha o JS da ilha pequeno (meta de RNF03).

### #8 Listagens e navegação com ordem fixa (matéria e assunto)
**Requisitos:** RF07 (RN1–RN5, filtros de matéria e assunto), US02
**Entregáveis:** `/`, `/questoes`, `/questoes/{materia}`, `/questoes/{materia}/{assunto}` com paginação por cursor (`?depoisDe=`); subassuntos incluídos; `GET /api/questoes/ids`; cálculo da próxima questão do filtro usado pela página da questão.
**Pronto quando:** a ordem é sempre crescente por id; cadastrar uma questão nova não muda a posição das antigas; `EXPLAIN` mostra uso dos índices (anexar a saída na PR).

> **Prompt:**
> Implemente a issue #8 (RF07, US02), seguindo a seção 4 de `doc/banco-de-dados.md` e a seção 8 de `doc/api.md`. Paginação **por cursor**, nunca por número de página. Faça a página da questão usar o mesmo serviço para descobrir a próxima questão do filtro indicado em `?de=`. Rode `EXPLAIN ANALYZE` nas consultas principais com uns 50 mil registros gerados no teste e me mostre que os índices parciais estão sendo usados.

### #9 Progresso local do visitante
**Requisitos:** RF10 (RN1–RN7), US03, RNF06
**Entregáveis:** módulo `frontend/src/progresso/` (TypeScript puro: registrar resposta, consultar, última vista por filtro, próxima não resolvida, ocultar resolvidas, limpar, limite de 20.000, tolerância a `localStorage` indisponível); integração nas ilhas de treino e de listagem ("Continuar de onde parei", marcação de resolvidas, chave "Ocultar resolvidas"); página `/meu-progresso`.
**Pronto quando:** Vitest com cobertura ≥ 70% no módulo; tudo funciona em aba anônima com o armazenamento bloqueado (sem erro no console).

> **Prompt:**
> Implemente a issue #9 (RF10, US03). Escreva primeiro o módulo `progresso` em TypeScript puro, com testes Vitest, e só depois integre nas ilhas. Toda leitura e escrita no `localStorage` dentro de try/catch, com versão no formato salvo (`{ v: 1, ... }`) para permitir migração futura. "Continuar de onde parei" busca os ids do filtro em `/api/questoes/ids` (paginando pelo cursor) e escolhe o primeiro não resolvido depois da última questão vista.

### #10 SEO técnico
**Requisitos:** RF09 (RN1–RN4), US14
**Entregáveis:** `/sitemap.xml` (índice) e `/sitemaps/questoes-{n}.xml`, `/robots.txt`, canonical e `noindex` conforme a tabela de `doc/api.md`, `<title>` e `meta description` gerados, Open Graph básico, dados estruturados quando aplicável, cabeçalhos de cache (`Cache-Control` público para as páginas, imutável para `/assets`).
**Pronto quando:** testes validam o XML do sitemap, o `robots.txt` e o `noindex` nas rotas certas; a ferramenta de teste de pesquisagem do Google lê o enunciado de uma questão (verificar depois do deploy).

> **Prompt:**
> Implemente a issue #10 (RF09, US14). O sitemap usa `SITE_URL`, inclui só questões PUBLICADAS com `lastmod = atualizado_em` e se divide em arquivos de no máximo 50.000 URLs. Defina `Cache-Control` adequado para a Cloudflare: páginas públicas com cache curto no navegador e maior no CDN (`s-maxage`), `/assets` com hash imutável, painel e API sem cache. Pesquise as diretrizes atuais do Google para dados estruturados de questões educacionais antes de escolher o schema e me diga qual escolheu e por quê.

### #11 Deploy, backup e monitoramento
**Requisitos:** RNF04, RNF09, RNF01 (HTTPS e cabeçalhos)
**Entregáveis:** `Dockerfile` multi-stage (Node → build do Vite; Maven → .jar; runtime JRE 21 com `-Xmx512m`), `docker-compose.prod.yml` (app + PostgreSQL + serviço de backup diário com `pg_dump`, retenção de 30 dias e envio para armazenamento externo), guia `doc/deploy.md` (VPS, Docker, Cloudflare com SSL "Full (strict)", regras de cache, UptimeRobot, como restaurar o backup).
**Pronto quando:** o site está no ar com HTTPS no domínio; o backup roda e uma restauração foi testada; o monitor alerta por e-mail.

> **Prompt:**
> Implemente a issue #11 (RNF04, RNF09). Gere a imagem única e o compose de produção, com o PostgreSQL sem porta exposta para a internet. Escreva `doc/deploy.md` passo a passo para alguém que nunca configurou um VPS: criar o servidor, instalar Docker, firewall (só 22, 80 e 443), Cloudflare na frente, variáveis de produção, backup diário com cópia externa e o procedimento de restauração. Inclua o checklist do Search Console (verificar domínio, enviar o sitemap).

**Marco da Fase 1:** qualquer pessoa abre uma questão no celular, responde, vê o comentário e continua de onde parou; o Google consegue ler o HTML; o editor cadastra questões pelo painel. **Agora cadastre de 200 a 300 questões com bons comentários antes de pedir o AdSense.**

---

## Fase 2 — Conteúdo e monetização

### #12 Publicidade (AdSense)
**Requisitos:** RF12 (RN1–RN5), RNF07, RNF01 (CSP), US15
**Entregáveis:** fragmento Thymeleaf `bloco-anuncio` (altura reservada, rótulo "Publicidade", 100 px de espaço), script do AdSense só quando `ADS_ENABLED=true`, bloco lateral só em telas a partir de 1024 px, `/ads.txt`, nenhum anúncio em página de erro/lista vazia/painel, CSP compatível, integração da escolha do aviso de cookies (anúncios personalizados ou não).
**Pronto quando:** teste visual (Playwright) em 360 px e 1920 px mede ≥ 100 px entre o anúncio e qualquer botão; CLS ≤ 0,1 com o anúncio carregando; com bloqueador de anúncios o layout não quebra.

> **Prompt:**
> Implemente a issue #12 (RF12, RNF07, US15). Releia as políticas de posicionamento de anúncios do AdSense antes de começar e confirme que a implementação as respeita. O espaço do anúncio nunca pode empurrar botões depois de carregado. Escreva um teste com Playwright que abra uma questão, responda e meça a distância entre o bloco de anúncio e cada elemento clicável, nas larguras 360 e 1920.

### #13 Estatísticas anônimas
**Requisitos:** RF17 (RN1–RN4), RNF08
**Entregáveis:** `POST /api/questoes/{id}/respostas` (202) com incremento atômico, enviado pela ilha só na primeira resposta daquele navegador; `GET /api/questoes/{id}/estatisticas`; exibição "62% acertaram" e % por alternativa após responder (com 30+ respostas); limitador por IP em memória (Bucket4j ou similar, sem gravar IP); zerar estatísticas quando o gabarito muda (RF04 RN5).
**Pronto quando:** testes de concorrência mostram que 100 respostas simultâneas viram exatamente 100 no contador; nenhum dado do visitante vai para o banco.

> **Prompt:**
> Implemente a issue #13 (RF17, RNF08). Use `UPDATE ... SET x = x + 1` (nunca ler, somar e salvar pela entidade). Escreva um teste que dispara 100 respostas em paralelo e confere o total. O limitador por IP fica só em memória, com expiração. Confirme no final que nenhuma tabela recebeu IP nem identificador.

### #14 Reporte de erro
**Requisitos:** RF16 (RN1–RN5), US07, US12
**Entregáveis:** `V4__reportes.sql`; botão "Reportar erro" na página da questão (formulário pequeno, sem dados pessoais); `POST /api/questoes/{id}/reportes` com limite de 5 por hora por IP; fila no painel com prioridade para 3+ reportes de gabarito errado, ações resolver e descartar com observação.
**Pronto quando:** testes cobrem o `429`, a prioridade da fila e o encerramento.

> **Prompt:**
> Implemente a issue #14 (RF16, US07, US12). Reaproveite o limitador por IP da issue #13. No painel, a fila abre a questão ao lado do reporte para o editor corrigir sem trocar de tela.

### #15 Caderno de erros
**Requisitos:** RF11 (RN1–RN2), US04
**Entregáveis:** funções no módulo `progresso`; página `/caderno-de-erros` (noindex) que busca os dados mínimos das questões erradas e permite percorrê-las, filtrando por matéria; remoção do caderno ao acertar.
**Pronto quando:** testes Vitest cobrem entrar e sair do caderno.

> **Prompt:**
> Implemente a issue #15 (RF11, US04), estendendo o módulo `progresso` com testes. A navegação pelo caderno deve abrir as páginas reais das questões (`/questao/{id}/{slug}?de=caderno`), para continuarem sendo visualizações de página.

### #16 Desempenho e Core Web Vitals
**Requisitos:** RNF03
**Entregáveis:** script k6 (ou Gatling) para a página da questão e a listagem; relatório com p95 sem cache; ajuste de pool de conexões e cache de classificações em memória; relatório Lighthouse móvel de uma questão; `doc/desempenho.md` com os resultados.
**Pronto quando:** as metas do RNF03 são atingidas ou os desvios ficam documentados com plano de correção.

> **Prompt:**
> Implemente a issue #16 (RNF03). Gere uma massa de 50 mil questões em um ambiente de teste, rode a carga de 50 req/s na origem e meça o p95. Rode o Lighthouse em modo móvel na página de uma questão com o anúncio ativo de teste. Corrija o que estiver fora da meta e registre antes/depois em `doc/desempenho.md`.

**Marco da Fase 2:** AdSense aprovado e exibindo anúncios em posição segura; páginas sendo indexadas; reportes e estatísticas ajudando a melhorar o acervo.

---

## Fase 3 — Simulados e filtros por origem

### #17 Filtros por banca, órgão, ano e nível
**Requisitos:** RF07 (RN1 e RN4, parte da Fase 3)
**Entregáveis:** rotas `/questoes/banca/{banca}`, `/questoes/orgao/{orgao}`, `/questoes/ano/{ano}`; combinação via query string com `noindex` a partir de 3 filtros; filtros na interface (chips, como no protótipo); entradas no sitemap.
**Pronto quando:** `EXPLAIN` usa `ix_questoes_pub_prova`; testes de `noindex` passam.

> **Prompt:**
> Implemente a issue #17 (RF07). Reaproveite o serviço de navegação por cursor da issue #8, adicionando os filtros que passam pela tabela `provas`. Só gere páginas indexáveis para combinações com conteúdo suficiente (por exemplo, 10 ou mais questões); abaixo disso, `noindex`.

### #18 Montagem de simulados
**Requisitos:** RF14 (RN1–RN4), US11
**Entregáveis:** `V5__simulados.sql`; CRUD no painel com seleção de questões por filtro, reordenação (arrastar), validação de 5 a 100 questões elegíveis; marcação automática de `precisa_revisao` quando uma questão do simulado é arquivada, anulada ou desatualizada.
**Pronto quando:** testes cobrem cada RN.

> **Prompt:**
> Implemente a issue #18 (RF14, US11). A marcação de `precisa_revisao` deve acontecer no mesmo service que muda o status da questão (RF05), em uma transação.

### #19 Execução do simulado
**Requisitos:** RF13 (RN1–RN5), US05
**Entregáveis:** `/simulados`, `/simulado/{slug}`, `/simulado/{slug}/{n}` (uma página por questão, noindex, anúncio seguindo RF12), ilha `simulado.tsx` com respostas salvas no navegador, cronômetro opcional, `/simulado/{slug}/resultado` com percentual, desempenho por matéria (barras do protótipo) e gabarito grátis.
**Pronto quando:** recarregar no meio não perde respostas; questões em branco contam como erro; testes Vitest para o cálculo do resultado.

> **Prompt:**
> Implemente a issue #19 (RF13, US05), usando o modo simulado do protótipo como referência visual. Cada questão é uma página própria (para ter visualização de página), mas a navegação entre elas não pode perder o estado: salve as respostas no módulo `progresso` com uma chave por simulado.

### #20 Comentários do simulado com Offerwall
**Requisitos:** RF15 (RN1–RN4), US06
**Entregáveis:** `/simulado/{slug}/comentarios` (noindex) com os comentários de todas as questões; botão "Ver comentários" na página de resultado; guia em `doc/deploy.md` para configurar o Offerwall no AdSense incluindo **só** o caminho `/simulado/*/comentarios`.
**Pronto quando:** sem anúncio com recompensa disponível, a página abre normalmente; o protótipo do "anúncio de 15 s" **não** existe no código.

> **Prompt:**
> Implemente a issue #20 (RF15, US06). O sistema não bloqueia nada por conta própria: a página é normal e quem exibe a mensagem com recompensa é o Offerwall do AdSense, configurado no painel do Google. Pesquise a documentação atual do Offerwall do AdSense e escreva o passo a passo de configuração (inclusão de URL, recompensa em tempo de acesso) em `doc/deploy.md`.

**Marco da Fase 3:** simulados no ar e filtros por banca/órgão indexados. A partir daqui, a decisão de seguir para a Fase 4 depende das métricas (ver o plano de negócio).

---

## Fase 4 — Conta e premium (não detalhada)

Só começar com tráfego estável por vários meses:

- "Entrar com Google" (sem senha própria), migrando o progresso do `localStorage` para a conta no primeiro login.
- Sincronização entre aparelhos e estatísticas por matéria ao longo do tempo.
- Premium barato (abaixo dos concorrentes): sem anúncios, simulados extras. Exige gateway de pagamento, controle de assinatura e suporte.

Quando chegar a hora, atualize `doc/requisitos.md` (novos RF e US), o diagrama de banco e este plano **antes** de pedir o código.
