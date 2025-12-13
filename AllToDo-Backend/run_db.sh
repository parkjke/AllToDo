#!/bin/bash
# Helper script to run database automatically handling Docker path issues

# 1. Try standard command
DOCKER_CMD="docker"

# 2. If not found, try absolute path on macOS
if ! command -v docker &> /dev/null; then
    if [ -f "/Applications/Docker.app/Contents/Resources/bin/docker" ]; then
        DOCKER_CMD="/Applications/Docker.app/Contents/Resources/bin/docker"
        echo "ℹ️  Docker not found in PATH. Using absolute path: $DOCKER_CMD"
    else
        echo "❌ Error: Docker not found. Please run Docker Desktop."
        exit 1
    fi
fi

# 3. Run docker compose
echo "🚀 Starting Database..."
"$DOCKER_CMD" compose up -d db

if [ $? -eq 0 ]; then
    echo "✅ Database is up and running!"
else
    echo "❌ Failed to start database."
fi
