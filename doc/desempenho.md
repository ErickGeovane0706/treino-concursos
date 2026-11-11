# Desempenho (RNF03, issue #16)

Resultados das medições de desempenho da origem e da página da questão no celular, com o que está dentro e fora das metas do RNF03 e o plano para o que falta.

## 1. Metas do RNF03 e situação em 2026-11-11

| Meta | Medido | Situação |
|---|---|---|
| Página da questão: p95 ≤ 300 ms sem cache | **9 ms** a 50 req/s; 12 ms a 150 req/s | ✅ |
| Listagens por cursor: p95 ≤ 300 ms sem cache | **12 ms** a 50 req/s; 12 ms a 150 req/s | ✅ |
| 50 req/s na origem, sem 5xx, JVM com `-Xmx512m` | 50,0 req/s, 0 falha, 0 requisição descartada (150 req/s também sem falha) | ✅ |
| Consultas de listagem e navegação com índice (`EXPLAIN`) | `IndiceNavegacaoExplainTest` verifica os planos no build | ✅ |
| LCP ≤ 2,5 s, CLS ≤ 0,1 (Lighthouse móvel, laboratório) | LCP **1,7 s**, CLS **0**, TBT **0 ms**, nota 99 | ✅ no laboratório |
| INP ≤ 200 ms e Core Web Vitals de campo (p75 no Search Console) | Sem dados: o site ainda não está no ar | ⏳ conferir após o lançamento |
| p95 ≤ 100 ms pelo cache da Cloudflare | Sem dados: depende do site no ar | ⏳ conferir após o lançamento |
| JavaScript da área pública ≤ 50 KB comprimido | **≈ 75 KB** na página da questão | ❌ desvio (seção 4) |

## 2. Como foi medido

Código da `main` em `a1294ae`, com as issues #17 a #20 incluídas.

- **Ambiente isolado com Docker** (Docker Desktop 29 num notebook Windows):
  - App: imagem de produção do `Dockerfile`, perfil `prod`, `--cpus=2 --memory=4g` e `-Xmx512m`.
  - Banco: PostgreSQL 17 em contêiner próprio com `--cpus=1 --memory=1g`.
  - k6: `grafana/k6:1.4.2`, na mesma rede Docker.
- **Massa:** `perf/seed-50k-questoes.sql`, com 50.000 questões (45.100 publicadas) e 250.000 alternativas, seguido de `ANALYZE` e reinício do app para limpar os caches.
- **Carga:** `perf/carga-origem.k6.js`, com 70% páginas de questão (com e sem `?de=`) e 30% listagens por cursor, cabeçalho `Cache-Control: no-cache`, 30 s a taxa constante. Antes da medição, 15 s de aquecimento da JVM.
- **Lighthouse 12.8.2**, modo móvel com limitação simulada (4× CPU, 150 ms de RTT, cerca de 1,6 Mbps), em `/questao/1/questao-1?de=portugues/sintaxe`, com `ADS_ENABLED=false`.

Relatórios em `perf/relatorios/`: `k6-2026-11-11-50rps.json`, `k6-2026-11-11-150rps.json` e `lighthouse-questao-mobile-2026-11-11.json`.

> **Limitação:** o processador do notebook deve ser mais rápido que um vCPU de VPS, e o k6 roda na mesma máquina, sem latência de rede. Os tempos absolutos são otimistas. A folga é grande (p95 de 9 a 12 ms contra a meta de 300 ms, e 3× a vazão pedida), mas a confirmação final é repetir a carga no VPS de produção (seção 5).

## 3. Histórico: os relatórios de 2026-10-29

Na issue #16, o Erivan fez as otimizações de origem: cache de matérias e assuntos em memória, consultas por cursor em duas etapas usando os índices parciais, ajuste do HikariCP e do cache de *prepared statements*, cache de templates e do manifest do Vite, gzip, e carregamento adiado do AdSense. Ele commitou estes relatórios:

| | k6 "antes" | k6 "depois" | Lighthouse "antes" | Lighthouse "depois" |
|---|---|---|---|---|
| p95 questão | 867 ms | 4.767 ms | | |
| p95 listagem | 1.028 ms | 3.438 ms | | |
| Vazão | 48,8 req/s | 31,0 req/s (533 descartadas) | | |
| LCP / TBT | | | 3,8 s / 370 ms | 1,9 s / 1.310 ms |

O "depois" ficou **pior** que o "antes" e fora das metas, e a issue foi fechada assim. A nova medição, com o mesmo script e a mesma massa num ambiente isolado e com recursos fixos, não repetiu esses números: com o código atual, o p95 ficou em 9 ms e o TBT em 0 ms. A explicação mais provável é que a medição de 29/10 rodou numa máquina disputada por outros processos. Não há registro do ambiente usado naquela medição para confirmar. Por isso, a seção 2 registra o ambiente desta vez.

## 4. Desvio: JavaScript da área pública acima de 50 KB

O que a página da questão baixa (gzip, medido no servidor):

| Arquivo | Conteúdo | gzip |
|---|---|---|
| `jsx-runtime-*.js` | React 19 + ReactDOM (pedaço compartilhado pelas ilhas) | 67,8 KB |
| `ilha-treino-*.js` | Ilha de treino e reporte | 3,2 KB |
| `montar-*.js` | Montagem das ilhas | 3,2 KB |
| `consentimento-*.js` | Aviso de cookies (sem React) | 0,9 KB |
| **Total** | | **≈ 75 KB** |

Só o React com o ReactDOM já passa de 50 KB comprimidos. Então **não dá para cumprir a meta mantendo o React nas ilhas públicas**. No laboratório, isso ainda não pesa (TBT de 0 ms e LCP de 1,7 s, porque as ilhas carregam depois do conteúdo do servidor), mas é tempo de download e de processamento no celular a cada visita sem cache.

**Plano de correção (precisa de decisão, porque muda a stack):**

1. **Preact com `preact/compat` só nas ilhas públicas (recomendado).** O código das ilhas continua o mesmo (mesma API do React), e o runtime cai para cerca de 4 a 5 KB gzip. O painel continua com React 19 e React Router. Exige separar o build do Vite em dois (ilhas com o alias para Preact e o `manifest.json` lido pelo Spring; painel com React em `/admin`) e rodar os testes das ilhas contra o Preact. Estimativa do total: cerca de 12 KB gzip.
2. **Ilhas sem framework (TypeScript puro).** É o menor resultado possível, mas exige reescrever `treino.tsx`, `listagem.tsx` e `simulado.tsx` e os testes.
3. **Aceitar o desvio e mudar a meta do RNF03** para cerca de 80 KB, registrando o motivo em `doc/requisitos.md`.

## 5. Conferir depois do lançamento

- [ ] Repetir `perf/carga-origem.k6.js` no VPS de produção, de fora dele, com a massa em um banco de teste (nunca no banco de produção: o seed apaga as tabelas).
- [ ] Conferir no Search Console, depois de 28 dias de tráfego, os Core Web Vitals de campo no celular (LCP, INP e CLS no p75).
- [ ] Conferir o p95 pelo cache da Cloudflare (cabeçalho `cf-cache-status: HIT`).
- [ ] Rodar o Lighthouse móvel de novo com `ADS_ENABLED=true` (o AdSense carrega depois da primeira interação ou 8 s depois do `load`).

## 6. Como repetir a medição

```bash
docker network create treino-perf
docker run -d --name treino-perf-db --network treino-perf --cpus=1 --memory=1g \
  -e POSTGRES_DB=treino -e POSTGRES_USER=treino -e POSTGRES_PASSWORD=perf-local postgres:17-alpine
docker build -t treino-perf-app .
docker run -d --name treino-perf-app --network treino-perf --cpus=2 --memory=4g -p 127.0.0.1:18080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=jdbc:postgresql://treino-perf-db:5432/treino \
  -e DB_USERNAME=treino -e DB_PASSWORD=perf-local -e SITE_URL=http://127.0.0.1:18080 \
  -e CONTATO_EMAIL=perf@exemplo.invalid -e CONTATO_RESPONSAVEL=Perf -e ADS_ENABLED=false treino-perf-app
# depois que /sobre responder 200 (o Flyway cria o schema):
docker exec -i treino-perf-db psql -v ON_ERROR_STOP=1 -U treino -d treino < perf/seed-50k-questoes.sql
docker exec treino-perf-db psql -U treino -d treino -c "ANALYZE;"
docker restart treino-perf-app
# 15 s de aquecimento e depois a medição
docker run --rm --network treino-perf -v "$PWD/perf:/work" -w /work grafana/k6:1.4.2 run \
  -e BASE_URL=http://treino-perf-app:8080 -e DURATION=15s carga-origem.k6.js
docker run --rm --network treino-perf -v "$PWD/perf:/work" -w /work grafana/k6:1.4.2 run \
  -e BASE_URL=http://treino-perf-app:8080 -e RELATORIO_PATH=/work/relatorios/k6-AAAA-MM-DD-50rps.json carga-origem.k6.js
npx lighthouse@12.8.2 "http://127.0.0.1:18080/questao/1/questao-1?de=portugues/sintaxe" \
  --only-categories=performance --output=json --output-path=perf/relatorios/lighthouse-questao-mobile-AAAA-MM-DD.json
# limpar
docker rm -f treino-perf-app treino-perf-db && docker network rm treino-perf
```
