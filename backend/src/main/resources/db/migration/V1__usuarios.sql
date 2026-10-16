-- Contas do painel (RF01). Visitantes nao tem conta.
CREATE TABLE usuarios (
    id_usuario       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome             VARCHAR(100) NOT NULL,
    email            VARCHAR(150) NOT NULL,
    senha_hash       VARCHAR(100) NOT NULL,
    perfil           VARCHAR(30)  NOT NULL,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    tentativas_login INT          NOT NULL DEFAULT 0,
    bloqueado_ate    TIMESTAMP,
    criado_em        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email_minusculo CHECK (email = lower(email)),
    CONSTRAINT ck_usuarios_perfil CHECK (perfil IN ('ADMIN', 'EDITOR')),
    CONSTRAINT ck_usuarios_tentativas CHECK (tentativas_login >= 0)
);
