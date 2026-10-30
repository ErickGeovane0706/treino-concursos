package br.com.treinoconcursos.service;

import br.com.treinoconcursos.exception.LimiteRequisicoesException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Limitador de requisicoes por endereco IP exclusivamente em memoria, com
 * expiracao automatica das janelas (RF16 RN3, RF17 RN4, RNF08).
 *
 * Nenhum endereco IP ou identificador do visitante e persistido no banco de
 * dados nem registrado em log.
 */
@Service
public class LimitadorPorIpService {

    public static final String ESCOPO_RESPOSTAS = "respostas";
    public static final String ESCOPO_REPORTES = "reportes";
    public static final Duration JANELA_PADRAO = Duration.ofHours(1);
    public static final int LIMITE_PADRAO_RESPOSTAS_POR_HORA = 120;
    public static final int LIMITE_PADRAO_REPORTES_POR_HORA = 5;

    private final Clock relogio;
    private final int limiteRespostasPorHora;
    private final int limiteReportesPorHora;
    private final ConcurrentMap<ChaveLimite, JanelaIp> janelas = new ConcurrentHashMap<>();

    public LimitadorPorIpService(Clock relogio) {
        this(relogio, LIMITE_PADRAO_RESPOSTAS_POR_HORA, LIMITE_PADRAO_REPORTES_POR_HORA);
    }

    @Autowired
    public LimitadorPorIpService(
            Clock relogio,
            @Value("${app.limite-ip.respostas-por-hora:" + LIMITE_PADRAO_RESPOSTAS_POR_HORA + "}") int limiteRespostasPorHora,
            @Value("${app.limite-ip.reportes-por-hora:" + LIMITE_PADRAO_REPORTES_POR_HORA + "}") int limiteReportesPorHora) {
        this.relogio = relogio;
        this.limiteRespostasPorHora = limiteRespostasPorHora;
        this.limiteReportesPorHora = limiteReportesPorHora;
    }

    /** RF17 RN4: controla em memoria o envio de respostas anonimas por IP. */
    public void verificarResposta(String ip) {
        consumir(ESCOPO_RESPOSTAS, ip, limiteRespostasPorHora, JANELA_PADRAO);
    }

    /** RF16 RN3: controla em memoria o envio de reportes de erro (ate 5 por hora por IP). */
    public void verificarReporte(String ip) {
        consumir(ESCOPO_REPORTES, ip, limiteReportesPorHora, JANELA_PADRAO);
    }

    /**
     * Registra um envio na janela do escopo e IP informados, removendo antes
     * entradas expiradas da memoria.
     *
     * @throws LimiteRequisicoesException quando o IP excede o limite na janela
     */
    public void consumir(String escopo, String ip, int limite, Duration janela) {
        Instant agora = Instant.now(relogio);
        expirarAntigos(agora);

        String ipNormalizado = (ip == null || ip.isBlank()) ? "desconhecido" : ip.trim();
        ChaveLimite chave = new ChaveLimite(escopo, ipNormalizado);

        JanelaIp atualizada = janelas.compute(chave, (k, atual) -> {
            if (atual == null || !atual.expiraEm().isAfter(agora)) {
                return new JanelaIp(1, agora.plus(janela));
            }
            return new JanelaIp(atual.quantidade() + 1, atual.expiraEm());
        });

        if (atualizada.quantidade() > limite) {
            throw new LimiteRequisicoesException(
                    "Muitas requisições deste endereço em pouco tempo. Tente novamente mais tarde.");
        }
    }

    /** Remove da memoria todas as janelas cujo prazo ja expirou. */
    public void expirarAntigos(Instant agora) {
        janelas.entrySet().removeIf(entrada -> !entrada.getValue().expiraEm().isAfter(agora));
    }

    /** Limpa todo o estado em memoria (usado entre testes). */
    public void limpar() {
        janelas.clear();
    }

    /** Quantidade de janelas ativas em memoria (para verificacao nos testes). */
    public int entradasAtivasEmMemoria() {
        expirarAntigos(Instant.now(relogio));
        return janelas.size();
    }

    /**
     * Extrai o IP do cliente considerando proxy reverso / Cloudflare
     * (X-Forwarded-For / X-Real-IP) ou o remoteAddr da conexao.
     */
    public static String extrairIp(HttpServletRequest request) {
        if (request == null) {
            return "desconhecido";
        }
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            int virgula = encaminhado.indexOf(',');
            String primeiro = (virgula >= 0 ? encaminhado.substring(0, virgula) : encaminhado).trim();
            if (!primeiro.isEmpty()) {
                return primeiro;
            }
        }
        String ipReal = request.getHeader("X-Real-IP");
        if (ipReal != null && !ipReal.isBlank()) {
            return ipReal.trim();
        }
        String remoto = request.getRemoteAddr();
        return (remoto == null || remoto.isBlank()) ? "desconhecido" : remoto.trim();
    }

    private record ChaveLimite(String escopo, String ip) {
    }

    private record JanelaIp(int quantidade, Instant expiraEm) {
    }
}
