package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.config.SiteConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Arquivo /ads.txt na raiz do dominio para verificacao de propriedade e
 * autorizacao de inventario pelo Google AdSense (RF12 RN5, doc/api.md secao 2).
 */
@Tag(name = "Publicidade (público)", description = "Arquivo ads.txt para verificação do Google AdSense (RF12)")
@RestController
public class PublicidadeController {

    private final SiteConfig siteConfig;

    public PublicidadeController(SiteConfig siteConfig) {
        this.siteConfig = siteConfig;
    }

    @Operation(summary = "Servir arquivo ads.txt com o publisher ID configurado", description = "Perfil: público")
    @ApiResponse(responseCode = "200", description = "Arquivo ads.txt gerado com ADSENSE_PUBLISHER_ID")
    @ApiResponse(responseCode = "404", description = "ADSENSE_PUBLISHER_ID não configurado")
    @GetMapping(value = "/ads.txt", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> adsTxt() {
        String conteudo = siteConfig.getAdsTxtConteudo();
        if (conteudo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(conteudo);
    }
}
