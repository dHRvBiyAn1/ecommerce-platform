#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$root/target"
temporary=$(mktemp -d "$root/target/sonar-scope.XXXXXX")
trap 'rm -rf "$temporary"' EXIT

# Dump mode exercises the real scanner converter without contacting Sonar.
# Keep credentials out of its temporary properties file, even in authenticated CI.
env -u SONAR_TOKEN -u SONAR_SCANNER_JSON_PARAMS -u SONAR_AUTH_TOKEN -u SONAR_LOGIN \
  "$root/mvnw" -B -ntp -Psonar-analysis -DskipTests install \
  org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://127.0.0.1:9 \
  -Dsonar.scanner.internal.dumpToFile="$temporary/analysis.properties"
java "$root/scripts/fixtures/SonarScopeCheck.java" "$temporary/analysis.properties" "$root" true

env -u SONAR_TOKEN -u SONAR_SCANNER_JSON_PARAMS -u SONAR_AUTH_TOKEN -u SONAR_LOGIN \
  "$root/mvnw" -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://127.0.0.1:9 \
  -Dsonar.scanner.internal.dumpToFile="$temporary/default.properties"
java "$root/scripts/fixtures/SonarScopeCheck.java" "$temporary/default.properties" "$root" false
