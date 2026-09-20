/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import com.dixon.sandbox.model.ServiceCategory;
import com.dixon.sandbox.model.ServiceConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class ProjectModuleScanner {

	private static final System.Logger LOGGER = System.getLogger(ProjectModuleScanner.class.getName());
	private static final Pattern GRADLE_INCLUDE = Pattern.compile("(?m)^\\s*include\\s*(?:\\(([^)]*)\\)|(.+))$");
	private static final Pattern QUOTED_VALUE = Pattern.compile("['\"]([^'\"]+)['\"]");
	private static final Pattern GRADLE_PROJECT_DIR = Pattern.compile(
			"(?m)^\\s*project\\(['\"]([^'\"]+)['\"]\\)\\.projectDir\\s*=\\s*(?:file\\()?['\"]([^'\"]+)['\"]\\)?");
	private static final Pattern JSON_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern JSON_SCRIPTS = Pattern.compile("\"scripts\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
	private static final Pattern JSON_SCRIPT = Pattern.compile("\"(dev|start|serve)\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern COMMAND_PORT = Pattern.compile("(?:--port(?:=|\\s+)|-p\\s+)(\\d+)");
	private static final Pattern CONFIG_PORT = Pattern.compile("(?:\\$\\{[^}:]+:)?(\\d+)(?:\\})?");
	private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", ".next", "build", "dist",
			"node_modules", "out", "target");

	private final File rootDir;
	private final boolean windows;

	ProjectModuleScanner(File rootDir) {
		this.rootDir = rootDir;
		this.windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
	}

	List<ServiceConfig> scan() {
		Map<String, ServiceConfig> detected = new LinkedHashMap<>();
		detectGradleApplications(detected);
		detectMavenApplications(detected);
		detectWebApplications(detected);
		return detected.values().stream().sorted(Comparator.comparing(ServiceConfig::getCategory)
				.thenComparing(ServiceConfig::getName, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	private void detectGradleApplications(Map<String, ServiceConfig> detected) {
		File settingsFile = firstExisting(rootDir, "settings.gradle", "settings.gradle.kts");
		Map<String, File> modules = settingsFile == null ? new LinkedHashMap<>() : readGradleModules(settingsFile);
		modules.putIfAbsent(":", rootDir);
		for (Map.Entry<String, File> entry : modules.entrySet()) {
			File moduleDir = entry.getValue();
			File buildFile = firstExisting(moduleDir, "build.gradle", "build.gradle.kts");
			if (buildFile == null) {
				continue;
			}
			String buildText = readText(buildFile);
			boolean springBoot = hasGradlePlugin(buildText, "org.springframework.boot")
					|| hasJavaSource(moduleDir, "@SpringBootApplication");
			boolean application = springBoot || hasApplicationPlugin(buildText)
					|| hasJavaSource(moduleDir, "static void main");
			if (!application || !new File(moduleDir, "src/main").isDirectory() || isSandboxModule(moduleDir)) {
				continue;
			}

			String task = springBoot ? "bootRun" : "run";
			String projectPath = entry.getKey();
			String command = gradleCommand(projectPath, task);
			addJavaApplication(detected, moduleDir, "Gradle", command, springBoot);
		}
	}

	private Map<String, File> readGradleModules(File settingsFile) {
		String settings = readText(settingsFile);
		Map<String, File> remappedDirectories = new LinkedHashMap<>();
		Matcher directoryMatcher = GRADLE_PROJECT_DIR.matcher(settings);
		while (directoryMatcher.find()) {
			remappedDirectories.put(normalizeGradlePath(directoryMatcher.group(1)),
					new File(rootDir, directoryMatcher.group(2)));
		}

		Map<String, File> modules = new LinkedHashMap<>();
		Matcher includeMatcher = GRADLE_INCLUDE.matcher(settings);
		while (includeMatcher.find()) {
			String arguments = includeMatcher.group(1) != null ? includeMatcher.group(1) : includeMatcher.group(2);
			Matcher valueMatcher = QUOTED_VALUE.matcher(arguments);
			while (valueMatcher.find()) {
				String projectPath = normalizeGradlePath(valueMatcher.group(1));
				File defaultDirectory = new File(rootDir, projectPath.substring(1).replace(':', File.separatorChar));
				modules.put(projectPath, remappedDirectories.getOrDefault(projectPath, defaultDirectory));
			}
		}
		return modules;
	}

	private String normalizeGradlePath(String value) {
		return value.startsWith(":") ? value : ":" + value;
	}

	private boolean hasApplicationPlugin(String buildText) {
		return hasGradlePlugin(buildText, "application");
	}

	private boolean hasGradlePlugin(String buildText, String pluginId) {
		String quotedPlugin = Pattern.quote(pluginId);
		return Pattern.compile(
				"(?:id\\s*\\(?\\s*['\"]" + quotedPlugin + "['\"]|apply\\s+plugin:\\s*['\"]" + quotedPlugin + "['\"])")
				.matcher(buildText).find();
	}

	private boolean isSandboxModule(File moduleDir) {
		return new File(moduleDir, "src/main/java/com/dixon/sandbox/DevSandboxApp.java").isFile();
	}

	private String gradleCommand(String projectPath, String task) {
		String wrapper = new File(rootDir, windows ? "gradlew.bat" : "gradlew").isFile()
				? (windows ? "gradlew.bat" : "./gradlew")
				: "gradle";
		String qualifiedTask = ":".equals(projectPath) ? task : projectPath + ":" + task;
		return wrapper + " " + qualifiedTask + " --no-daemon";
	}

	private void detectMavenApplications(Map<String, ServiceConfig> detected) {
		File rootPom = new File(rootDir, "pom.xml");
		if (!rootPom.isFile()) {
			return;
		}
		Deque<File> pending = new ArrayDeque<>();
		Set<Path> visited = new HashSet<>();
		pending.add(rootPom);
		while (!pending.isEmpty()) {
			File pom = pending.removeFirst();
			Path normalizedPom = pom.toPath().toAbsolutePath().normalize();
			if (!visited.add(normalizedPom)) {
				continue;
			}
			MavenProject project = readMavenProject(pom);
			if (project == null) {
				continue;
			}
			File moduleDir = pom.getParentFile();
			boolean springBoot = project.springBoot() || hasJavaSource(moduleDir, "@SpringBootApplication");
			boolean runnable = project.runnable() || hasJavaSource(moduleDir, "static void main");
			if (runnable && new File(moduleDir, "src/main").isDirectory()) {
				String wrapper = new File(rootDir, windows ? "mvnw.cmd" : "mvnw").isFile()
						? (windows ? "mvnw.cmd" : "./mvnw")
						: "mvn";
				String relativePom = rootDir.toPath().relativize(pom.toPath()).toString();
				String command = wrapper + " -f " + quotePath(relativePom) + " "
						+ (springBoot ? "spring-boot:run" : "exec:java");
				addJavaApplication(detected, moduleDir, "Maven", command, springBoot);
			}
			for (String module : project.modules()) {
				File childPom = new File(moduleDir, module + File.separator + "pom.xml");
				if (childPom.isFile()) {
					pending.addLast(childPom);
				}
			}
		}
	}

	private MavenProject readMavenProject(File pom) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			Element project = factory.newDocumentBuilder().parse(pom).getDocumentElement();
			List<String> modules = childValues(firstChild(project, "modules"), "module");
			String xml = readText(pom);
			boolean springBoot = xml.contains("spring-boot-maven-plugin");
			boolean application = springBoot || xml.contains("exec-maven-plugin");
			return new MavenProject(modules, application, springBoot);
		} catch (Exception exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to read Maven project " + pom, exception);
			return null;
		}
	}

	private Element firstChild(Element parent, String name) {
		if (parent == null) {
			return null;
		}
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element
					&& name.equals(element.getLocalName() != null ? element.getLocalName() : element.getNodeName())) {
				return element;
			}
		}
		return null;
	}

	private List<String> childValues(Element parent, String name) {
		List<String> values = new ArrayList<>();
		if (parent == null) {
			return values;
		}
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && name.equals(element.getNodeName())) {
				values.add(element.getTextContent().trim());
			}
		}
		return values;
	}

	private void addJavaApplication(Map<String, ServiceConfig> detected, File moduleDir, String buildSystem,
			String command, boolean springBoot) {
		String name = readSpringApplicationName(moduleDir);
		if (name == null || name.isBlank()) {
			name = moduleDir.getName();
		}
		boolean worker = isNonWebSpringApplication(moduleDir);
		int port = worker ? 0 : readSpringPort(moduleDir);
		String healthUrl = springBoot && port > 0 ? "http://localhost:" + port + "/actuator/health" : "";
		String id = moduleId(moduleDir, ServiceCategory.JAVA_APP);
		ServiceConfig config = new ServiceConfig(id, name, "Detected " + buildSystem + " Java application",
				ServiceCategory.JAVA_APP, rootDir, command, port, healthUrl);
		if (port > 0) {
			config.addEnvVar("SERVER_PORT", String.valueOf(port));
		}
		detected.putIfAbsent(id, config);
	}

	private void detectWebApplications(Map<String, ServiceConfig> detected) {
		try (var paths = Files.walk(rootDir.toPath(), 7)) {
			paths.filter(path -> path.getFileName().toString().equals("package.json"))
					.filter(path -> !containsIgnoredDirectory(path)).forEach(path -> addWebApplication(detected, path));
		} catch (IOException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to scan web modules under " + rootDir, exception);
		}
	}

	private boolean containsIgnoredDirectory(Path path) {
		for (Path part : rootDir.toPath().relativize(path)) {
			if (IGNORED_DIRECTORIES.contains(part.toString())) {
				return true;
			}
		}
		return false;
	}

	private void addWebApplication(Map<String, ServiceConfig> detected, Path packageJson) {
		String json = readText(packageJson.toFile());
		Matcher scriptsMatcher = JSON_SCRIPTS.matcher(json);
		if (!scriptsMatcher.find()) {
			return;
		}
		Map<String, String> scripts = new LinkedHashMap<>();
		Matcher scriptMatcher = JSON_SCRIPT.matcher(scriptsMatcher.group(1));
		while (scriptMatcher.find()) {
			scripts.put(scriptMatcher.group(1), scriptMatcher.group(2));
		}
		String scriptName = List.of("dev", "start", "serve").stream().filter(scripts::containsKey).findFirst()
				.orElse(null);
		if (scriptName == null) {
			return;
		}
		String scriptCommand = scripts.get(scriptName);
		File moduleDir = packageJson.getParent().toFile();
		String name = firstMatch(JSON_NAME, json);
		if (name == null || name.isBlank()) {
			name = moduleDir.getName();
		}
		String command = packageManagerCommand(moduleDir, scriptName);
		int port = readWebPort(moduleDir, scriptCommand);
		String id = moduleId(moduleDir, ServiceCategory.WEB_APP);
		ServiceConfig config = new ServiceConfig(id, name, "Detected JavaScript/TypeScript web application",
				ServiceCategory.WEB_APP, moduleDir, command, port, port > 0 ? "http://localhost:" + port + "/" : "");
		detected.putIfAbsent(id, config);
	}

	private String packageManagerCommand(File moduleDir, String scriptName) {
		if (new File(moduleDir, "pnpm-lock.yaml").isFile()) {
			return "pnpm " + scriptName;
		}
		if (new File(moduleDir, "yarn.lock").isFile()) {
			return "yarn " + scriptName;
		}
		if (new File(moduleDir, "bun.lock").isFile() || new File(moduleDir, "bun.lockb").isFile()) {
			return "bun run " + scriptName;
		}
		return "npm run " + scriptName;
	}

	private int readWebPort(File moduleDir, String scriptCommand) {
		int commandPort = parsePort(firstMatch(COMMAND_PORT, scriptCommand));
		if (commandPort > 0) {
			return commandPort;
		}
		for (String configName : List.of("vite.config.ts", "vite.config.js", "vite.config.mts", "vite.config.mjs",
				"angular.json")) {
			File config = new File(moduleDir, configName);
			if (!config.isFile()) {
				continue;
			}
			Matcher portMatcher = Pattern.compile("(?m)\\bport\\s*[:=]\\s*(\\d+)").matcher(readText(config));
			if (portMatcher.find()) {
				return parsePort(portMatcher.group(1));
			}
		}
		return 0;
	}

	private String readSpringApplicationName(File moduleDir) {
		for (File config : springConfigurationFiles(moduleDir)) {
			String text = readText(config);
			Matcher properties = Pattern.compile("(?m)^\\s*spring\\.application\\.name\\s*[=:]\\s*([^#\\s]+)")
					.matcher(text);
			if (properties.find()) {
				return properties.group(1);
			}
			String yamlName = readNestedYamlValue(text, "spring", "application", "name");
			if (yamlName != null) {
				return yamlName;
			}
		}
		return null;
	}

	private int readSpringPort(File moduleDir) {
		for (File config : springConfigurationFiles(moduleDir)) {
			String text = readText(config);
			Matcher properties = Pattern.compile("(?m)^\\s*server\\.port\\s*[=:]\\s*([^#\\s]+)").matcher(text);
			if (properties.find()) {
				int port = parsePort(properties.group(1));
				if (port > 0) {
					return port;
				}
			}
			int port = parsePort(readNestedYamlValue(text, "server", "port"));
			if (port > 0) {
				return port;
			}
		}
		return 0;
	}

	private boolean isNonWebSpringApplication(File moduleDir) {
		for (File config : springConfigurationFiles(moduleDir)) {
			String text = readText(config);
			if (Pattern
					.compile("(?m)^\\s*spring\\.main\\.web-application-type\\s*[=:]\\s*none\\s*$",
							Pattern.CASE_INSENSITIVE)
					.matcher(text).find()
					|| "none".equalsIgnoreCase(readNestedYamlValue(text, "spring", "main", "web-application-type"))) {
				return true;
			}
		}
		return false;
	}

	private List<File> springConfigurationFiles(File moduleDir) {
		File resources = new File(moduleDir, "src/main/resources");
		File[] files = resources.listFiles(file -> file.isFile() && (file.getName().matches("application.*\\.ya?ml")
				|| file.getName().matches("application.*\\.properties")));
		if (files == null) {
			return List.of();
		}
		return List.of(files).stream().sorted(Comparator.comparing(file -> !file.getName().equals("application.yml")
				&& !file.getName().equals("application.yaml") && !file.getName().equals("application.properties")))
				.toList();
	}

	private String readNestedYamlValue(String text, String... path) {
		String[] lines = text.split("\\R");
		int parentIndent = -1;
		int startLine = 0;
		for (int pathIndex = 0; pathIndex < path.length; pathIndex++) {
			boolean found = false;
			for (int lineIndex = startLine; lineIndex < lines.length; lineIndex++) {
				String line = lines[lineIndex];
				if (line.isBlank() || line.stripLeading().startsWith("#")) {
					continue;
				}
				int indent = line.length() - line.stripLeading().length();
				if (parentIndent >= 0 && indent <= parentIndent) {
					break;
				}
				String expectedKey = path[pathIndex] + ":";
				String trimmed = line.strip();
				if ((parentIndent >= 0 || indent == 0) && trimmed.startsWith(expectedKey)) {
					String value = trimmed.substring(expectedKey.length()).strip();
					if (pathIndex == path.length - 1) {
						int comment = value.indexOf(" #");
						return comment >= 0 ? value.substring(0, comment).strip() : value;
					}
					parentIndent = indent;
					startLine = lineIndex + 1;
					found = true;
					break;
				}
			}
			if (!found) {
				return null;
			}
		}
		return null;
	}

	private boolean hasJavaSource(File moduleDir, String marker) {
		Path javaSources = new File(moduleDir, "src/main/java").toPath();
		if (!Files.isDirectory(javaSources)) {
			return false;
		}
		try (var paths = Files.walk(javaSources)) {
			return paths.filter(path -> path.getFileName().toString().endsWith(".java"))
					.anyMatch(path -> readText(path.toFile()).contains(marker));
		} catch (IOException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to scan Java sources under " + moduleDir, exception);
			return false;
		}
	}

	private int parsePort(String value) {
		if (value == null) {
			return 0;
		}
		Matcher matcher = CONFIG_PORT.matcher(value.strip());
		if (!matcher.matches()) {
			return 0;
		}
		try {
			int port = Integer.parseInt(matcher.group(1));
			return port > 0 && port <= 65535 ? port : 0;
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	private String moduleId(File moduleDir, ServiceCategory category) {
		Path relative = rootDir.toPath().relativize(moduleDir.toPath());
		String path = relative.toString().isBlank() ? rootDir.getName() : relative.toString();
		return category.name().toLowerCase(Locale.ROOT) + "-"
				+ path.replace(File.separatorChar, '-').replaceAll("[^A-Za-z0-9_.-]", "-");
	}

	private String quotePath(String path) {
		return path.contains(" ") ? "\"" + path.replace("\"", "\\\"") + "\"" : path;
	}

	private File firstExisting(File directory, String... names) {
		for (String name : names) {
			File candidate = new File(directory, name);
			if (candidate.isFile()) {
				return candidate;
			}
		}
		return null;
	}

	private String readText(File file) {
		try {
			return Files.readString(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to read " + file, exception);
			return "";
		}
	}

	private String firstMatch(Pattern pattern, String value) {
		Matcher matcher = pattern.matcher(value);
		return matcher.find() ? matcher.group(1) : null;
	}

	private record MavenProject(List<String> modules, boolean runnable, boolean springBoot) {
	}
}
