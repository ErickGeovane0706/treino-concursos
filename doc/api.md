# Contrato da API e das Rotas — Convenções Gerais

Este documento define as regras que valem para **todas** as rotas do Treino de Concursos. Diferente de uma SPA pura, o projeto tem dois tipos de rota:

| Tipo | Quem atende | Exemplo | Formato |
|---|---|---|---|
| **Páginas públicas** | Spring MVC + Thymeleaf (renderizadas no servidor) | `/questao/1234/haver-no-sentido-de-ocorrer` | HTML |
| **API JSON** | `@RestController` | `/api/questoes/1234/respostas`, `/api/admin/questoes` | JSON |

As páginas públicas são renderizadas no servidor para o Google ler o conteúdo (RF08) e para cada questão ser uma visualização de página com anúncio (RF06 RN3). O React aparece em dois lugares: em **ilhas** pequenas dentro das páginas públicas (responder a questão, progresso local) e na **SPA do painel** (`/admin`).

Os **endpoints JSON** de cada entidade são documentados no **Swagger**, gerado a partir do código:

- Swagger UI: `http://localhost:8080/swagger-ui.html` (somente no perfil `dev`)
- Especificação OpenAPI: `http://localhost:8080/v3/api-docs`

> **Fluxo de cada fatia:** o backend começa pelo esqueleto (DTOs + Controller com os métodos ainda sem lógica). Assim o formato já aparece no Swagger e o frontend pode começar em paralelo.

---

## 1. Idioma

- Caminhos, campos e enums em **português, sem acentos e sem cedilha**: `questoes`, `alternativas`, `totalRespostas`.
- Mesma nomenclatura do modelo do banco ([`diagramas/bancoDeDados.plantuml`](diagramas/bancoDeDados.plantuml)), adaptada ao padrão do JSON (seção 4).
- Mensagens de erro para o usuário em **português, com acentos**.

## 2. Páginas públicas (HTML)

| Rota | Página | Indexável | RF |
|---|---|---|---|
| `/` | Início: matérias, "Continuar de onde parei", simulados em destaque | sim | RF07, RF10 |
| `/questoes` | Todas as questões | sim | RF07 |
| `/questoes/{materia}` | Questões da matéria | sim | RF07 |
| `/questoes/{materia}/{assunto}` | Questões do assunto (inclui subassuntos) | sim | RF07 |
| `/questoes/banca/{banca}`, `/questoes/orgao/{orgao}`, `/questoes/ano/{ano}` | Listagens por origem (Fase 3) | sim | RF07 |
| `/questoes?materia=..&banca=..&ano=..` | Combinação com 3 ou mais filtros | **noindex** | RF07 RN4 |
| `/questao/{id}/{slug}` | Página da questão (modo treino) | sim | RF06, RF08 |
| `/caderno-de-erros` | Caderno de erros (montado no navegador) | **noindex** | RF11 |
| `/meu-progresso` | Progresso local e botão para apagar | **noindex** | RF10 |
| `/simulados` | Lista de simulados | sim | RF13 |
| `/simulado/{slug}` | Apresentação do simulado | sim | RF13 |
| `/simulado/{slug}/{n}` | Questão `n` do simulado | **noindex** | RF13 RN1 |
| `/simulado/{slug}/resultado` | Resultado e gabarito | **noindex** | RF13 RN3 |
| `/simulado/{slug}/comentarios` | Comentários (Offerwall do AdSense) | **noindex** | RF15 |
| `/sobre`, `/contato`, `/privacidade`, `/termos`, `/cookies` | Institucionais | sim | RF18 |
| `/sitemap.xml`, `/sitemaps/questoes-{n}.xml` | Sitemaps | — | RF09 |
| `/robots.txt`, `/ads.txt` | Arquivos de raiz | — | RF09, RF12 |
| `/admin/**` | SPA do painel (React) | bloqueado no robots | RF01 |

Regras das páginas:

- URLs em minúsculas, com hífen, **sem barra no final**. Slugs vindos do banco (`materias.slug`, `assuntos.slug` etc.).
- Quando a URL pedida não é a canônica (slug antigo, maiúsculas, barra no final), a resposta é `301` para a canônica (RF08 RN1).
- Questão inexistente: `404`. Questão arquivada: `410 Gone` (RF05 RN3). As duas sem anúncio.
- Parâmetros de navegação vão na query string e **não** fazem parte da URL canônica. Exemplo: `/questao/1234/slug?de=portugues/crase` indica de qual listagem o visitante veio, para o botão "Próxima questão" seguir o mesmo filtro.

## 3. URLs da API JSON

| Regra | Exemplo |
|---|---|
| Endpoints públicos começam com `/api` | `/api/questoes/1234/respostas` |
| Endpoints do painel começam com `/api/admin` | `/api/admin/questoes` |
| Recurso é um **substantivo no plural** | `/api/admin/bancas` |
| A ação é definida pelo **método HTTP** | `DELETE /api/admin/bancas/5` |
| Recurso que pertence a outro usa caminho aninhado | `/api/admin/materias/3/assuntos` |
| Nomes compostos usam hífen | `/api/admin/reportes-erro` |
| Filtros usam query string | `/api/admin/questoes?status=RASCUNHO` |
| Ações que não são criar/alterar/remover: verbo como sub-recurso com `PATCH` ou `POST` | `PATCH /api/admin/questoes/12/publicar`, `PATCH /api/admin/reportes-erro/8/resolver` |
| Sem versão na URL | `/api/questoes`, e não `/api/v1/questoes` |

### Métodos HTTP

| Método | Uso | Resposta de sucesso |
|---|---|---|
| `GET` | Buscar um item ou uma lista | `200 OK` |
| `POST` | Criar | `201 Created` com o item e o cabeçalho `Location` |
| `PUT` | Atualizar o item inteiro | `200 OK` |
| `PATCH` | Alterar uma parte ou executar uma ação | `200 OK` |
| `DELETE` | Remover | `204 No Content` |

### Endpoints públicos (sem login)

| Método e caminho | Uso | RF |
|---|---|---|
| `GET /api/questoes/ids?materia=&assunto=&banca=&orgao=&ano=&nivel=&depoisDe=&limite=` | Ids publicados do filtro, em ordem crescente (usado por "Continuar" e "Ocultar resolvidas") | RF07, RF10 |
| `GET /api/questoes/{id}/estatisticas` | `% de acerto` e `% por alternativa` (só com 30+ respostas) | RF17 |
| `POST /api/questoes/{id}/respostas` | Registra a resposta anônima `{ "letra": "C" }` → `202 Accepted` | RF17 |
| `POST /api/questoes/{id}/reportes` | Registra reporte `{ "motivo": "GABARITO_ERRADO", "descricao": "..." }` → `201` | RF16 |

### Endpoints do painel (com login)

| Recurso | Caminho base | Perfil |
|---|---|---|
| Autenticação | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/eu` | — |
| Matérias e assuntos | `/api/admin/materias`, `/api/admin/materias/{id}/assuntos` | EDITOR |
| Bancas, órgãos e provas | `/api/admin/bancas`, `/api/admin/orgaos`, `/api/admin/provas` | EDITOR |
| Questões | `/api/admin/questoes` (+ `/publicar`, `/arquivar`, `/anular`, `/marcar-desatualizada`, `POST /api/admin/previa-markdown`) | EDITOR |
| Simulados | `/api/admin/simulados` (+ `/publicar`, `/arquivar`) | EDITOR |
| Reportes | `/api/admin/reportes-erro` (+ `/resolver`, `/descartar`) | EDITOR |
| Usuários | `/api/admin/usuarios` (+ `/inativar`, `/reativar`, `/desbloquear`, `/redefinir-senha`) | ADMIN |

## 4. Campos no JSON

| Regra | Exemplo |
|---|---|
| Nomes em **camelCase** | `totalRespostas`, `tempoSugeridoMin` |
| Coluna `snake_case` vira `camelCase` | `id_assunto_pai` → `idAssuntoPai` |
| IDs são números | `"id": 1234` |
| Enums em texto maiúsculo, iguais ao banco | `"status": "PUBLICADA"`, `"tipo": "CERTO_ERRADO"` |
| Campo sem valor vem como `null` (nunca omitido) | `"idProva": null` |
| Listas vazias vêm como `[]` | `"alternativas": []` |
| Textos em Markdown vêm crus no painel e já convertidos em HTML sanitizado nas páginas públicas | `"enunciado": "Assinale a **correta**..."` |

**A API nunca expõe as entidades JPA.** Entrada e saída usam DTOs (`XxxRequest`, `XxxResponse`). Campos sensíveis (`senhaHash`) **nunca** aparecem em uma resposta, e os endpoints públicos **nunca** devolvem o gabarito separado da página (o gabarito só existe no HTML da questão, RF06 RN4).

## 5. Datas e horas

Formato **ISO-8601**, no horário de Brasília (UTC-3), sem fuso na string: `"2026-10-16T08:45:00"` (`LocalDateTime`). A formatação para exibição é responsabilidade de quem renderiza (Thymeleaf ou React).

## 6. Formato padrão de erro

Toda resposta de erro da API JSON usa **Problem Details (RFC 9457)** (`ProblemDetail` do Spring), centralizado no `GlobalExceptionHandler`:

```json
{
  "type": "about:blank",
  "title": "Violação de regra de negócio",
  "status": 409,
  "detail": "Uma questão de múltipla escolha deve ter exatamente uma alternativa correta.",
  "instance": "/api/admin/questoes"
}
```

O erro de validação (`400`) usa a mensagem fixa "Um ou mais campos estão inválidos."; a validação campo a campo é feita no formulário do painel.

As páginas HTML de erro (`404`, `410`, `500`) são templates Thymeleaf próprios, **sem anúncio**.

## 7. Códigos de status

| Código | Quando usar | Exemplo |
|---|---|---|
| `200 OK` | Sucesso com dados | Listar questões no painel |
| `201 Created` | Recurso criado | Cadastrar questão, enviar reporte |
| `202 Accepted` | Recebido para contabilizar | Resposta anônima (RF17) |
| `204 No Content` | Sucesso sem conteúdo | Remover banca |
| `301 Moved Permanently` | URL não canônica | Slug antigo da questão |
| `400 Bad Request` | Dado mal formado | Letra `F` em uma resposta |
| `401 Unauthorized` | Não autenticado | Acessar `/api/admin` sem login |
| `403 Forbidden` | Sem permissão ou CSRF inválido | EDITOR criando usuário |
| `404 Not Found` | Recurso inexistente | `GET /questao/999999` |
| `409 Conflict` | Regra de negócio impede | Duas alternativas corretas; excluir matéria com questões |
| `410 Gone` | Questão arquivada | RF05 RN3 |
| `423 Locked` | Conta bloqueada | 5 senhas erradas (RF01 RN3) |
| `429 Too Many Requests` | Limite por IP | Mais de 5 reportes por hora (RF16 RN3) |
| `500` | Erro inesperado | — |

**Regra prática:** `400` é quando o **formato** está errado; `409` é quando o formato está certo, mas as **regras** não permitem.

## 8. Paginação

O acervo cresce sem parar, então as listas **são paginadas**, de dois jeitos:

**Público: cursor (RF07 RN3).** A posição não muda quando entram questões novas.

```
GET /api/questoes/ids?assunto=crase&depoisDe=1240&limite=500
```
```json
{ "itens": [1243, 1250, 1262], "proximoCursor": null }
```

`proximoCursor` é o último id da página, ou `null` quando acabou. As listagens HTML usam a mesma lógica (`?depoisDe=1240`).

**Painel: páginas numeradas** (padrão do Spring Data, `?page=0&size=20&sort=id,desc`), serializadas com `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`:

```json
{ "content": [ ... ], "page": { "size": 20, "number": 0, "totalElements": 342, "totalPages": 18 } }
```

## 9. Autenticação e segurança

- Visitantes **não** fazem login; só `/api/admin/**` e `/api/auth/eu` exigem autenticação. `/admin/**` é só o casco estático da SPA (sem dados) e fica aberto para a tela de login carregar; quem protege os dados é a API. Requisições anônimas não criam sessão.
- Login: `POST /api/auth/login` com `{ "email", "senha" }` → `200` com o usuário, `401` ("E-mail ou senha inválidos.", a mesma mensagem para e-mail inexistente, senha errada ou conta inativa) ou `423` (bloqueada, RF01 RN3). Logout: `POST /api/auth/logout` → `204`. No login o id da sessão e o token CSRF são trocados.
- Senhas do painel: de 8 a 72 caracteres (72 é o limite do BCrypt). Se um usuário for inativado ou tiver o perfil alterado, a sessão dele perde o acesso na requisição seguinte.
- Painel com **sessão** do Spring Security (cookie `HttpOnly`, `Secure`, `SameSite=Lax`), não JWT: o painel é servido pela mesma origem, então a sessão é mais simples e mais segura.
- **CSRF ativo**: o Spring grava o cookie `XSRF-TOKEN` (já na primeira resposta) e a SPA envia o cabeçalho `X-XSRF-TOKEN` em toda requisição que altera dados. Os dois `POST` públicos (respostas e reportes) são isentos de CSRF, pois não usam sessão, e têm limite por IP.
- Perfis: `EDITOR` e `ADMIN` (RBAC, RNF01), indicados em cada endpoint no Swagger.

---

## Documentando os endpoints no Swagger

```java
@Tag(name = "Questões (painel)", description = "Cadastro e ciclo de vida das questões (RF04, RF05)")
@RestController
@RequestMapping("/api/admin/questoes")
public class QuestaoAdminController {

    @Operation(summary = "Publicar questão", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "200", description = "Questão publicada")
    @ApiResponse(responseCode = "409", description = "Questão sem comentário ou com gabarito inválido")
    @PatchMapping("/{id}/publicar")
    public QuestaoResponse publicar(@PathVariable Integer id) { ... }
}
```

- `@Tag`: agrupa os endpoints e cita os requisitos (RF).
- `@Operation`: resumo e **perfil** que pode usar.
- `@ApiResponse`: códigos possíveis, seguindo a seção 7.
