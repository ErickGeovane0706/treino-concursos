-- Simulados montados pelos editores (RF13, RF14, US05, US11).

CREATE TABLE simulados (
    id_simulado        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_autor           INT          NOT NULL,
    titulo             VARCHAR(150) NOT NULL,
    slug               VARCHAR(150) NOT NULL,
    descricao          TEXT,
    tempo_sugerido_min INT,
    status             VARCHAR(30)  NOT NULL DEFAULT 'RASCUNHO',
    precisa_revisao    BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em          TIMESTAMP    NOT NULL DEFAULT now(),
    publicado_em       TIMESTAMP,
    CONSTRAINT fk_simulados_autor FOREIGN KEY (id_autor) REFERENCES usuarios (id_usuario),
    CONSTRAINT uk_simulados_slug UNIQUE (slug),
    CONSTRAINT ck_simulados_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_simulados_titulo CHECK (length(trim(titulo)) > 0),
    CONSTRAINT ck_simulados_tempo CHECK (tempo_sugerido_min > 0),
    CONSTRAINT ck_simulados_status CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO')),
    CONSTRAINT ck_simulados_publicado_em CHECK (status <> 'PUBLICADO' OR publicado_em IS NOT NULL)
);

-- A PK impede repetir a questao no simulado (RF14 RN2); a ordem e unica por simulado.
CREATE TABLE simulado_questoes (
    id_simulado INT NOT NULL,
    id_questao  INT NOT NULL,
    ordem       INT NOT NULL,
    CONSTRAINT pk_simulado_questoes PRIMARY KEY (id_simulado, id_questao),
    CONSTRAINT uk_simulado_questoes_ordem UNIQUE (id_simulado, ordem),
    CONSTRAINT fk_simulado_questoes_simulado FOREIGN KEY (id_simulado)
        REFERENCES simulados (id_simulado) ON DELETE CASCADE,
    CONSTRAINT fk_simulado_questoes_questao FOREIGN KEY (id_questao) REFERENCES questoes (id_questao),
    CONSTRAINT ck_simulado_questoes_ordem CHECK (ordem > 0)
);

CREATE INDEX ix_simulado_questoes_questao ON simulado_questoes (id_questao);
