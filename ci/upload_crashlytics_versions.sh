#!/bin/bash
# fail if any commands fails
set -e

# Buildkite uses a clean state for each step (for concurrency)
source ./ci/prepare_env_buildkite.sh

# Buildkite branch equals to tag name if build was triggered by tag
if [[ $BUILKITE_BRANCH  =~ ^v[0-9]+.* ]]; then
    export APP_VERSION_NAME=${BUILDKITE_BRANCH:1}
fi

echo "INFURA_API_KEY=$INFURA_API_KEY" > project_keys

./gradlew assembleInternal assembleRinkeby assembleRelease
./gradlew crashlyticsUploadDistributionInternal crashlyticsUploadDistributionRinkeby crashlyticsUploadDistributionRelease
