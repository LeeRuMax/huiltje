#!/bin/sh
# ./run.sh convert|features|train  (Linux met Docker)
# DATA = uitgepakte InfantCry-DBL v2 (map met dunstan337/, collected1214/, metadata.csv)
# MODELS = map met de YAMNet SavedModel in yamnet/
set -e
cd "$(dirname "$0")"
: "${DATA:?zet DATA=/pad/naar/InfantCry-DBL}" "${MODELS:?zet MODELS=/pad/naar/models}"
docker build -q -t huiltje-train . >/dev/null
docker run --rm --memory 2500m --cpus 3 -v "$PWD":/work -v "$DATA":/data:ro -v "$MODELS":/models:ro huiltje-train python train.py "$@"
