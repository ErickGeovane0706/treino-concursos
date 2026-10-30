package br.com.treinoconcursos.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que os artefatos de producao (RNF04, RNF09, RNF01) permanecem
 * consistentes no repositorio: Dockerfile multi-stage com -Xmx512m,
 * docker-compose.prod.yml sem porta exposta no PostgreSQL, script de backup
 * com retencao de 30 dias e copia externa, e guia doc/deploy.md.
 */
class InfraProducaoTest {

    private static final Path RAIZ = Path.of("..").toAbsolutePath().normalize();

    @Test
    void dockerfileMultiStageEmpacotaFrontendEBackendComJre21EXmx512m() throws IOException {
        Path dockerfile = RAIZ.resolve("Dockerfile");
        assertThat(dockerfile).exists();

        String conteudo = Files.readString(dockerfile);
        assertThat(conteudo)
                .contains("FROM node:22-alpine AS frontend-build")
                .contains("RUN npm run build")
                .contains("FROM eclipse-temurin:21-jdk-alpine AS backend-build")
                .contains("COPY --from=frontend-build /workspace/frontend/dist /workspace/frontend/dist")
                .contains("FROM eclipse-temurin:21-jre-alpine AS runtime")
                .contains("-Xmx512m");
    }

    @Test
    void dockerComposeProdIsolaPostgresNaRedeInternaEIncluiBackupDiario() throws IOException {
        Path composeProd = RAIZ.resolve("docker-compose.prod.yml");
        assertThat(composeProd).exists();

        String conteudo = Files.readString(composeProd);
        assertThat(conteudo)
                .contains("services:")
                .contains("  db:")
                .contains("  app:")
                .contains("  backup:")
                .contains("postgres:17-alpine")
                .contains("SPRING_PROFILES_ACTIVE: prod")
                .contains("BACKUP_RETENCAO_DIAS: ${BACKUP_RETENCAO_DIAS:-30}")
                .contains("/usr/local/bin/backup.sh")
                .doesNotContain("5432:5432");
    }

    @Test
    void scriptDeBackupUsaPgDumpRetencaoDe30DiasERcloneParaCopiaExterna() throws IOException {
        Path scriptBackup = RAIZ.resolve("deploy/backup.sh");
        assertThat(scriptBackup).exists();

        String conteudo = Files.readString(scriptBackup);
        assertThat(conteudo)
                .contains("pg_dump")
                .contains("BACKUP_RETENCAO_DIAS:-30")
                .contains("-mtime +\"$RETENCAO_DIAS\"")
                .contains("rclone copy")
                .contains("rclone delete");
    }

    @Test
    void guiaDeDeployDocumentaVpsFirewallCloudflareBackupMonitoramentoESearchConsole() throws IOException {
        Path guiaDeploy = RAIZ.resolve("doc/deploy.md");
        assertThat(guiaDeploy).exists();

        String conteudo = Files.readString(guiaDeploy);
        assertThat(conteudo)
                .contains("ufw allow 22/tcp")
                .contains("ufw allow 80/tcp")
                .contains("ufw allow 443/tcp")
                .contains("Full (strict)")
                .contains("Cache Rules")
                .contains("UptimeRobot")
                .contains("Search Console")
                .contains("sitemap.xml")
                .contains("pg_dump")
                .contains("200 a 300 questões");
    }
}
