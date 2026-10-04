import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

class SonarScopeCheck {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var stream = Files.newInputStream(Path.of(args[0]))) {
            properties.load(stream);
        }
        Path root = Path.of(args[1]).toRealPath();
        boolean analysis = Boolean.parseBoolean(args[2]);
        var modules = Arrays.asList(properties.getProperty("sonar.modules").split(","));
        String frontend = "com.project:frontend-analysis.";
        if (analysis) {
            require(properties.getProperty(frontend + "sonar.sources", "")
                    .equals(root.resolve("frontend/ecommerce-app/src").toString()),
                    "actual scanner omitted frontend source scope");
            require(modules.contains("com.project:frontend-analysis"), "frontend analysis module not selected");
            var exclusions = Arrays.asList(properties.getProperty(frontend + "sonar.exclusions", "").split(","));
            require(exclusions.stream().anyMatch(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern)
                    .matches(Path.of("src/test/setup.ts"))), "frontend test support leaked into production scope");
            require(Path.of(properties.getProperty(frontend + "sonar.javascript.lcov.reportPaths"))
                    .equals(root.resolve("frontend/ecommerce-app/coverage/sonar-lcov.info")),
                    "frontend scanner coverage location is incorrect");
            Path report = Path.of(properties.getProperty(frontend + "sonar.javascript.lcov.reportPaths"));
            if (!Files.isRegularFile(report)) report = root.resolve("frontend/ecommerce-app/coverage/lcov.info");
            Path frontendBase = Path.of(properties.getProperty(frontend + "sonar.projectBaseDir"));
            Path sourceRoot = root.resolve("frontend/ecommerce-app/src");
            int files = 0;
            for (String line : Files.readAllLines(report)) {
                if (!line.startsWith("SF:")) continue;
                Path source = frontendBase.resolve(line.substring(3)).normalize();
                require(source.startsWith(sourceRoot) && Files.isRegularFile(source),
                        "LCOV source does not resolve within frontend scanner scope");
                files++;
            }
            require(files > 0, "frontend LCOV contains no source records");
            System.out.println("PASS: " + files + " LCOV source identities resolve within actual frontend scanner scope");
        } else {
            require(!modules.contains("com.project:frontend-analysis"), "frontend analysis module leaked into normal builds");
        }
        require(modules.size() == (analysis ? 13 : 12), "unexpected scanner module count");
        for (String module : modules) {
            if (module.equals("com.project:frontend-analysis")) continue;
            String prefix = module + ".";
            Path base = Path.of(properties.getProperty(prefix + "sonar.projectBaseDir"));
            require(properties.getProperty(prefix + "sonar.sources", "").contains(base.resolve("src/main/java").toString()),
                    "Java source scope changed for " + module);
            if (Files.isDirectory(base.resolve("src/test/java"))) {
                require(properties.getProperty(prefix + "sonar.tests", "").contains(base.resolve("src/test/java").toString()),
                        "Java test classification changed for " + module);
            }
            require(Path.of(properties.getProperty(prefix + "sonar.coverage.jacoco.xmlReportPaths"))
                    .equals(base.resolve("target/site/jacoco/jacoco.xml")), "module-local coverage changed for " + module);
            require(properties.getProperty(prefix + "sonar.java.source").equals("17"), "Java target changed for " + module);
        }
        System.out.println("PASS: actual Maven scanner scope, coverage locations, and Java test classification (analysis=" + analysis + ")");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
