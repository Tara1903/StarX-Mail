#!/bin/bash
set -e

echo "========================================================"
echo "  Building StarX Mail Android APK"
echo "========================================================"

cd "$(dirname "$0")/../android"

chmod +x gradlew

echo "Running unit tests..."
./gradlew test

echo "Assembling Debug APK..."
./gradlew assembleDebug

echo ""
echo "========================================================"
echo "  Android build completed successfully!"
echo "  APK location: android/app/build/outputs/apk/debug/app-debug.apk"
echo "========================================================"
