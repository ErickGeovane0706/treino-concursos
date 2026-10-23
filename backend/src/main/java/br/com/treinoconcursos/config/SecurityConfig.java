package br.com.treinoconcursos.config;

import br.com.treinoconcursos.repository.UsuarioRepository;
import br.com.treinoconcursos.seguranca.CsrfCookieFilter;
import br.com.treinoconcursos.seguranca.CsrfSpaHandler;
import br.com.treinoconcursos.seguranca.UsuarioSessaoFilter;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

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
            SecurityContextRepository securityContextRepository, UsuarioRepository usuarioRepository,
            @Value("${app.vite-dev-server:}") String servidorViteDev)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers("/api/auth/eu").authenticated()
                        .anyRequest().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(politicaCsp(servidorViteDev)))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .requestMatcher(AnyRequestMatcher.INSTANCE)))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(new CsrfSpaHandler()))
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

    /**
     * RNF01: CSP compativel com o proprio site (fontes e scripts locais) e com o
     * Google AdSense. Em desenvolvimento, libera tambem o servidor do Vite.
     */
    static String politicaCsp(String servidorViteDev) {
        String origensDev = (servidorViteDev == null || servidorViteDev.isBlank())
                ? ""
                : " " + servidorViteDev.trim() + " " + servidorViteDev.trim().replaceFirst("^http", "ws");
        return "default-src 'self';"
                + " script-src 'self' 'unsafe-inline' https://pagead2.googlesyndication.com https://adservice.google.com https://www.googletagservices.com https://tpc.googlesyndication.com https://ep2.adtrafficquality.google https://fundingchoicesmessages.google.com" + origensDev + ";"
                + " style-src 'self' 'unsafe-inline'" + origensDev + ";"
                + " img-src 'self' data: https:;"
                + " font-src 'self' data: https://fonts.gstatic.com;"
                + " connect-src 'self' https://pagead2.googlesyndication.com https://adservice.google.com https://googleads.g.doubleclick.net https://ep1.adtrafficquality.google https://ep2.adtrafficquality.google https://fundingchoicesmessages.google.com" + origensDev + ";"
                + " frame-src 'self' https://googleads.g.doubleclick.net https://tpc.googlesyndication.com https://pagead2.googlesyndication.com https://www.google.com https://ep2.adtrafficquality.google https://fundingchoicesmessages.google.com;"
                + " object-src 'none';"
                + " base-uri 'self'";
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
