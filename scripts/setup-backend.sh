#!/bin/bash
set -e

echo "========================================================"
echo "  Setting up StarX Mail Backend"
echo "========================================================"

cd "$(dirname "$0")/../backend"

if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
fi

echo "Installing dependencies..."
npm install

echo "Building TypeScript project..."
npm run build

echo ""
echo "========================================================"
echo "  Backend setup complete!"
echo "  Run 'npm start' or 'npm run dev' inside backend/"
echo "========================================================"
