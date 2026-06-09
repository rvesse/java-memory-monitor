#!/usr/bin/env bash

NAMESPACE=$1
POD=$2
CONTAINER=$3
DUMP_LOCATION=${4:-/tmp/dumps}
DUMP_INTERVAL=${5:-180}
DEBUG_CONTAINER=${6:-jvm-memory-monitor}
DEBUG_CONTAINER_IMAGE=${7:-rvesse/jvm-memory-monitor:latest}
shift 7

function blankLine() {
    echo ""
}

function title() {
  local TITLE=$1
  blankLine
  echo "${TITLE}"
  printf "%${#TITLE}s" | sed 's/ /-/g'
  blankLine
}

title "JVM Memory Monitor for Kubernetes"

if [ -z "${NAMESPACE}" ]; then
  echo "MUST specify a Kubernetes Namespace as the first argument"
  exit 1
fi

if [ -z "${POD}" ]; then
  echo "MUST specify a Kubernetes Pod Name as the second argument"
  exit 2
fi

if [ -z "${CONTAINER}" ]; then
  echo "MUST specify a Container Name as the third argument"
  exit 3
fi

mkdir -p ${DUMP_LOCATION}
if [ ! -d "${DUMP_LOCATION}" ]; then
  echo "DUMP_LOCATION ${DUMP_LOCATION} is not a directory"
  exit 4
fi

echo "Monitoring JVM memory for container ${CONTAINER} in pod ${POD} in namespace ${NAMESPACE} every ${DUMP_INTERVAL} seconds to ${DUMP_LOCATION}"
echo ""

function onInterrupt() {
    echo "Stopping monitoring JVM memory..."
    exit 0
}

# Detect whether the relevant debug container is already attached to the target pod, if not we will need to use kubectl 
# debug to attach one before we can start dumping the heap
kubectl get pod -n ${NAMESPACE} ${POD} \
        -o jsonpath="{.spec.ephemeralContainers[*].name}" | grep "${DEBUG_CONTAINER}" > /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "Debug container ${DEBUG_CONTAINER} not found in pod ${POD}, attaching it now..."
  kubectl debug -n ${NAMESPACE} --image=${DEBUG_CONTAINER_IMAGE} \
          --image-pull-policy Always \
          --container=${DEBUG_CONTAINER} --target=${CONTAINER} ${POD} \
          --arguments-only -- --dump-interval ${DUMP_INTERVAL} "$@"
  echo "Waiting to allow debug container to start running..."
  sleep 60
  echo "Debug container ${DEBUG_CONTAINER} should now be running in pod ${POD}, starting to retrieve heap dumps..."
else
  echo "Debug container ${DEBUG_CONTAINER} already attached to pod ${POD}, continuing to retrieve heap dumps..."
fi

trap onInterrupt SIGINT SIGTERM
set -o pipefail

while IFS=$'\n' read -r LINE; do
  # Look for log lines tagged with the [READY] flag as these indicate produced dump files that the monitor debug
  # container has produced that we can retrieve from the pod being debugged
  TAG=$(echo "${LINE}" | awk '{print $1}')
  if [ "${TAG}" != "[READY]" ]; then
    continue
  fi

  # Extract the dump file path from the log line
  DUMP_FILE=$(echo "${LINE}" | awk '{print $2}')
  title "${DUMP_FILE}"
  echo "Dump file ${DUMP_FILE} is ready, seeing whether we have it on the local machine already..."

  # Check whether we already have a copy of the dump file locally, if so we can skip copying it again.
  # This is mainly needed if this script is stopped and then re-run against a pod where we've previously retrieved some
  # dumps and avoids copying them again
  if [ "${POD}" != "${CONTAINER}" ]; then
    LOCAL_DUMP_FILE="${DUMP_LOCATION}/${POD}_${CONTAINER}_$(basename ${DUMP_FILE})"
  else
    LOCAL_DUMP_FILE="${DUMP_LOCATION}/${POD}_$(basename ${DUMP_FILE})"
  fi
  if [ -f "${LOCAL_DUMP_FILE}" ]; then
    echo "Dump file ${DUMP_FILE} already exists locally as ${LOCAL_DUMP_FILE}, skipping copy"
    ls -lh ${LOCAL_DUMP_FILE}
    continue
  fi

  DUMP_EXT=${DUMP_FILE##*.}
  COPY_FILE=${DUMP_FILE}
  LOCAL_COPY_FILE=${LOCAL_DUMP_FILE}
  COPY_CONTAINER=${CONTAINER}
  if [ "${DUMP_EXT}" == "hprof" ]; then
    # Compress the heap dump to save space and speed up copying
    echo "Compressing heap dump ${DUMP_FILE} in pod ${POD}..."
    kubectl exec -n ${NAMESPACE} -c ${CONTAINER} ${POD} \
            -- /bin/sh -c "gzip ${DUMP_FILE} && ls -lh ${DUMP_FILE}.gz"
    if [ $? -ne 0 ]; then
      echo "Failed to compress heap dump ${DUMP_FILE} in pod ${POD}"
      continue
    fi
    blankLine
    COPY_FILE="${COPY_FILE}.gz"
    LOCAL_COPY_FILE=${LOCAL_COPY_FILE}.gz
  else
    COPY_CONTAINER=${DEBUG_CONTAINER}
  fi

  # Copy the dump to the local machine
  echo "Copying dump ${COPY_FILE} to local machine..."
  kubectl cp ${NAMESPACE}/${POD}:${COPY_FILE} ${LOCAL_COPY_FILE} -c ${COPY_CONTAINER}
  if [ ! -f "${LOCAL_COPY_FILE}" ]; then
    echo "Failed to copy dump ${COPY_FILE} from pod ${POD}"
    continue
  else
    echo "Successfully copied dump ${COPY_FILE} to ${LOCAL_COPY_FILE}"
    ls -lh ${LOCAL_COPY_FILE}
  fi
  blankLine

  # Uncompress the dump locally if appropriate
  if [ "${LOCAL_COPY_FILE}" != "${LOCAL_DUMP_FILE}" ]; then
    echo "Uncompressing dump ${LOCAL_COPY_FILE} locally..."
    gunzip ${LOCAL_COPY_FILE}
    if [ $? -ne 0 ]; then
      echo "Failed to uncompress dump ${LOCAL_COPY_FILE} locally"
      rm -Rf "${LOCAL_COPY_FILE}"
      continue
    else
      echo "Successfully uncompressed heap dump ${LOCAL_COPY_FILE} to ${LOCAL_DUMP_FILE}"
      ls -lh ${LOCAL_DUMP_FILE}
    fi
    blankLine
  fi

  # Remove the dump file from the pod to save space now we've copied it locally
  # Note that we can't exec into the debug container itself to remove the native memory dumps
  # that live in its filesystem, however the memory monitor script automatically cleanups these
  # files periodically, and these are only a couple of kilobytes so unlikely to cause problems
  # unline heap dumps that can be 100s of megabytes!
  if [ "${CONTAINER}" == "${COPY_CONTAINER}" ]; then
    echo "Removing dump file ${COPY_FILE} from pod ${POD} to save space..."
    kubectl exec -n ${NAMESPACE} -c ${CONTAINER} ${POD} -- rm -Rf ${COPY_FILE}
    if [ $? -ne 0 ]; then
      echo "Failed to remove dump file ${COPY_FILE} from pod ${POD}, you may want to check the pod and remove it manually to save space"
    else 
      echo "Successfully removed dump file ${COPY_FILE} from pod ${POD}"
    fi
  fi

done < <(kubectl logs -n ${NAMESPACE} ${POD} -c ${DEBUG_CONTAINER} -f)

echo "No further dump files available, the debug container may be stopped/failed"

# Check the status of the debug container
STATUS=$(kubectl get pod -n ${NAMESPACE} ${POD} \
                 -o jsonpath="{.status.ephemeralContainerStatuses[?(@.name=='${DEBUG_CONTAINER}')].state.terminated}" 2>&1)
if [ -n "${STATUS}" ]; then
  echo "Debug container ${DEBUG_CONTAINER} has exited:"
  echo "${STATUS}" | jq
  blankLine
  echo "If you want to restart memory monitoring please relaunch this script and supply a new debug container name"
  exit 0
fi

exit 0