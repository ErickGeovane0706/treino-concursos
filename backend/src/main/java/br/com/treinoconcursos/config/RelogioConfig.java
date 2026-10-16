package br.com.treinoconcursos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relogio da aplicacao no horario de Brasilia (doc/api.md, secao 5). Injetado
 * nos services para os testes poderem avancar o tempo (ex.: bloqueio de 15 min).
 */
@Configuration
public class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
