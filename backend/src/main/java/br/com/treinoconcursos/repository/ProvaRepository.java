package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Prova;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProvaRepository extends JpaRepository<Prova, Integer> {

    /** Lista do painel: busca no cargo, na sigla da banca ou do orgao (busca vazia = todas). */
    @EntityGraph(attributePaths = {"banca", "orgao"})
    @Query("""
            select p from Prova p
            where :busca = ''
               or lower(p.cargo) like lower(concat('%', :busca, '%'))
               or lower(p.banca.sigla) like lower(concat('%', :busca, '%'))
               or lower(p.orgao.sigla) like lower(concat('%', :busca, '%'))
            """)
    Page<Prova> buscar(@Param("busca") String busca, Pageable pageable);

    boolean existsByBancaIdAndOrgaoIdAndAnoAndCargoIgnoreCase(Integer idBanca, Integer idOrgao, Integer ano,
            String cargo);

    boolean existsByBancaIdAndOrgaoIdAndAnoAndCargoIgnoreCaseAndIdNot(Integer idBanca, Integer idOrgao, Integer ano,
            String cargo, Integer id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);

    boolean existsByBancaId(Integer idBanca);

    boolean existsByOrgaoId(Integer idOrgao);
}
