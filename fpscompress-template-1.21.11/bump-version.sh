#!/bin/bash

# Version bump script for gradle.properties
# Usage: ./bump-version.sh [major|minor|patch|alpha|beta|release]

set -e

GRADLE_PROPS="gradle.properties"
BUMP_TYPE="${1:-patch}"

if [ ! -f "$GRADLE_PROPS" ]; then
    echo "Error: $GRADLE_PROPS not found!"
    exit 1
fi

# Extract current version
CURRENT_VERSION=$(grep "^mod_version=" "$GRADLE_PROPS" | cut -d'=' -f2)

if [ -z "$CURRENT_VERSION" ]; then
    echo "Error: Could not find mod_version in $GRADLE_PROPS"
    exit 1
fi

echo "Current version: $CURRENT_VERSION"

# Parse version components
# Format: MAJOR.MINOR.PATCH[-PRERELEASE]
if [[ $CURRENT_VERSION =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-([a-zA-Z]+))?$ ]]; then
    MAJOR="${BASH_REMATCH[1]}"
    MINOR="${BASH_REMATCH[2]}"
    PATCH="${BASH_REMATCH[3]}"
    PRERELEASE="${BASH_REMATCH[5]}"
else
    echo "Error: Invalid version format: $CURRENT_VERSION"
    echo "Expected format: MAJOR.MINOR.PATCH[-PRERELEASE]"
    exit 1
fi

# Calculate new version based on bump type
case "$BUMP_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        PRERELEASE=""
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        PRERELEASE=""
        ;;
    patch)
        PATCH=$((PATCH + 1))
        PRERELEASE=""
        ;;
    alpha)
        PRERELEASE="alpha"
        ;;
    beta)
        PRERELEASE="beta"
        ;;
    release)
        PRERELEASE=""
        ;;
    *)
        echo "Error: Invalid bump type: $BUMP_TYPE"
        echo "Usage: $0 [major|minor|patch|alpha|beta|release]"
        exit 1
        ;;
esac

# Construct new version
if [ -n "$PRERELEASE" ]; then
    NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}-${PRERELEASE}"
else
    NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
fi

echo "New version: $NEW_VERSION"

# Update gradle.properties
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^mod_version=.*/mod_version=${NEW_VERSION}/" "$GRADLE_PROPS"
else
    # Linux/Windows Git Bash
    sed -i "s/^mod_version=.*/mod_version=${NEW_VERSION}/" "$GRADLE_PROPS"
fi

echo "✓ Version bumped from $CURRENT_VERSION to $NEW_VERSION in $GRADLE_PROPS"

# Git operations
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "Warning: Not a git repository, skipping git commit and tag"
    exit 0
fi

# Check for uncommitted changes (other than gradle.properties)
if ! git diff --quiet --exit-code -- . ":(exclude)$GRADLE_PROPS"; then
    echo "Warning: You have uncommitted changes besides $GRADLE_PROPS"
    read -p "Continue with commit and tag? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Skipping git commit and tag"
        exit 0
    fi
fi

# Stage gradle.properties
git add "$GRADLE_PROPS"

# Create commit
COMMIT_MSG="Bump version to $NEW_VERSION"
git commit -m "$COMMIT_MSG"
echo "✓ Created commit: $COMMIT_MSG"

# Create tag
TAG_NAME="v$NEW_VERSION"
if git tag -l "$TAG_NAME" | grep -q "$TAG_NAME"; then
    echo "Warning: Tag $TAG_NAME already exists, skipping tag creation"
else
    git tag -a "$TAG_NAME" -m "Release $NEW_VERSION"
    echo "✓ Created tag: $TAG_NAME"
fi

echo ""
echo "Done! Don't forget to push:"
echo "  git push && git push --tags"
