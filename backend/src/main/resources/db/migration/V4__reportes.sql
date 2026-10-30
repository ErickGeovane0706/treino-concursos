-- Reportes de erro em questoes (RF16, US07, US12).
-- Nenhum dado pessoal ou endereco IP do visitante e armazenado (RF16 RN2, RNF08).

CREATE TABLE reportes_erro (
    id_reporte           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_questao           INT          NOT NULL,
    motivo               VARCHAR(30)  NOT NULL,
    descricao            TEXT,
    status               VARCHAR(30)  NOT NULL DEFAULT 'ABERTO',
    criado_em            TIMESTAMP    NOT NULL DEFAULT now(),
    id_resolvido_por     INT,
    resolvido_em         TIMESTAMP,
    observacao_resolucao TEXT,
    CONSTRAINT fk_reportes_questao FOREIGN KEY (id_questao) REFERENCES questoes (id_questao),
    CONSTRAINT fk_reportes_resolvido_por FOREIGN KEY (id_resolvido_por) REFERENCES usuarios (id_usuario),
    CONSTRAINT ck_reportes_motivo CHECK (motivo IN (
        'GABARITO_ERRADO',
        'ENUNCIADO_COM_ERRO',
        'COMENTARIO_INCORRETO',
        'QUESTAO_DESATUALIZADA',
        'OUTRO'
    )),
    CONSTRAINT ck_reportes_descricao CHECK (length(descricao) <= 1000),
    CONSTRAINT ck_reportes_status CHECK (status IN ('ABERTO', 'RESOLVIDO', 'DESCARTADO'))
);

CREATE INDEX ix_reportes_abertos ON reportes_erro (id_questao, motivo) WHERE status = 'ABERTO';
