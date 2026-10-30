package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.EncerrarReporteRequest;
import br.com.treinoconcursos.dto.ReporteErroPublicoResponse;
import br.com.treinoconcursos.dto.ReporteErroRequest;
import br.com.treinoconcursos.dto.ReporteErroResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.MotivoReporte;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.ReporteErro;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.StatusReporte;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.repository.ReporteErroRepository;
import br.com.treinoconcursos.repository.ReporteErroRepository.ContagemPorQuestao;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registro anonimo e tratamento de reportes de erro em questoes (RF16 RN1-RN5, US07, US12).
 */
@Service
public class ReporteErroService {

    static final int LIMITE_DESCRICAO = 1000;

    private final ReporteErroRepository reporteRepository;
    private final QuestaoRepository questaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LimitadorPorIpService limitadorPorIpService;
    private final Clock relogio;

    public ReporteErroService(
            ReporteErroRepository reporteRepository,
            QuestaoRepository questaoRepository,
            UsuarioRepository usuarioRepository,
            LimitadorPorIpService limitadorPorIpService,
            Clock relogio) {
        this.reporteRepository = reporteRepository;
        this.questaoRepository = questaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.limitadorPorIpService = limitadorPorIpService;
        this.relogio = relogio;
    }

    /**
     * US07 e RF16 RN1-RN3: registra reporte para questao publicada, com limite
     * de 5 por hora por IP em memoria e sem coletar dados pessoais.
     */
    @Transactional
    public ReporteErroPublicoResponse registrarReporte(Integer idQuestao, ReporteErroRequest request, String ip) {
        limitadorPorIpService.verificarReporte(ip);

        Questao questao = questaoRepository.findById(idQuestao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Questão não encontrada."));
        if (questao.getStatus() != StatusQuestao.PUBLICADA) {
            throw new RecursoNaoEncontradoException("Questão não encontrada.");
        }

        String descricao = limparTexto(request.descricao());
        if (descricao != null && descricao.length() > LIMITE_DESCRICAO) {
            throw new RegraNegocioException("A descrição deve ter no máximo 1.000 caracteres.");
        }

        ReporteErro reporte = new ReporteErro();
        reporte.setQuestao(questao);
        reporte.setMotivo(request.motivo());
        reporte.setDescricao(descricao);
        reporte.setStatus(StatusReporte.ABERTO);
        reporte.setCriadoEm(LocalDateTime.now(relogio));

        reporte = reporteRepository.save(reporte);
        return ReporteErroPublicoResponse.de(reporte);
    }

    /**
     * US12 e RF16 RN5: lista a fila do painel com prioridade no topo para
     * questoes que acumulam 3 ou mais reportes ABERTOS de GABARITO_ERRADO.
     */
    @Transactional(readOnly = true)
    public Page<ReporteErroResponse> listarFila(
            StatusReporte status,
            MotivoReporte motivo,
            Integer idQuestao,
            Pageable pageable) {
        Pageable paginacao = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<ReporteErro> pagina = reporteRepository.listarFila(status, motivo, idQuestao, paginacao);
        if (pagina.isEmpty()) {
            return Page.empty(paginacao);
        }

        Set<Integer> idsQuestao = pagina.getContent().stream()
                .map(r -> r.getQuestao().getId())
                .collect(Collectors.toSet());
        Map<Integer, Long> contagensGabarito = reporteRepository.contarGabaritoErradoAbertosPorQuestoes(idsQuestao)
                .stream()
                .collect(Collectors.toMap(ContagemPorQuestao::getIdQuestao, ContagemPorQuestao::getTotal));

        return pagina.map(r -> ReporteErroResponse.de(
                r,
                contagensGabarito.getOrDefault(r.getQuestao().getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public ReporteErroResponse buscar(Integer id) {
        ReporteErro reporte = buscarEntidade(id);
        long abertosGabarito = contarGabaritoAbertos(reporte.getQuestao().getId());
        return ReporteErroResponse.de(reporte, abertosGabarito);
    }

    /** RF16 RN4: marca o reporte como RESOLVIDO com observacao opcional. */
    @Transactional
    public ReporteErroResponse resolver(Integer id, EncerrarReporteRequest request, Integer idUsuario) {
        return encerrar(id, StatusReporte.RESOLVIDO, request, idUsuario);
    }

    /** RF16 RN4: marca o reporte como DESCARTADO com observacao opcional. */
    @Transactional
    public ReporteErroResponse descartar(Integer id, EncerrarReporteRequest request, Integer idUsuario) {
        return encerrar(id, StatusReporte.DESCARTADO, request, idUsuario);
    }

    private ReporteErroResponse encerrar(
            Integer id,
            StatusReporte novoStatus,
            EncerrarReporteRequest request,
            Integer idUsuario) {
        ReporteErro reporte = buscarEntidade(id);
        if (reporte.getStatus() != StatusReporte.ABERTO) {
            throw new RegraNegocioException("Este reporte já foi encerrado.");
        }
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        reporte.setStatus(novoStatus);
        reporte.setResolvidoPor(usuario);
        reporte.setResolvidoEm(LocalDateTime.now(relogio));
        reporte.setObservacaoResolucao(limparTexto(request == null ? null : request.observacao()));
        reporteRepository.flush();

        long abertosGabarito = contarGabaritoAbertos(reporte.getQuestao().getId());
        return ReporteErroResponse.de(reporte, abertosGabarito);
    }

    private ReporteErro buscarEntidade(Integer id) {
        return reporteRepository.findComDetalhesById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reporte não encontrado."));
    }

    private long contarGabaritoAbertos(Integer idQuestao) {
        return reporteRepository.countByQuestaoIdAndStatusAndMotivo(
                idQuestao,
                StatusReporte.ABERTO,
                MotivoReporte.GABARITO_ERRADO);
    }

    private static String limparTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.strip();
        return limpo.isEmpty() ? null : limpo;
    }
}
