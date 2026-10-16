package br.com.treinoconcursos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Relogio dos testes: comeca na hora real e pode ser adiantado, para testar
 * regras com tempo (ex.: desbloqueio apos 15 minutos) sem esperar.
 */
public class RelogioAjustavel extends Clock {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private Duration avanco = Duration.ZERO;

    public void avancar(Duration duracao) {
        avanco = avanco.plus(duracao);
    }

    public void reiniciar() {
        avanco = Duration.ZERO;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(avanco);
    }

    @Override
    public ZoneId getZone() {
        return ZONA;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        throw new UnsupportedOperationException();
    }
}
