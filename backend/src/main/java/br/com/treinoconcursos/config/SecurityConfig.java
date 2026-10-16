package br.com.treinoconcursos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Seguranca minima do esqueleto (issue #1).
 *
 * As paginas publicas e a API publica ficam abertas, sem login (o visitante
 * nao tem conta). O painel e a API do painel ficam FECHADOS para todos ate a
 * issue #2, que traz o login por sessao, CSRF por cookie e os perfis
 * EDITOR/ADMIN.
 *
 * Sem autenticacao, a resposta e um 401 simples (sem pagina de login nem
 * redirecionamento), como pede doc/api.md. CSRF fica ligado (padrao).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**", "/api/admin/**").denyAll()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    /**
     * Sem usuarios ate a issue #2. Evita que o Spring Boot crie um usuario
     * padrao e escreva a senha gerada no log (que o CI publico exibiria).
     */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
