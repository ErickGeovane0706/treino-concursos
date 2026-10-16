package br.com.treinoconcursos.config;

import br.com.treinoconcursos.repository.UsuarioRepository;
import br.com.treinoconcursos.seguranca.CsrfCookieFilter;
import br.com.treinoconcursos.seguranca.UsuarioSessaoFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.List;

/**
 * Seguranca do painel (RF01, RNF01, doc/api.md secao 9).
 *
 * - Visitantes nao fazem login: o site publico e a API publica ficam abertos.
 * - O painel usa SESSAO (nao JWT). O login e feito pelo AuthController, que
 *   chama o AuthService (bloqueio apos 5 senhas erradas) e grava a sessao.
 * - CSRF por cookie: o Spring grava XSRF-TOKEN e a SPA devolve o valor no
 *   cabecalho X-XSRF-TOKEN em toda requisicao que altera dados.
 * - /admin/** e so o "casco" estatico da SPA (sem dados), entao fica aberto
 *   para a tela de login carregar; quem protege os dados e a API.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository, UsuarioRepository usuarioRepository)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers("/api/auth/eu").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(contexto -> contexto.securityContextRepository(securityContextRepository))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // A API responde 401 em vez de redirecionar, entao nao ha requisicao para
                // "lembrar". Sem isto, cada 401 anonimo criaria uma sessao a toa.
                .requestCache(cache -> cache.disable())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new UsuarioSessaoFilter(usuarioRepository, securityContextRepository),
                        AuthorizationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    /** RNF01: BCrypt com custo 12 (minimo exigido: 10). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Cookie XSRF-TOKEN legivel pelo JavaScript da SPA (o token nao e segredo de sessao). */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Executada no login: troca o id da sessao (contra fixacao de sessao) e
     * gera um novo token CSRF.
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    /**
     * O login nao passa pelo UserDetailsService (e feito pelo AuthService).
     * Este bean vazio so impede o Spring Boot de criar um usuario padrao e
     * escrever a senha gerada no log, que o CI publico exibiria.
     */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
