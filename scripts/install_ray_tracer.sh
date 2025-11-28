#!/usr/bin/env bash
set -euo pipefail

# install_ray_tracer.sh
# Usage:
#  - Ensure `.env` at project root contains `RAY_TRACER_PATH` (or set as env var)
#  - Optionally set `RAY_TRACER_GROUP_ID`, `RAY_TRACER_ARTIFACT_ID`, `RAY_TRACER_VERSION`
#  - Run: `./scripts/install_ray_tracer.sh [path/to/ray_tracer.jar]`

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${PROJECT_ROOT}/.env"
if [ -f "${ENV_FILE}" ]; then
  # Load simple KEY=VALUE pairs from .env (ignore lines starting with #)
  while IFS='=' read -r key val; do
    # skip comments and empty lines
    [[ "$key" =~ ^# ]] && continue
    [[ -z "$key" ]] && continue
    # trim whitespace
    key="$(echo "$key" | xargs)"
    val="$(echo "${val:-}" | sed -e 's/^\s*"//' -e 's/"\s*$//' | xargs)"
    export "$key"="$val"
  done < <(grep -v '^\s*#' "${ENV_FILE}" | sed '/^\s*$/d')
fi

# Allow override via first argument, then env var from .env
JAR_PATH="${1:-${RAY_TRACER_PATH:-}}"

if [ -z "${JAR_PATH}" ]; then
  echo "Erreur: chemin du JAR non défini. Passez le chemin en argument ou définissez RAY_TRACER_PATH dans .env"
  exit 1
fi

# If path is relative and not found, try relative to project root
if [ ! -e "${JAR_PATH}" ]; then
  if [ -e "${PROJECT_ROOT}/${JAR_PATH}" ]; then
    JAR_PATH="${PROJECT_ROOT}/${JAR_PATH}"
  fi
fi

if [ ! -f "${JAR_PATH}" ]; then
  echo "Fichier JAR introuvable: ${JAR_PATH}"
  exit 2
fi

GROUP_ID="${RAY_TRACER_GROUP_ID:-com.example.raytracer}"
ARTIFACT_ID="${RAY_TRACER_ARTIFACT_ID:-ray-tracer}"
VERSION="${RAY_TRACER_VERSION:-1.0.0}"
PACKAGING="${RAY_TRACER_PACKAGING:-jar}"

echo "Installation du JAR local: ${JAR_PATH}"
echo "Coordinates: ${GROUP_ID}:${ARTIFACT_ID}:${VERSION} (${PACKAGING})"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Erreur: Maven n'est pas installé ou 'mvn' n'est pas dans le PATH." >&2
  exit 3
fi

mvn --batch-mode install:install-file \
  -Dfile="${JAR_PATH}" \
  -DgroupId="${GROUP_ID}" \
  -DartifactId="${ARTIFACT_ID}" \
  -Dversion="${VERSION}" \
  -Dpackaging="${PACKAGING}" \
  -DgeneratePom=true

echo "OK: ${GROUP_ID}:${ARTIFACT_ID}:${VERSION} installé dans le dépôt local Maven."
