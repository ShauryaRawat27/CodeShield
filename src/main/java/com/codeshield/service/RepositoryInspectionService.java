package com.codeshield.service;

import com.codeshield.config.RepositoryInspectionProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Exposure;
import com.codeshield.web.dto.ScanRequest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class RepositoryInspectionService {

    private static final Set<String> COMPOSE_FILES = Set.of(
            "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml"
    );
    private static final Pattern XML_JAVA_VERSION = Pattern.compile(
            "<(?:java\\.version|maven\\.compiler\\.(?:release|source))>\\s*([^<]+)\\s*</",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GRADLE_JAVA_VERSION = Pattern.compile(
            "(?:JavaLanguageVersion\\.of\\(|VERSION_|sourceCompatibility\\s*=\\s*['\\\"]?)(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOCKER_JAVA_VERSION = Pattern.compile(
            "(?im)^\\s*FROM\\s+\\S*(?:temurin|openjdk|corretto|java)\\S*:(\\d+)"
    );
    private static final Pattern DOCKER_USER = Pattern.compile("(?im)^\\s*USER\\s+([^\\s#]+)");
    private static final Pattern SPRING_BOOT_MAVEN_VERSION = Pattern.compile(
            "(?is)<parent>.*?<artifactId>spring-boot-starter-parent</artifactId>.*?<version>\\s*([^<]+)\\s*</version>.*?</parent>"
    );
    private static final Pattern SPRING_BOOT_GRADLE_VERSION = Pattern.compile(
            "(?i)(?:id\\s*\\(?['\\\"]org\\.springframework\\.boot['\\\"]\\)?|org\\.springframework\\.boot)\\s*version\\s*['\\\"]([^'\\\"]+)"
    );
    private static final Pattern COMPOSE_IMAGE = Pattern.compile("(?im)^\\s*image:\\s*['\\\"]?([^'\\\"#\\s]+)");
    private static final Pattern PORT_MAPPING = Pattern.compile(
            "(?m)^\\s*-\\s*['\\\"]?(?:(127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::])\\s*:)?(\\d{2,5})\\s*:\\s*(\\d{2,5})"
    );
    private final RepositoryInspectionProperties properties;

    public RepositoryInspectionService(RepositoryInspectionProperties properties) {
        this.properties = properties;
    }

    public RepositoryInspection inspect(ApplicationAsset asset, String repositorySource) {
        if (repositorySource == null || repositorySource.isBlank()) {
            throw new IllegalArgumentException("Enter a public HTTPS Git URL or a local repository path.");
        }
        String source = repositorySource.trim();
        if (source.regionMatches(true, 0, "https://", 0, 8)) {
            return inspectRemote(asset, source);
        }
        if (source.contains("://")) {
            throw new IllegalArgumentException("Remote repositories must use a public HTTPS URL.");
        }

        Path root = validateRoot(source);
        return inspectRoot(asset, root, "Local repository: " + root);
    }

    private RepositoryInspection inspectRemote(ApplicationAsset asset, String remoteUrl) {
        validateRemoteUrl(remoteUrl);
        Path temporaryRepository = null;
        try {
            temporaryRepository = Files.createTempDirectory("codeshield-repository-");
            try (Git ignored = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(temporaryRepository.toFile())
                    .setDepth(properties.getCloneDepth())
                    .setCloneAllBranches(false)
                    .setNoTags()
                    .setTimeout(properties.getCloneTimeoutSeconds())
                    .call()) {
                return inspectRoot(asset, temporaryRepository, "Git repository: " + remoteUrl);
            }
        } catch (IOException | GitAPIException exception) {
            throw new IllegalArgumentException(
                    "CodeShield could not clone the repository. Check that the URL is public and supports HTTPS.",
                    exception
            );
        } finally {
            deleteTemporaryRepository(temporaryRepository);
        }
    }

    private RepositoryInspection inspectRoot(ApplicationAsset asset, Path root, String evidenceSource) {
        List<Path> candidates = findConfigurationFiles(root);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No supported files were found. Add a Dockerfile, compose file, pom.xml, or Gradle build file."
            );
        }

        Path dockerfile = findByName(candidates, "dockerfile").orElse(null);
        Path composeFile = candidates.stream()
                .filter(path -> COMPOSE_FILES.contains(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
        Path pomFile = findByName(candidates, "pom.xml").orElse(null);
        Path gradleFile = candidates.stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase("build.gradle")
                        || path.getFileName().toString().equalsIgnoreCase("build.gradle.kts"))
                .findFirst()
                .orElse(null);

        String docker = read(dockerfile);
        String compose = read(composeFile);
        String pom = read(pomFile);
        String gradle = read(gradleFile);

        ScanRequest scan = new ScanRequest();
        scan.setAssetId(asset.getId());
        scan.setJavaVersion(firstMatch(XML_JAVA_VERSION, pom)
                .or(() -> firstMatch(GRADLE_JAVA_VERSION, gradle))
                .or(() -> firstMatch(DOCKER_JAVA_VERSION, docker))
                .orElse(asset.getExpectedJavaVersion()));
        scan.setContainerUser(lastMatch(DOCKER_USER, docker)
                .orElse(dockerfile == null ? asset.getExpectedContainerUser() : "root"));
        scan.setDockerSocketMounted(compose.contains("/var/run/docker.sock"));
        scan.setDatabaseExposure(databaseExposure(compose));
        scan.setInternetFacing(hasPublicPort(compose));
        scan.setImageDigest(imageReference(compose, dockerfile, asset.getExpectedImageDigest()));
        scan.setDependencyVersion(firstMatch(SPRING_BOOT_MAVEN_VERSION, pom)
                .or(() -> firstMatch(SPRING_BOOT_GRADLE_VERSION, gradle))
                .orElse(asset.getExpectedDependencyVersion()));
        scan.setKnownExploited(false);
        scan.setEvidenceSource(evidenceSource);

        List<String> inspectedFiles = candidates.stream()
                .map(root::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
        return new RepositoryInspection(scan, inspectedFiles);
    }

    private void validateRemoteUrl(String remoteUrl) {
        try {
            URI uri = new URI(remoteUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Enter a valid public HTTPS Git URL.");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Repository URLs containing credentials are not accepted.");
            }
            if (uri.getFragment() != null) {
                throw new IllegalArgumentException("Repository URLs cannot contain a fragment.");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isPrivateAddress(address)) {
                    throw new IllegalArgumentException("The Git URL must resolve to a public server.");
                }
            }
        } catch (URISyntaxException | UnknownHostException exception) {
            throw new IllegalArgumentException("Enter a valid, reachable public HTTPS Git URL.", exception);
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private void deleteTemporaryRepository(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary cleanup is best effort and never changes the source repository.
                }
            });
        } catch (IOException ignored) {
            // The operating system can remove any remaining temporary files later.
        }
    }

    private Path validateRoot(String repositoryPath) {
        try {
            Path root = Path.of(repositoryPath).toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("Repository path must point to a readable directory.");
            }
            return root;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Repository path does not exist or cannot be read.", exception);
        }
    }

    private List<Path> findConfigurationFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root, properties.getMaximumSearchDepth())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !containsIgnoredDirectory(root, path))
                    .filter(this::isSupportedFile)
                    .filter(path -> safeSize(path) <= properties.getMaximumFileSizeBytes())
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("CodeShield could not inspect the repository.", exception);
        }
    }

    private boolean containsIgnoredDirectory(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (properties.getIgnoredDirectories().contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("dockerfile")
                || name.startsWith("dockerfile.")
                || COMPOSE_FILES.contains(name)
                || name.equals("pom.xml")
                || name.equals("build.gradle")
                || name.equals("build.gradle.kts");
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return Long.MAX_VALUE;
        }
    }

    private Optional<Path> findByName(List<Path> paths, String name) {
        return paths.stream().filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).findFirst();
    }

    private String read(Path path) {
        if (path == null) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("CodeShield could not read " + path.getFileName() + ".", exception);
        }
    }

    private Optional<String> firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private Optional<String> lastMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1).trim());
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getLast());
    }

    private Exposure databaseExposure(String compose) {
        Matcher matcher = PORT_MAPPING.matcher(compose);
        while (matcher.find()) {
            String host = matcher.group(1);
            String hostPort = matcher.group(2);
            String containerPort = matcher.group(3);
            if (properties.getDatabasePorts().contains(hostPort)
                    || properties.getDatabasePorts().contains(containerPort)) {
                return "127.0.0.1".equals(host) ? Exposure.INTERNAL : Exposure.PUBLIC;
            }
        }
        return Exposure.INTERNAL;
    }

    private boolean hasPublicPort(String compose) {
        Matcher matcher = PORT_MAPPING.matcher(compose);
        while (matcher.find()) {
            if (!"127.0.0.1".equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private String imageReference(String compose, Path dockerfile, String expectedDigest) {
        Matcher matcher = COMPOSE_IMAGE.matcher(compose);
        while (matcher.find()) {
            String image = matcher.group(1);
            int digestIndex = image.indexOf("@sha256:");
            if (digestIndex >= 0) {
                return image.substring(digestIndex + 1);
            }
        }
        if (matcher.reset().find() || (dockerfile != null && compose.contains("build:"))) {
            return "UNPINNED_IMAGE";
        }
        return expectedDigest;
    }
}
