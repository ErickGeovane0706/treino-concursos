-- Classificacoes do acervo (RF02, RF03). Regras de profundidade de assunto
-- (max. 3 niveis, pai da mesma materia) ficam no service (doc/banco-de-dados.md).

CREATE TABLE materias (
    id_materia INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    ativa      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_materias_nome UNIQUE (nome),
    CONSTRAINT uk_materias_slug UNIQUE (slug),
    CONSTRAINT ck_materias_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE assuntos (
    id_assunto     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_materia     INT          NOT NULL,
    id_assunto_pai INT,
    nome           VARCHAR(150) NOT NULL,
    slug           VARCHAR(170) NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_assuntos_materia FOREIGN KEY (id_materia) REFERENCES materias (id_materia),
    CONSTRAINT fk_assuntos_pai FOREIGN KEY (id_assunto_pai) REFERENCES assuntos (id_assunto),
    CONSTRAINT uk_assuntos_materia_slug UNIQUE (id_materia, slug),
    CONSTRAINT ck_assuntos_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_assuntos_pai_diferente CHECK (id_assunto_pai IS NULL OR id_assunto_pai <> id_assunto)
);

CREATE TABLE bancas (
    id_banca INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome     VARCHAR(100) NOT NULL,
    sigla    VARCHAR(30)  NOT NULL,
    slug     VARCHAR(120) NOT NULL,
    CONSTRAINT uk_bancas_nome UNIQUE (nome),
    CONSTRAINT uk_bancas_sigla UNIQUE (sigla),
    CONSTRAINT uk_bancas_slug UNIQUE (slug),
    CONSTRAINT ck_bancas_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE orgaos (
    id_orgao INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome     VARCHAR(150) NOT NULL,
    sigla    VARCHAR(30)  NOT NULL,
    slug     VARCHAR(120) NOT NULL,
    esfera   VARCHAR(30)  NOT NULL,
    uf       CHAR(2),
    CONSTRAINT uk_orgaos_slug UNIQUE (slug),
    CONSTRAINT ck_orgaos_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_orgaos_esfera CHECK (esfera IN ('FEDERAL', 'ESTADUAL', 'MUNICIPAL')),
    -- RF03 RN1: fora da esfera federal a UF e obrigatoria
    CONSTRAINT ck_orgaos_uf CHECK (esfera = 'FEDERAL' OR uf IS NOT NULL),
    CONSTRAINT ck_orgaos_uf_formato CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$')
);

CREATE TABLE provas (
    id_prova INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_banca INT          NOT NULL,
    id_orgao INT          NOT NULL,
    ano      INT          NOT NULL,
    cargo    VARCHAR(150) NOT NULL,
    nivel    VARCHAR(30)  NOT NULL,
    slug     VARCHAR(200) NOT NULL,
    CONSTRAINT fk_provas_banca FOREIGN KEY (id_banca) REFERENCES bancas (id_banca),
    CONSTRAINT fk_provas_orgao FOREIGN KEY (id_orgao) REFERENCES orgaos (id_orgao),
    -- RF03 RN2
    CONSTRAINT uk_provas_banca_orgao_ano_cargo UNIQUE (id_banca, id_orgao, ano, cargo),
    CONSTRAINT uk_provas_slug UNIQUE (slug),
    CONSTRAINT ck_provas_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_provas_ano CHECK (ano BETWEEN 1990 AND 2100),
    CONSTRAINT ck_provas_nivel CHECK (nivel IN ('FUNDAMENTAL', 'MEDIO', 'SUPERIOR'))
);

CREATE INDEX ix_assuntos_materia ON assuntos (id_materia);
CREATE INDEX ix_provas_banca ON provas (id_banca);
CREATE INDEX ix_provas_orgao ON provas (id_orgao);
