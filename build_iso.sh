#!/bin/bash
set -e

# Ensure we are in the project root
cd "$(dirname "$0")"

echo "Starting BD-UN-JB Docker Builder..."

# Build the docker image if needed
docker compose build builder

# Run the build process
docker compose run --rm --remove-orphans builder

echo ""
echo "Done! If successful, BD-UN-JB.iso should be in the current directory."