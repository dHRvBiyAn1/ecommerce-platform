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

failed=0

for dockerfile in "${dockerfiles[@]}"; do
  case "$dockerfile" in
    *config*|*discovery*)
      printf '%s\n' "unexpected target in common parent Dockerfile test: $dockerfile" >&2
      failed=1
      continue
      ;;
  esac

  if [[ ! -f "$dockerfile" ]]; then
    printf '%s\n' "missing Dockerfile: $dockerfile" >&2
    failed=1
    continue
  fi

  copy_line=$(grep -nF 'COPY --chmod=0755 mvnw pom.xml ./' "$dockerfile" | cut -d: -f1 | head -n1 || true)
  parent_install_line=$(grep -nE '^[[:space:]]*(RUN|&&)?[[:space:]]*\.\/mvnw[[:space:]]+-B[[:space:]]+-ntp[[:space:]]+-DskipTests[[:space:]]+-N[[:space:]]+install([[:space:]]|\\|$)' "$dockerfile" | cut -d: -f1 | head -n1 || true)
  common_install_line=$(grep -nF './mvnw -B -ntp -DskipTests -f services/common/pom.xml install' "$dockerfile" | cut -d: -f1 | head -n1 || true)

  if [[ -z "$copy_line" ]]; then
    printf '%s\n' "$dockerfile: missing COPY --chmod=0755 mvnw pom.xml ./" >&2
    failed=1
  fi

  if [[ -z "$parent_install_line" ]]; then
    printf '%s\n' "$dockerfile: installs common without first installing the root parent POM with ./mvnw -B -ntp -DskipTests -N install" >&2
    failed=1
  fi

  if [[ -z "$common_install_line" ]]; then
    printf '%s\n' "$dockerfile: missing services/common install command" >&2
    failed=1
  fi

  if [[ -n "$copy_line" && -n "$parent_install_line" && "$copy_line" -ge "$parent_install_line" ]]; then
    printf '%s\n' "$dockerfile: parent POM install must run after COPY --chmod=0755 mvnw pom.xml ./" >&2
    failed=1
  fi

  if [[ -n "$parent_install_line" && -n "$common_install_line" && "$parent_install_line" -ge "$common_install_line" ]]; then
    printf '%s\n' "$dockerfile: installs common without the parent POM installed first" >&2
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' "all ${#dockerfiles[@]} Dockerfiles install the root parent POM before services/common"
