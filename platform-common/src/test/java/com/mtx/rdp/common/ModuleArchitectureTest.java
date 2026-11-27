package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * 读取反应器 POM，核对 ADR-0002 的模块名单和直接依赖。允许集合写在测试里，断言比较的是文件内容。
 */
class ModuleArchitectureTest {

    private static final Set<String> MODULES = Set.of(
            "platform-common",
            "platform-core",
            "platform-messaging",
            "platform-connector",
            "platform-transform",
            "platform-quality",
            "platform-writer",
            "platform-pipeline",
            "app-admin",
            "app-worker");

    private static final Set<String> BANNED_ARTIFACTS = Set.of(
            "mybatis-plus-spring-boot3-starter",
            "mysql-connector-j",
            "spring-boot-starter-jdbc",
            "spring-boot-starter-data-redis",
            "spring-boot-starter-quartz",
            "spring-boot-starter-amqp",
            "spring-kafka",
            "kafka-clients",
            "flyway-core");

    @Test
    void reactorMatchesApprovedModuleBoundaries() throws Exception {
        Path root = reactorRoot();
        Element rootPom = parse(root.resolve("pom.xml"));
        List<String> aggregated = textChildren(directChild(rootPom, "modules"), "module");

        assertEquals(MODULES, Set.copyOf(aggregated));
        assertEquals(aggregated.size(), new HashSet<>(aggregated).size());
        assertEquals(MODULES, childModulesWithPom(root));
        assertTrue(directChildren(rootPom, "dependencies").isEmpty());

        Map<String, Set<DeclaredDependency>> actual = new LinkedHashMap<>();
        for (String module : aggregated) {
            Element pom = parse(root.resolve(module).resolve("pom.xml"));
            assertEquals(module, directText(pom, "artifactId"));
            Set<DeclaredDependency> dependencies = declaredDependencies(pom);
            assertNoInlineVersion(dependencies);
            actual.put(module, dependencies);
        }

        assertEquals(
                Set.of(new DeclaredDependency("org.junit.jupiter", "junit-jupiter", "test")),
                actual.get("platform-common"));
        assertFalse(dependsOn(actual.get("platform-common"), "platform-core"));
        assertEquals(internal("platform-common"), actual.get("platform-core"));
        assertEquals(internal("platform-common", "platform-core"), actual.get("platform-messaging"));
        assertEquals(internal("platform-common", "platform-core"), actual.get("platform-connector"));
        assertEquals(internal("platform-common", "platform-core"), actual.get("platform-transform"));
        assertEquals(internal("platform-common", "platform-core"), actual.get("platform-quality"));
        assertEquals(internal("platform-common", "platform-core"), actual.get("platform-writer"));
        assertEquals(internal("platform-common", "platform-core"), actual.get("platform-pipeline"));
        assertEquals(adminDependencies(), actual.get("app-admin"));
        assertEquals(workerDependencies(), actual.get("app-worker"));

        assertFalse(dependsOn(actual.get("app-admin"), "app-worker"));
        assertFalse(dependsOn(actual.get("app-worker"), "app-admin"));
        assertAcyclic(actual);
        assertBannedArtifactsAbsent(actual);
        assertCoreHasNoInfrastructureGroup(actual.get("platform-core"));
        assertPipelineDoesNotReachImplementations(actual.get("platform-pipeline"));
        assertMyBatisPlusIsManagedButNotUsed(rootPom, actual);
    }

    private static Set<DeclaredDependency> adminDependencies() {
        Set<DeclaredDependency> dependencies = internal(
                "platform-common", "platform-core", "platform-messaging");
        dependencies.addAll(applicationRuntime());
        return dependencies;
    }

    private static Set<DeclaredDependency> workerDependencies() {
        Set<DeclaredDependency> dependencies = internal(
                "platform-common",
                "platform-core",
                "platform-messaging",
                "platform-connector",
                "platform-transform",
                "platform-quality",
                "platform-writer",
                "platform-pipeline");
        dependencies.addAll(applicationRuntime());
        return dependencies;
    }

    private static Set<DeclaredDependency> applicationRuntime() {
        return new LinkedHashSet<>(Set.of(
                new DeclaredDependency("org.springframework.boot", "spring-boot-starter", "compile"),
                new DeclaredDependency("org.springframework.boot", "spring-boot-starter-actuator", "compile"),
                new DeclaredDependency("org.springframework.boot", "spring-boot-starter-test", "test")));
    }

    private static Set<DeclaredDependency> internal(String... artifactIds) {
        Set<DeclaredDependency> dependencies = new LinkedHashSet<>();
        for (String artifactId : artifactIds) {
            dependencies.add(new DeclaredDependency("com.mtx.rdp", artifactId, "compile"));
        }
        return dependencies;
    }

    private static boolean dependsOn(Set<DeclaredDependency> dependencies, String artifactId) {
        return dependencies.stream().anyMatch(dependency -> dependency.artifactId().equals(artifactId));
    }

    private static void assertNoInlineVersion(Set<DeclaredDependency> dependencies) {
        for (DeclaredDependency dependency : dependencies) {
            assertTrue(dependency.version().isEmpty(), dependency.artifactId());
        }
    }

    private static void assertBannedArtifactsAbsent(Map<String, Set<DeclaredDependency>> actual) {
        for (Set<DeclaredDependency> dependencies : actual.values()) {
            for (DeclaredDependency dependency : dependencies) {
                assertFalse(BANNED_ARTIFACTS.contains(dependency.artifactId()), dependency.artifactId());
                assertFalse(dependency.groupId().contains("dolphinscheduler"), dependency.groupId());
            }
        }
    }

    private static void assertCoreHasNoInfrastructureGroup(Set<DeclaredDependency> dependencies) {
        for (DeclaredDependency dependency : dependencies) {
            String groupId = dependency.groupId();
            assertFalse(groupId.startsWith("org.springframework"), groupId);
            assertFalse(groupId.startsWith("org.apache.kafka"), groupId);
            assertFalse(groupId.startsWith("org.mybatis"), groupId);
            assertFalse(groupId.startsWith("com.baomidou"), groupId);
            assertFalse(groupId.startsWith("org.quartz-scheduler"), groupId);
            assertFalse(groupId.startsWith("redis.clients"), groupId);
            assertFalse(groupId.startsWith("io.lettuce"), groupId);
        }
    }

    private static void assertPipelineDoesNotReachImplementations(Set<DeclaredDependency> dependencies) {
        Set<String> forbidden = Set.of(
                "platform-connector",
                "platform-transform",
                "platform-quality",
                "platform-writer");
        for (DeclaredDependency dependency : dependencies) {
            assertFalse(forbidden.contains(dependency.artifactId()), dependency.artifactId());
            assertFalse(dependency.groupId().startsWith("org.quartz-scheduler"), dependency.groupId());
            assertFalse(dependency.artifactId().contains("quartz"), dependency.artifactId());
        }
    }

    private static void assertMyBatisPlusIsManagedButNotUsed(
            Element rootPom,
            Map<String, Set<DeclaredDependency>> actual) {
        Element managed = directChild(directChild(rootPom, "dependencyManagement"), "dependencies");
        List<Element> managedDependencies = directChildren(managed, "dependency");
        boolean mybatisManaged = false;
        for (Element dependency : managedDependencies) {
            if ("mybatis-plus-spring-boot3-starter".equals(directText(dependency, "artifactId"))) {
                mybatisManaged = true;
            }
        }
        assertTrue(mybatisManaged);
        for (Set<DeclaredDependency> dependencies : actual.values()) {
            assertFalse(dependsOn(dependencies, "mybatis-plus-spring-boot3-starter"));
        }
    }

    private static void assertAcyclic(Map<String, Set<DeclaredDependency>> actual) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (Map.Entry<String, Set<DeclaredDependency>> entry : actual.entrySet()) {
            Set<String> edges = new LinkedHashSet<>();
            for (DeclaredDependency dependency : entry.getValue()) {
                if ("com.mtx.rdp".equals(dependency.groupId()) && MODULES.contains(dependency.artifactId())) {
                    edges.add(dependency.artifactId());
                }
            }
            graph.put(entry.getKey(), edges);
        }
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        for (String module : graph.keySet()) {
            walk(module, graph, visiting, visited);
        }
    }

    private static void walk(
            String module,
            Map<String, Set<String>> graph,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(module)) {
            return;
        }
        assertFalse(visiting.contains(module), module);
        visiting.add(module);
        for (String dependency : graph.getOrDefault(module, Set.of())) {
            walk(dependency, graph, visiting, visited);
        }
        visiting.remove(module);
        visited.add(module);
    }

    private static Set<String> childModulesWithPom(Path root) throws IOException {
        Set<String> found = new HashSet<>();
        try (var children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("pom.xml"))) {
                    found.add(child.getFileName().toString());
                }
            }
        }
        return found;
    }

    private static Set<DeclaredDependency> declaredDependencies(Element project) {
        List<Element> dependencyLists = directChildren(project, "dependencies");
        if (dependencyLists.isEmpty()) {
            return Set.of();
        }
        assertEquals(1, dependencyLists.size());
        Set<DeclaredDependency> dependencies = new LinkedHashSet<>();
        for (Element dependency : directChildren(dependencyLists.getFirst(), "dependency")) {
            String scope = directText(dependency, "scope");
            dependencies.add(new DeclaredDependency(
                    directText(dependency, "groupId"),
                    directText(dependency, "artifactId"),
                    scope == null || scope.isBlank() ? "compile" : scope,
                    directText(dependency, "version") == null ? "" : directText(dependency, "version")));
        }
        return dependencies;
    }

    private static Path reactorRoot() throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path pom = dir.resolve("pom.xml");
            if (Files.isRegularFile(pom) && "mtx-data-platform".equals(directText(parse(pom), "artifactId"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 mtx-data-platform 根 POM");
    }

    private static Element parse(Path pom) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try (InputStream input = Files.newInputStream(pom)) {
            return factory.newDocumentBuilder().parse(input).getDocumentElement();
        }
    }

    private static Element directChild(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        assertEquals(1, children.size(), localName);
        return children.getFirst();
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        Node node = parent.getFirstChild();
        while (node != null) {
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                children.add(element);
            }
            node = node.getNextSibling();
        }
        return children;
    }

    private static List<String> textChildren(Element parent, String localName) {
        List<String> values = new ArrayList<>();
        for (Element child : directChildren(parent, localName)) {
            values.add(child.getTextContent().trim());
        }
        return values;
    }

    private static String directText(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        if (children.isEmpty()) {
            return null;
        }
        assertEquals(1, children.size(), localName);
        return children.getFirst().getTextContent().trim();
    }

    private record DeclaredDependency(String groupId, String artifactId, String scope, String version) {

        private DeclaredDependency(String groupId, String artifactId, String scope) {
            this(groupId, artifactId, scope, "");
        }
    }
}
