package br.com.treinoconcursos.model;

/** Perfis do painel (RF01 RN2). Visitantes nao tem perfil: nao fazem login. */
public enum Perfil {
    /** Gerencia questoes, classificacoes, simulados e reportes. */
    EDITOR,
    /** Tudo o que o EDITOR faz e tambem gerencia os usuarios do painel. */
    ADMIN
}
