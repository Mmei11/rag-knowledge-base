#!/usr/bin/env bash
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$BACKEND_DIR/.." && pwd)"

ENV_FILE_ARGS=()
if [[ -f "$REPO_DIR/.env" ]]; then
  ENV_FILE_ARGS=(--env-file "$REPO_DIR/.env")
fi

docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  "${ENV_FILE_ARGS[@]}" \
  -e DB_URL="${DB_URL:-jdbc:postgresql://host.docker.internal:5432/rag_kb}" \
  -e REDIS_HOST="${REDIS_HOST:-host.docker.internal}" \
  -e RETRIEVAL_SIMILARITY_THRESHOLD="${RETRIEVAL_SIMILARITY_THRESHOLD:-0.35}" \
  -e RERANK_PROVIDER="${RERANK_PROVIDER:-none}" \
  -e RERANK_MODEL="${RERANK_MODEL:-qwen3-rerank}" \
  -e RERANK_CANDIDATE_LIMIT="${RERANK_CANDIDATE_LIMIT:-30}" \
  -e RERANK_TIMEOUT_MS="${RERANK_TIMEOUT_MS:-3000}" \
  -e RERANK_MAX_DOCUMENT_CHARS="${RERANK_MAX_DOCUMENT_CHARS:-2000}" \
  -e RERANK_API_KEY="${RERANK_API_KEY:-}" \
  -v "$BACKEND_DIR":/workspace \
  -v ragkb-maven-repo:/root/.m2 \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn -s /workspace/.mvn/settings.xml \
  -Dtest=RetrievalEvaluationTest \
  -Dretrieval.eval.enabled=true \
  "$@" \
  test
