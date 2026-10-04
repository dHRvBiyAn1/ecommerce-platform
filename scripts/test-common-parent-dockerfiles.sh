#!/usr/bin/env bash
set -euo pipefail

dockerfiles=(
  services/api-gateway/Dockerfile
  services/auth-service/Dockerfile
  services/product-service/Dockerfile
  services/inventory-service/Dockerfile
  services/order-service/Dockerfile
  services/payment-service/Dockerfile
  services/notification-service/Dockerfile
  services/cart-service/Dockerfile
  services/coupon-service/Dockerfile
)

expected_image_services=(
  api-gateway
  auth-service
  product-service
  inventory-service
  order-service
  payment-service
  notification-service
  cart-service
  coupon-service
)

failures=0

fail() {
  printf '%s\n' "$1" >&2
  failures=$((failures + 1))
}

run_instruction_line() {
  local dockerfile=$1
  local command=$2

  awk -v command="$command" '
    function check_instruction() {
      normalized = instruction
      gsub(/\\[[:space:]]*/, " ", normalized)
      gsub(/[[:space:]]+/, " ", normalized)
      if (index(normalized, command)) {
        print start_line
        exit
      }
    }

    /^[[:space:]]*RUN[[:space:]]/ {
      instruction = $0
      start_line = NR
      while (instruction ~ /\\[[:space:]]*$/ && (getline continuation) > 0) {
        instruction = instruction "\n" continuation
      }
      check_instruction()
    }
  ' "$dockerfile"
}

for dockerfile in "${dockerfiles[@]}"; do
  case "$dockerfile" in
    *config*|*discovery*)
      fail "unexpected target in common parent Dockerfile test: $dockerfile"
      continue
      ;;
  esac

  if [[ ! -f "$dockerfile" ]]; then
    fail "missing Dockerfile: $dockerfile"
    continue
  fi

  service=${dockerfile#services/}
  service=${service%/Dockerfile}
  copy_line=$(grep -nF 'COPY --chmod=0755 mvnw pom.xml ./' "$dockerfile" | cut -d: -f1 | head -n1 || true)
  parent_install_line=$(grep -nE '^[[:space:]]*(RUN|&&)?[[:space:]]*\.\/mvnw[[:space:]]+-B[[:space:]]+-ntp[[:space:]]+-DskipTests[[:space:]]+-N[[:space:]]+install([[:space:]]|\\|$)' "$dockerfile" | cut -d: -f1 | head -n1 || true)
  common_install_line=$(grep -nF './mvnw -B -ntp -DskipTests -f services/common/pom.xml install' "$dockerfile" | cut -d: -f1 | head -n1 || true)
  service_package_line=$(grep -nF "./mvnw -B -ntp -DskipTests -f services/$service/pom.xml clean package" "$dockerfile" | cut -d: -f1 | head -n1 || true)
  parent_install_run=$(run_instruction_line "$dockerfile" './mvnw -B -ntp -DskipTests -N install')
  common_install_run=$(run_instruction_line "$dockerfile" './mvnw -B -ntp -DskipTests -f services/common/pom.xml install')
  service_package_run=$(run_instruction_line "$dockerfile" "./mvnw -B -ntp -DskipTests -f services/$service/pom.xml clean package")

  if [[ -z "$copy_line" ]]; then
    fail "$dockerfile: missing COPY --chmod=0755 mvnw pom.xml ./"
  fi

  if [[ -z "$parent_install_line" ]]; then
    fail "$dockerfile: installs common without first installing the root parent POM with ./mvnw -B -ntp -DskipTests -N install"
  fi

  if [[ -z "$common_install_line" ]]; then
    fail "$dockerfile: missing services/common install command"
  fi

  if [[ -z "$service_package_line" ]]; then
    fail "$dockerfile: missing $service package command"
  fi

  if [[ -n "$copy_line" && -n "$parent_install_line" && "$copy_line" -ge "$parent_install_line" ]]; then
    fail "$dockerfile: parent POM install must run after COPY --chmod=0755 mvnw pom.xml ./"
  fi

  if [[ -n "$parent_install_line" && -n "$common_install_line" && "$parent_install_line" -ge "$common_install_line" ]]; then
    fail "$dockerfile: installs common without the parent POM installed first"
  fi

  if [[ -n "$common_install_line" && -n "$service_package_line" && "$common_install_line" -ge "$service_package_line" ]]; then
    fail "$dockerfile: packages $service before installing services/common"
  fi

  if [[ -n "$parent_install_run" && -n "$common_install_run" && -n "$service_package_run" ]] \
    && [[ "$parent_install_run" != "$common_install_run" || "$parent_install_run" != "$service_package_run" ]]; then
    fail "$dockerfile: root parent install, services/common install, and $service package must share one RUN instruction (found RUN lines $parent_install_run, $common_install_run, $service_package_run)"
  fi
done

workflow=.github/workflows/ci.yml
if [[ ! -f "$workflow" ]]; then
  fail "missing workflow: $workflow"
else
  image_services=()
  while IFS= read -r service; do
    image_services+=("$service")
  done < <(
    awk '
      /^  images:/ { in_images = 1; next }
      in_images && /^  [A-Za-z0-9_-]+:/ { exit }
      in_images && /^        service:[[:space:]]*$/ { in_service = 1; next }
      in_service && /^          - / { sub(/^          - /, ""); print; next }
      in_service && $0 !~ /^[[:space:]]*$/ { exit }
    ' "$workflow"
  )

  if [[ "${#image_services[@]}" -ne "${#expected_image_services[@]}" ]]; then
    fail "$workflow: images.strategy.matrix.service must list exactly ${#expected_image_services[@]} common-consuming services"
  else
    for i in "${!expected_image_services[@]}"; do
      if [[ "${image_services[$i]}" != "${expected_image_services[$i]}" ]]; then
        fail "$workflow: images.strategy.matrix.service[$i] expected ${expected_image_services[$i]}, got ${image_services[$i]}"
      fi
    done
  fi
fi

if [[ "$failures" -ne 0 ]]; then
  printf '%s\n' "$failures common parent Dockerfile assertion(s) failed" >&2
  exit 1
fi

printf '%s\n' "all ${#dockerfiles[@]} Dockerfiles install the root parent POM, services/common, and their service package in one RUN; CI builds all ${#expected_image_services[@]} common-consuming images"
