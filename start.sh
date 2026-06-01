#!/usr/bin/env bash
set -euo pipefail

PORT=8080
if lsof -ti:"$PORT" &>/dev/null; then
  echo "Port $PORT in use — killing existing process..."
  lsof -ti:"$PORT" | xargs kill -9
  sleep 1
fi

./mvnw spring-boot:run
