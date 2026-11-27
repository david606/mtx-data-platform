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
 * 读取反应器 POM。十模块、依赖方向、Core 隔离和 Pipeline 边界是持续约束。
 * 各模块的精确依赖集合只表示当前基线；按 ADR 增加依赖时要同步更新对应预期，不能靠根 POM 全局禁止。
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

    private static final Set<String> CORE_BANNED_DEPENDENCIES = Set.of(
            "org.springframework:*",
            "org.springframework.boot:*",
            "org.springframework.data:*",
            "org.apache.kafka:*",
            "org.springframework.kafka:*",
            "org.mybatis:*",
            "com.baomidou:*",
            "org.quartz-scheduler:*",
            "redis.clients:*",
            "io.lettuce:*",
            "com.mysql:*",
            "org.flywaydb:*",
            "org.apache.doris:*");

    @Test
    void reactorMatchesApprovedModuleBoundaries() throws Exception {
        Path root = reactorRoot();
        Element rootPom = parse(root.resolve("pom.xml"));
        List<String> aggregated = textChildren(directChild(rootPom, "modules"), "module");

        assertEquals(MODULES, Set.copyOf(aggregated));
        assertEquals(aggregated.size(), new HashSet<>(aggregated).size());
        assertEquals(MODULES, childModulesWithPom(root));
        assertTrue(directChildren(rootPom, "dependencies").isEmpty());
        assertRootEnforcerOnlyRequiresJdk25(rootPom);

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
        assertEquals(coreDependencies(), actual.get("platform-core"));
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
        assertCoreHasNoInfrastructureGroup(actual.get("platform-core"));
        assertCoreEnforcerCoversTransitiveInfrastructure(parse(root.resolve("platform-core/pom.xml")));
        assertPipelineDoesNotReachImplementations(actual.get("platform-pipeline"));
        assertMyBatisPlusVersionRemainsManaged(rootPom);
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

    private static Set<DeclaredDependency> coreDependencies() {
        Set<DeclaredDependency> dependencies = internal("platform-common");
        dependencies.add(new DeclaredDependency("org.junit.jupiter", "junit-jupiter", "test"));
        return dependencies;
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

    private static void assertRootEnforcerOnlyRequiresJdk25(Element rootPom) {
        Element rules = enforcerRules(rootPom, "require-jdk-25");
        assertEquals("[25,26)", directText(directChild(rules, "requireJavaVersion"), "version"));
        assertTrue(elementsNamed(rootPom, "bannedDependencies").isEmpty());
    }

    private static void assertCoreEnforcerCoversTransitiveInfrastructure(Element corePom) {
        Element rules = enforcerRules(corePom, "ban-infrastructure-frameworks");
        Element banned = directChild(rules, "bannedDependencies");
        assertEquals("true", directText(banned, "searchTransitive"));
        assertEquals(CORE_BANNED_DEPENDENCIES, Set.copyOf(textChildren(directChild(banned, "excludes"), "exclude")));
    }

    private static Element enforcerRules(Element pom, String executionId) {
        Element plugins = directChild(directChild(pom, "build"), "plugins");
        Element enforcer = null;
        for (Element plugin : directChildren(plugins, "plugin")) {
            if ("maven-enforcer-plugin".equals(directText(plugin, "artifactId"))) {
                enforcer = plugin;
                break;
            }
        }
        if (enforcer == null) {
            throw new AssertionError("缺少 maven-enforcer-plugin");
        }
        Element executions = directChild(enforcer, "executions");
        for (Element execution : directChildren(executions, "execution")) {
            if (executionId.equals(directText(execution, "id"))) {
                return directChild(directChild(execution, "configuration"), "rules");
            }
        }
        throw new AssertionError("缺少 Enforcer 执行: " + executionId);
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

    private static void assertMyBatisPlusVersionRemainsManaged(Element rootPom) {
        Element managed = directChild(directChild(rootPom, "dependencyManagement"), "dependencies");
        boolean mybatisManaged = false;
        for (Element dependency : directChildren(managed, "dependency")) {
            if ("mybatis-plus-spring-boot3-starter".equals(directText(dependency, "artifactId"))) {
                mybatisManaged = true;
            }
        }
        assertTrue(mybatisManaged);
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

    private static List<Element> elementsNamed(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        collectNamed(parent, localName, found);
        return found;
    }

    private static void collectNamed(Element parent, String localName, List<Element> found) {
        Node node = parent.getFirstChild();
        while (node != null) {
            if (node instanceof Element element) {
                if (localName.equals(element.getLocalName())) {
                    found.add(element);
                }
                collectNamed(element, localName, found);
            }
            node = node.getNextSibling();
        }
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
