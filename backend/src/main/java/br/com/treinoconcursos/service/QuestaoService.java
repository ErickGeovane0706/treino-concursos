package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.AlternativaRequest;
import br.com.treinoconcursos.dto.QuestaoRequest;
import br.com.treinoconcursos.dto.QuestaoResponse;
import br.com.treinoconcursos.dto.QuestaoResumoResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cadastro (RF04) e ciclo de vida (RF05) das questoes. */
@Service
public class QuestaoService {

    static final int LIMITE_SLUG = 120;
    static final String SLUG_PADRAO = "questao";
    private static final List<String> LETRAS_CERTO_ERRADO = List.of("C", "E");
    private static final Map<String, String> TEXTOS_CERTO_ERRADO = Map.of("C", "Certo", "E", "Errado");

    private final QuestaoRepository questaoRepository;
    private final AssuntoRepository assuntoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MateriaService materiaService;
    private final ProvaService provaService;
    private final Clock relogio;

    public QuestaoService(QuestaoRepository questaoRepository, AssuntoRepository assuntoRepository,
            UsuarioRepository usuarioRepository, MateriaService materiaService, ProvaService provaService,
            Clock relogio) {
        this.questaoRepository = questaoRepository;
        this.assuntoRepository = assuntoRepository;
        this.usuarioRepository = usuarioRepository;
        this.materiaService = materiaService;
        this.provaService = provaService;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Page<QuestaoResumoResponse> listar(StatusQuestao status, Integer idMateria, String busca,
            Pageable pageable) {
        String termo = busca == null ? "" : busca.trim();
        return questaoRepository.buscar(status, idMateria, termo, pageable).map(QuestaoResumoResponse::de);
    }

    @Transactional(readOnly = true)
    public QuestaoResponse buscar(Integer id) {
        return QuestaoResponse.de(buscarEntidade(id));
    }

    @Transactional
    public QuestaoResponse criar(QuestaoRequest request, Integer idAutor) {
        Questao questao = new Questao();
        questao.setAutor(usuarioRepository.findById(idAutor)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado.")));
        preencher(questao, request);
        for (AlternativaRequest alternativa : request.alternativas()) {
            questao.adicionarAlternativa(new Alternativa(alternativa.letra(), textoDe(request.tipo(), alternativa),
                    alternativa.correta()));
        }
        questao = questaoRepository.save(questao);
        questaoRepository.flush();
        return QuestaoResponse.de(questao);
    }

    /**
     * Altera os dados sem mudar o status: uma publicada continua publicada
     * (RF05 RN2), e por isso o comentario nao pode ser apagado (RF04 RN1).
     */
    @Transactional
    public QuestaoResponse atualizar(Integer id, QuestaoRequest request) {
        Questao questao = buscarEntidade(id);
        if (questao.getStatus() == StatusQuestao.PUBLICADA && vazio(request.comentario())) {
            throw semComentario();
        }
        String gabaritoAnterior = gabarito(questao.getTipo(), questao.getAlternativas().stream()
                .filter(Alternativa::isCorreta).map(Alternativa::getLetra).findFirst().orElse(null));

        preencher(questao, request);
        substituirAlternativas(questao, request);
        questao.setAtualizadoEm(LocalDateTime.now(relogio));
        questaoRepository.flush();

        // RF04 RN5
        if (!gabaritoAnterior.equals(gabarito(request.tipo(), letraCorreta(request.alternativas())))) {
            questaoRepository.zerarTotais(id);
            questaoRepository.zerarMarcacoes(id);
            questao.setTotalRespostas(0);
            questao.setTotalAcertos(0);
            questao.getAlternativas().forEach(a -> a.setTotalMarcacoes(0));
        }
        return QuestaoResponse.de(questao);
    }

    /** So rascunho nunca publicado: o endereco de uma questao publicada pode estar indexado. */
    @Transactional
    public void excluir(Integer id) {
        Questao questao = buscarEntidade(id);
        if (questao.getPublicadoEm() != null) {
            throw new RegraNegocioException("Uma questão que já foi publicada não pode ser excluída. Arquive-a.");
        }
        questaoRepository.delete(questao);
    }

    /** RF05 RN1: de RASCUNHO ou ARQUIVADA para PUBLICADA, com comentario (RF04 RN1). */
    @Transactional
    public QuestaoResponse publicar(Integer id) {
        Questao questao = buscarEntidade(id);
        if (questao.getStatus() == StatusQuestao.PUBLICADA) {
            throw new RegraNegocioException("A questão já está publicada.");
        }
        if (vazio(questao.getComentario())) {
            throw semComentario();
        }
        validarAlternativas(questao.getTipo(), questao.getAlternativas().stream()
                .map(a -> new AlternativaRequest(a.getLetra(), a.getTexto(), a.isCorreta())).toList());
        LocalDateTime agora = LocalDateTime.now(relogio);
        questao.setStatus(StatusQuestao.PUBLICADA);
        if (questao.getPublicadoEm() == null) {
            questao.setPublicadoEm(agora);
        }
        questao.setAtualizadoEm(agora);
        return QuestaoResponse.de(questao);
    }

    /** RF05 RN3: arquivada responde 410 e sai do sitemap. */
    @Transactional
    public QuestaoResponse arquivar(Integer id) {
        Questao questao = buscarEntidade(id);
        if (questao.getStatus() == StatusQuestao.ARQUIVADA) {
            throw new RegraNegocioException("A questão já está arquivada.");
        }
        questao.setStatus(StatusQuestao.ARQUIVADA);
        questao.setAtualizadoEm(LocalDateTime.now(relogio));
        return QuestaoResponse.de(questao);
    }

    /** RF05 RN4: continua publicada, com aviso. {@code anulada = false} desfaz a marcacao. */
    @Transactional
    public QuestaoResponse anular(Integer id, boolean anulada) {
        Questao questao = buscarEntidade(id);
        questao.setAnulada(anulada);
        questao.setAtualizadoEm(LocalDateTime.now(relogio));
        return QuestaoResponse.de(questao);
    }

    /** RF05 RN5: continua publicada, com aviso. {@code desatualizada = false} desfaz a marcacao. */
    @Transactional
    public QuestaoResponse marcarDesatualizada(Integer id, boolean desatualizada) {
        Questao questao = buscarEntidade(id);
        questao.setDesatualizada(desatualizada);
        questao.setAtualizadoEm(LocalDateTime.now(relogio));
        return QuestaoResponse.de(questao);
    }

    Questao buscarEntidade(Integer id) {
        return questaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Questão não encontrada."));
    }

    /** Classificacao, textos e slug, depois de validar RF04 RN2 e RN3. */
    private void preencher(Questao questao, QuestaoRequest request) {
        validarAlternativas(request.tipo(), request.alternativas());

        questao.setMateria(materiaService.buscarEntidade(request.idMateria()));
        questao.setAssunto(assuntoDa(request));
        // RF04 RN3
        if (request.origem() == OrigemQuestao.PROVA) {
            if (request.idProva() == null) {
                throw new RegraNegocioException("Informe a prova (banca, órgão e ano) de uma questão de prova.");
            }
            questao.setProva(provaService.buscarEntidade(request.idProva()));
        } else {
            if (request.idProva() != null) {
                throw new RegraNegocioException("Uma questão autoral não pode ter prova.");
            }
            questao.setProva(null);
        }
        questao.setTipo(request.tipo());
        questao.setOrigem(request.origem());
        questao.setEnunciado(request.enunciado().strip());
        questao.setComentario(vazio(request.comentario()) ? null : request.comentario().strip());
        // Depois de publicada o slug fica: a URL antiga ainda redireciona, mas nao ha por que trocar
        if (questao.getPublicadoEm() == null) {
            String slug = Slugs.gerar(questao.getEnunciado(), LIMITE_SLUG);
            questao.setSlug(slug.isEmpty() ? SLUG_PADRAO : slug);
        }
    }

    private Assunto assuntoDa(QuestaoRequest request) {
        if (request.idAssunto() == null) {
            return null;
        }
        Assunto assunto = assuntoRepository.findById(request.idAssunto())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Assunto não encontrado."));
        if (!assunto.getMateria().getId().equals(request.idMateria())) {
            throw new RegraNegocioException("O assunto escolhido não pertence à matéria da questão.");
        }
        return assunto;
    }

    /**
     * Troca as alternativas sem violar as restricoes do banco: o Hibernate
     * executa INSERTs antes de UPDATEs e DELETEs, entao primeiro desmarca a
     * correta e remove as que sairam (flush), e so depois insere e marca a nova
     * correta (indice unico parcial "uma correta por questao").
     */
    private void substituirAlternativas(Questao questao, QuestaoRequest request) {
        Set<String> letrasNovas = request.alternativas().stream().map(AlternativaRequest::letra)
                .collect(Collectors.toSet());
        questao.getAlternativas().removeIf(a -> !letrasNovas.contains(a.getLetra()));
        questao.getAlternativas().forEach(a -> a.setCorreta(false));
        questaoRepository.flush();

        Map<String, Alternativa> atuais = questao.getAlternativas().stream()
                .collect(Collectors.toMap(Alternativa::getLetra, Function.identity()));
        for (AlternativaRequest pedido : request.alternativas()) {
            String texto = textoDe(request.tipo(), pedido);
            Alternativa alternativa = atuais.get(pedido.letra());
            if (alternativa == null) {
                questao.adicionarAlternativa(new Alternativa(pedido.letra(), texto, pedido.correta()));
            } else {
                alternativa.setTexto(texto);
                alternativa.setCorreta(pedido.correta());
            }
        }
        questao.getAlternativas().sort((a, b) -> a.getLetra().compareTo(b.getLetra()));
    }

    /** RF04 RN2 */
    static void validarAlternativas(TipoQuestao tipo, List<AlternativaRequest> alternativas) {
        List<String> letras = alternativas.stream().map(AlternativaRequest::letra).toList();
        if (new HashSet<>(letras).size() != letras.size()) {
            throw new RegraNegocioException("Há alternativas com a mesma letra.");
        }
        if (tipo == TipoQuestao.MULTIPLA_ESCOLHA) {
            List<String> esperadas = List.of("A", "B", "C", "D", "E").subList(0, alternativas.size());
            if (alternativas.size() < 4 || !letras.equals(esperadas)) {
                throw new RegraNegocioException(
                        "Uma questão de múltipla escolha deve ter 4 ou 5 alternativas, de A a E, em sequência.");
            }
            for (AlternativaRequest alternativa : alternativas) {
                if (vazio(alternativa.texto())) {
                    throw new RegraNegocioException("A alternativa " + alternativa.letra() + " está sem texto.");
                }
            }
        } else if (!new HashSet<>(letras).equals(Set.copyOf(LETRAS_CERTO_ERRADO))) {
            throw new RegraNegocioException(
                    "Uma questão de certo ou errado deve ter exatamente as alternativas C (Certo) e E (Errado).");
        }
        long corretas = alternativas.stream().filter(AlternativaRequest::correta).count();
        if (corretas != 1) {
            throw new RegraNegocioException(tipo == TipoQuestao.MULTIPLA_ESCOLHA
                    ? "Uma questão de múltipla escolha deve ter exatamente uma alternativa correta."
                    : "Marque se o gabarito é Certo ou Errado.");
        }
    }

    private static String textoDe(TipoQuestao tipo, AlternativaRequest alternativa) {
        return tipo == TipoQuestao.CERTO_ERRADO ? TEXTOS_CERTO_ERRADO.get(alternativa.letra())
                : alternativa.texto().strip();
    }

    private static String letraCorreta(List<AlternativaRequest> alternativas) {
        return alternativas.stream().filter(AlternativaRequest::correta).map(AlternativaRequest::letra)
                .findFirst().orElse(null);
    }

    /** O gabarito e o tipo mais a letra correta: trocar de tipo tambem invalida as estatisticas. */
    private static String gabarito(TipoQuestao tipo, String letraCorreta) {
        return tipo + ":" + letraCorreta;
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static RegraNegocioException semComentario() {
        return new RegraNegocioException("O comentário é obrigatório para publicar a questão.");
    }
}
