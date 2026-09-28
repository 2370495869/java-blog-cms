#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec ./mvnw -B -ntp spring-boot:run "$@"
