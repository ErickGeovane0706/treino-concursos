-- Questoes e alternativas (RF04, RF05, RF17).

CREATE TABLE questoes (
    id_questao      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_materia      INT          NOT NULL,
    id_assunto      INT,
    id_prova        INT,
    id_autor        INT          NOT NULL,
    tipo            VARCHAR(30)  NOT NULL,
    origem          VARCHAR(30)  NOT NULL,
    enunciado       TEXT         NOT NULL,
    comentario      TEXT,
    slug            VARCHAR(120) NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'RASCUNHO',
    anulada         BOOLEAN      NOT NULL DEFAULT FALSE,
    desatualizada   BOOLEAN      NOT NULL DEFAULT FALSE,
    total_respostas INT          NOT NULL DEFAULT 0,
    total_acertos   INT          NOT NULL DEFAULT 0,
    criado_em       TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMP    NOT NULL DEFAULT now(),
    publicado_em    TIMESTAMP,
    CONSTRAINT fk_questoes_materia FOREIGN KEY (id_materia) REFERENCES materias (id_materia),
    CONSTRAINT fk_questoes_assunto FOREIGN KEY (id_assunto) REFERENCES assuntos (id_assunto),
    CONSTRAINT fk_questoes_prova FOREIGN KEY (id_prova) REFERENCES provas (id_prova),
    CONSTRAINT fk_questoes_autor FOREIGN KEY (id_autor) REFERENCES usuarios (id_usuario),
    CONSTRAINT ck_questoes_tipo CHECK (tipo IN ('MULTIPLA_ESCOLHA', 'CERTO_ERRADO')),
    CONSTRAINT ck_questoes_origem CHECK (origem IN ('PROVA', 'AUTORAL')),
    CONSTRAINT ck_questoes_status CHECK (status IN ('RASCUNHO', 'PUBLICADA', 'ARQUIVADA')),
    -- RF04 RN3: prova obrigatoria para origem PROVA e proibida para AUTORAL
    CONSTRAINT ck_questoes_origem_prova CHECK ((origem = 'PROVA') = (id_prova IS NOT NULL)),
    -- RF04 RN1: comentario obrigatorio (e nao vazio) para publicar
    CONSTRAINT ck_questoes_comentario_publicada
        CHECK (status <> 'PUBLICADA' OR (comentario IS NOT NULL AND btrim(comentario) <> '')),
    CONSTRAINT ck_questoes_enunciado CHECK (btrim(enunciado) <> ''),
    CONSTRAINT ck_questoes_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_questoes_totais CHECK (total_acertos >= 0 AND total_acertos <= total_respostas),
    CONSTRAINT ck_questoes_publicado_em CHECK (status <> 'PUBLICADA' OR publicado_em IS NOT NULL)
);

CREATE TABLE alternativas (
    id_alternativa  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_questao      INT     NOT NULL,
    letra           CHAR(1) NOT NULL,
    texto           TEXT    NOT NULL,
    correta         BOOLEAN NOT NULL DEFAULT FALSE,
    total_marcacoes INT     NOT NULL DEFAULT 0,
    CONSTRAINT fk_alternativas_questao FOREIGN KEY (id_questao) REFERENCES questoes (id_questao) ON DELETE CASCADE,
    CONSTRAINT uk_alternativas_questao_letra UNIQUE (id_questao, letra),
    CONSTRAINT ck_alternativas_letra CHECK (letra IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_alternativas_texto CHECK (btrim(texto) <> ''),
    CONSTRAINT ck_alternativas_marcacoes CHECK (total_marcacoes >= 0)
);

-- RF04 RN2: no maximo uma alternativa correta por questao (o "pelo menos uma"
-- e validado no service).
CREATE UNIQUE INDEX uk_alternativas_correta ON alternativas (id_questao) WHERE correta;

-- Navegacao publica: so PUBLICADA, ordem por id_questao (RF07)
CREATE INDEX ix_questoes_pub_materia ON questoes (id_materia, id_questao) WHERE status = 'PUBLICADA';
CREATE INDEX ix_questoes_pub_assunto ON questoes (id_assunto, id_questao) WHERE status = 'PUBLICADA';
CREATE INDEX ix_questoes_pub_prova ON questoes (id_prova, id_questao) WHERE status = 'PUBLICADA';
-- Sitemap (RF09)
CREATE INDEX ix_questoes_pub_atualizado ON questoes (atualizado_em) WHERE status = 'PUBLICADA';
-- Painel
CREATE INDEX ix_alternativas_questao ON alternativas (id_questao);
