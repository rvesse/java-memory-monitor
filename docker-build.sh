#!/usr/bin/env bash
#
# Copyright (C) 2026 Telicent Limited
#

function error() {
  echo "$@" 1>&2
}

function abort() {
  echo "$@" 1>&2
  exit 255
}

function echorun() {
  echo "$@"
  "$@"
}

command -v docker >/dev/null 2>&1 || abort "This script requires the docker command on your PATH"

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
SCRIPT_DIR=$(cd "${SCRIPT_DIR}" && pwd)

DOCKER_REPO=$1
DOCKER_TAG=${2:-latest}
TARGET_PLATFORMS=${TARGET_PLATFORMS:-linux/amd64,linux/arm64}
echo "Docker Tag is ${DOCKER_TAG}"
if [ -n "${DOCKER_REPO}" ]; then
  echo "Docker Registry is ${DOCKER_REPO}, images will be pushed to this repository"
else
  echo "No Docker Registry defined, images will be built locally only"
fi

export DOCKER_BUILDKIT=1

function buildImage() {
  local IMAGE_NAME="$1"
  if [ -n "${DOCKER_REPO}" ]; then
    IMAGE_NAME="${DOCKER_REPO}/${IMAGE_NAME}"
  fi
  shift 1
  local DOCKERFILE=$1
  shift 1

  local DOCKER_ARGS=(
    "docker"
  )
  # If TARGET_PLATFORMS is set add the --platform flag so we get a multi-platform image build.  This of couse assumes
  # that docker buildx is available as plain docker will not support this
  if [ -n "${TARGET_PLATFORMS}" ]; then
    DOCKER_ARGS+=(
      "buildx"
      "build"
      "--platform"
      "${TARGET_PLATFORMS}"
    )
    # If a Docker repository is specified add the --push argument so the resulting multi-platform manifest and all the
    # image layers get pushed accordingly
    if [ -n "${DOCKER_REPO}" ]; then
      DOCKER_ARGS+=("--push")
    fi
  else
    DOCKER_ARGS+=("build")
  fi

  # Set the Tag and Dockerfile plus inject the PROJECT_VERSION as a build argument
  DOCKER_ARGS+=(
    "-t"
    "${IMAGE_NAME}:${DOCKER_TAG}"
    "-f"
    "${SCRIPT_DIR}/${DOCKERFILE}"
    "--no-cache"
  )

  if [ $# -gt 0 ]; then
    DOCKER_ARGS+=("$@")
  fi
  if [ -n "${EXTRA_BUILD_ARGS}" ]; then
    DOCKER_ARGS+=( ${EXTRA_BUILD_ARGS} )
  fi
  DOCKER_ARGS+=(
    "."
  )

  echo "Building Docker Image ${IMAGE_NAME}:${DOCKER_TAG}..."
  # shellcheck disable=SC2015
  echorun "${DOCKER_ARGS[@]}" || abort "Docker Build failed"
}

function pushImage() {
  local IMAGE_NAME=$1
  if [ -n "${DOCKER_REPO}" ]; then
    IMAGE_NAME="${DOCKER_REPO}/${IMAGE_NAME}"
    echo "Pushing image ${IMAGE_NAME}:${DOCKER_TAG}..."
    echorun docker push "${IMAGE_NAME}:${DOCKER_TAG}" || abort "Docker push failed"
    if [ "${DOCKER_TAG}" != "latest" ] && [ "${BRANCH}" == "main" ]; then
      echorun docker tag "${IMAGE_NAME}:${DOCKER_TAG}" "${IMAGE_NAME}:latest" || abort "Docker tag failed"
      echorun docker push "${IMAGE_NAME}:latest" || abort "Docker push failed"
    fi
    echo ""
  fi
}

function buildAndPushImage() {
  buildImage "$@"
  if [ -z "${TARGET_PLATFORMS}" ]; then
    # NB - When TARGET_PLATFORMS is set and we're doing a multi-platform build we add the --push argument to the docker
    #      build command instead which automatically pushes the image manifest and layers to the Docker repository
    # Therefore only need an explicit push when doing a single platform build i.e. local developer build
    pushImage "$@"
  fi
}

buildAndPushImage "java-memory-monitor" "Dockerfile"
buildAndPushImage "java-loiter" "loiter/src/Dockerfile"
