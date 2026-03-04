#!/bin/bash
# This script runs in the background to seed data once Solr is ready.

echo "Waiting for Solr to be ready..."
until curl -sf "http://localhost:8983/solr/admin/cores?action=STATUS" > /dev/null 2>&1; do
  sleep 1
done

echo "Waiting for imdb_movies core..."
until curl -sf "http://localhost:8983/solr/imdb_movies/admin/ping" > /dev/null 2>&1; do
  sleep 1
done

echo "Seeding data into imdb_movies core..."
curl -sf "http://localhost:8983/solr/imdb_movies/update?commit=true" \
  -H "Content-Type: application/json" \
  --data-binary @/opt/seed-data/imdb_movies_solr.json

echo "Data seeding complete."