-- =============================================================================
-- Massa de teste de desempenho (RNF03, Issue #16): 50.000 questoes e
-- 250.000 alternativas em um banco limpo ja migrado pelo Flyway (V1..V4).
-- =============================================================================

BEGIN;

TRUNCATE reportes_erro, alternativas, questoes, provas, orgaos, bancas, assuntos, materias, usuarios
    RESTART IDENTITY CASCADE;

INSERT INTO usuarios (nome, email, senha_hash, perfil, ativo)
VALUES ('Carga RNF03', 'perf@treinoconcursos.invalid', '!', 'EDITOR', FALSE);

-- 10 materias ativas
INSERT INTO materias (nome, slug, ativa) VALUES
    ('Português', 'portugues', TRUE),
    ('Direito Constitucional', 'direito-constitucional', TRUE),
    ('Direito Administrativo', 'direito-administrativo', TRUE),
    ('Informática', 'informatica', TRUE),
    ('Raciocínio Lógico', 'raciocinio-logico', TRUE),
    ('Matemática Financeira', 'matematica-financeira', TRUE),
    ('Administração Geral', 'administracao-geral', TRUE),
    ('Contabilidade Geral', 'contabilidade-geral', TRUE),
    ('Direito Penal', 'direito-penal', TRUE),
    ('Arquivologia', 'arquivologia', TRUE);

-- Assuntos de Portugues em 3 niveis (RF02 RN2): sintaxe -> concordancia-verbal -> verbo-haver
INSERT INTO assuntos (id_materia, id_assunto_pai, nome, slug, ativo)
VALUES (1, NULL, 'Sintaxe', 'sintaxe', TRUE);

INSERT INTO assuntos (id_materia, id_assunto_pai, nome, slug, ativo)
VALUES (1, 1, 'Concordância verbal', 'concordancia-verbal', TRUE);

INSERT INTO assuntos (id_materia, id_assunto_pai, nome, slug, ativo)
VALUES (1, 2, 'Verbo haver', 'verbo-haver', TRUE);

INSERT INTO assuntos (id_materia, id_assunto_pai, nome, slug, ativo)
VALUES (1, NULL, 'Crase', 'crase', TRUE);

-- Mais 46 assuntos distribuidos entre as 10 materias (total = 50 assuntos)
INSERT INTO assuntos (id_materia, id_assunto_pai, nome, slug, ativo)
SELECT
    1 + (g % 10),
    NULL,
    'Assunto ' || g,
    'assunto-' || g,
    TRUE
FROM generate_series(5, 50) AS g;

-- Bancas, orgaos e provas para exercitar JOINs de fonteDe(questao) (RF04 RN3)
INSERT INTO bancas (nome, sigla, slug) VALUES
    ('Centro Brasileiro de Pesquisa em Avaliação', 'CEBRASPE', 'cebraspe'),
    ('Fundação Getulio Vargas', 'FGV', 'fgv'),
    ('Fundação Carlos Chagas', 'FCC', 'fcc'),
    ('Fundação Vunesp', 'VUNESP', 'vunesp'),
    ('Instituto AOCP', 'AOCP', 'aocp');

INSERT INTO orgaos (nome, sigla, slug, esfera, uf) VALUES
    ('Polícia Rodoviária Federal', 'PRF', 'prf', 'FEDERAL', NULL),
    ('Tribunal Regional Federal da 1ª Região', 'TRF1', 'trf1', 'FEDERAL', NULL),
    ('Receita Federal do Brasil', 'RFB', 'rfb', 'FEDERAL', NULL),
    ('Tribunal de Justiça de São Paulo', 'TJ-SP', 'tj-sp', 'ESTADUAL', 'SP'),
    ('Tribunal de Contas do Estado de Goiás', 'TCE-GO', 'tce-go', 'ESTADUAL', 'GO');

INSERT INTO provas (id_banca, id_orgao, ano, cargo, nivel, slug)
SELECT
    1 + ((g - 1) % 5),
    1 + ((g - 1) % 5),
    2020 + (g % 7),
    'Analista Administrativo Turma ' || g,
    CASE WHEN g % 2 = 0 THEN 'SUPERIOR' ELSE 'MEDIO' END,
    'prova-perf-' || g
FROM generate_series(1, 20) AS g;

-- 50.000 questoes:
--   - Questoes 1..1000: todas PUBLICADAS para garantir IDs conhecidos no inicio
--   - Demais: ~90% PUBLICADAS, ~5% RASCUNHO, ~5% ARQUIVADA
--   - 80% de origem PROVA (com join em prova -> banca + orgao), 20% AUTORAL
INSERT INTO questoes (
    id_materia, id_assunto, id_prova, id_autor, tipo, origem,
    enunciado, comentario, slug, status, total_respostas, total_acertos,
    criado_em, atualizado_em, publicado_em
)
SELECT
    1 + ((g - 1) % 10) AS id_materia,
    CASE
        WHEN ((g - 1) % 10) = 0 THEN 1 + ((g / 10) % 4)
        ELSE 5 + (g % 46)
    END AS id_assunto,
    CASE
        WHEN g % 5 = 0 THEN NULL
        ELSE 1 + (g % 20)
    END AS id_prova,
    1 AS id_autor,
    'MULTIPLA_ESCOLHA' AS tipo,
    CASE WHEN g % 5 = 0 THEN 'AUTORAL' ELSE 'PROVA' END AS origem,
    'No âmbito da administração pública e da norma culta da língua portuguesa, analise o caso concreto referente ao item **#' || g
        || '** e assinale a alternativa que apresenta a interpretação **correta** segundo a jurisprudência dominante.' AS enunciado,
    CASE
        WHEN g > 1000 AND g % 20 = 0 THEN NULL
        ELSE 'A alternativa **C** está correta para a questão #' || g
            || ', pois observa os princípios da legalidade, impessoalidade, moralidade, publicidade e eficiência (art. 37 da CF/88), '
            || 'além das regras de concordância verbal e regência nominal aplicáveis ao texto oficial.'
    END AS comentario,
    'questao-' || g AS slug,
    CASE
        WHEN g <= 1000 THEN 'PUBLICADA'
        WHEN g % 20 = 0 THEN 'RASCUNHO'
        WHEN g % 20 = 10 THEN 'ARQUIVADA'
        ELSE 'PUBLICADA'
    END AS status,
    35 AS total_respostas,
    22 AS total_acertos,
    now() - ((50000 - g) || ' seconds')::interval AS criado_em,
    now() - ((50000 - g) || ' seconds')::interval AS atualizado_em,
    CASE
        WHEN g > 1000 AND g % 20 = 0 THEN NULL
        ELSE now() - ((50000 - g) || ' seconds')::interval
    END AS publicado_em
FROM generate_series(1, 50000) AS g;

-- 5 alternativas (A..E) para cada uma das 50.000 questoes (250.000 linhas)
INSERT INTO alternativas (id_questao, letra, texto, correta, total_marcacoes)
SELECT
    q.id_questao,
    alt.letra,
    'Texto da alternativa ' || alt.letra || ' da questão #' || q.id_questao
        || ' com redação técnica compatível com provas de concursos públicos.',
    alt.letra = 'C',
    CASE alt.letra WHEN 'C' THEN 22 WHEN 'A' THEN 5 WHEN 'B' THEN 4 WHEN 'D' THEN 2 ELSE 2 END
FROM questoes q
CROSS JOIN (
    VALUES ('A'), ('B'), ('C'), ('D'), ('E')
) AS alt(letra);

COMMIT;

VACUUM ANALYZE materias;
VACUUM ANALYZE assuntos;
VACUUM ANALYZE bancas;
VACUUM ANALYZE orgaos;
VACUUM ANALYZE provas;
VACUUM ANALYZE questoes;
VACUUM ANALYZE alternativas;
