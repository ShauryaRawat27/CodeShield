package com.codeshield.service;

import com.codeshield.config.RepositoryInspectionProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Exposure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryInspectionServiceTest {

    private final RepositoryInspectionService service = new RepositoryInspectionService(properties());

    @TempDir
    Path repository;

    @Test
    void extractsSecurityEvidenceFromRepositoryFiles() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                  </parent>
                  <properties><java.version>17</java.version></properties>
                </project>
                """);
        Files.writeString(repository.resolve("Dockerfile"), """
                FROM eclipse-temurin:17-jre
                USER root
                """);
        Files.writeString(repository.resolve("compose.yaml"), """
                services:
                  app:
                    build: .
                    ports:
                      - "8080:8080"
                    volumes:
                      - /var/run/docker.sock:/var/run/docker.sock
                  database:
                    image: postgres:16
                    ports:
                      - "5432:5432"
                """);

        RepositoryInspection inspection = service.inspect(asset(), repository.toString());

        assertThat(inspection.inspectedFiles()).containsExactly("Dockerfile", "compose.yaml", "pom.xml");
        assertThat(inspection.scanRequest().getJavaVersion()).isEqualTo("17");
        assertThat(inspection.scanRequest().getDependencyVersion()).isEqualTo("3.2.0");
        assertThat(inspection.scanRequest().getContainerUser()).isEqualTo("root");
        assertThat(inspection.scanRequest().getDatabaseExposure()).isEqualTo(Exposure.PUBLIC);
        assertThat(inspection.scanRequest().isDockerSocketMounted()).isTrue();
        assertThat(inspection.scanRequest().isInternetFacing()).isTrue();
        assertThat(inspection.scanRequest().getImageDigest()).isEqualTo("UNPINNED_IMAGE");
        assertThat(inspection.scanRequest().getEvidenceSource()).contains(repository.toRealPath().toString());
    }

    @Test
    void rejectsDirectoriesWithoutSupportedConfiguration() {
        assertThatThrownBy(() -> service.inspect(asset(), repository.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No supported files");
    }

    @Test
    void rejectsRemoteRepositoriesWithoutHttps() {
        assertThatThrownBy(() -> service.inspect(asset(), "http://example.com/repository.git"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public HTTPS URL");
    }

    private ApplicationAsset asset() {
        ApplicationAsset asset = new ApplicationAsset();
        asset.setId(7L);
        asset.setName("Test Service");
        asset.setOwner("Application Security Team");
        asset.setExpectedJavaVersion("21");
        asset.setExpectedDependencyVersion("3.3.6");
        asset.setExpectedContainerUser("appuser");
        asset.setExpectedDatabaseExposure(Exposure.INTERNAL);
        asset.setExpectedImageDigest("sha256:approved-image");
        return asset;
    }

    private static RepositoryInspectionProperties properties() {
        RepositoryInspectionProperties properties = new RepositoryInspectionProperties();
        properties.setMaximumFileSizeBytes(1_000_000);
        properties.setMaximumSearchDepth(6);
        properties.setCloneDepth(1);
        properties.setCloneTimeoutSeconds(60);
        properties.setIgnoredDirectories(java.util.Set.of(
                ".git", ".idea", ".m2", "node_modules", "target", "build", ".gradle"
        ));
        properties.setDatabasePorts(java.util.Set.of("1433", "3306", "5432", "6379", "27017"));
        return properties;
    }
}
