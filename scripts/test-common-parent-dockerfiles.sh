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

workflow=.github/workflows/ci.yml
if [[ ! -f "$workflow" ]]; then
  printf '%s\n' "missing workflow: $workflow" >&2
  failed=1
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
    printf '%s\n' "$workflow: images.strategy.matrix.service must list exactly ${#expected_image_services[@]} common-consuming services" >&2
    failed=1
  else
    for i in "${!expected_image_services[@]}"; do
      if [[ "${image_services[$i]}" != "${expected_image_services[$i]}" ]]; then
        printf '%s\n' "$workflow: images.strategy.matrix.service[$i] expected ${expected_image_services[$i]}, got ${image_services[$i]}" >&2
        failed=1
      fi
    done
  fi
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' "all ${#dockerfiles[@]} Dockerfiles install the root parent POM before services/common and CI builds all ${#expected_image_services[@]} common-consuming images"
