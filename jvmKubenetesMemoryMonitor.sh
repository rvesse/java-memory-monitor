#!/usr/bin/env bash

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

# Command Line Argument Handling 
function showHelp() {
  title "jvmKubernetesMemoryMonitor.sh Usage"
  blankLine
  cat <<EOF
This script launches a Kubernetes ephemeral debug container into an existing 
Kubernetes (K8S) pod in your K8S cluster.  By default this is a container
packaging the jvmMemoryMonitor.sh script in this repository whose help describes
it's purpose and arguments.

Once the debug container is launched, or if it already exists, this script monitors
it's log output for messages indicating a new memory dump is available.  Those memory
dumps are then copied out of the K8S containers to your local machine for inspection
at your leisure.  You can customise the arguments passed to the debug container as
described later in USAGE EXAMPLES.

The script is idempotent in that if run with the same arguments against a pod that has
previously had the debug container attached it will skip transferring memory dumps it
already has locally and just resume from the next available dump file.

OPTIONS

  -c <container>
  --contaner <container>

    Specifies the container within the pod to which you wish to attach the debug 
    container.  This is the container whose process namespace will be shared with
    the debug container and thus dictates which Java processes (if any) are 
    visible to the debug container.

  --debug-container <name>

    Specifies a name for the debug container. As K8S debug container names must be
    unique within a pod if the container terminates for any reason you will need
    to re-run this script with a different name provided, jvm-memory-monitor is the
    default debug container name used.

  --debug-container-image <image>

    Specifies a custom image for the debug container.  The default image is 
    rvesse/jvm-memory-monitor:latest but may be customised if you want to use
    a custom image.  If using a custom image other aspects of this script, e.g.
    automated retrieval of memory dumps to the local machine, may not function.

  --help

    Prints this help message and exits.

  -l <directory>
  --dump-location <directory>

    Specifies the directory on your local machine to which dump files should be 
    transferred, by default this is the /tmp/dumps directory.

  -n <namespace>
  --namespace <namespace>

    Specifies the K8S namespace where the pod you wish to attach the debug 
    container to is running.

  -p <pod>
  --pod <pod>

    Specifies the K8S pod you wish to attach the debug container to.

  --

    Separates options to this script from options you wish to pass into the debug
    container.  Any options seen after this are not interpreted as options to this
    script but instead passed as arguments to the debug container, see USAGE
    EXAMPLES below for some examples of this.

USAGE EXAMPLES

  Monitoring memory using default options:

    ./jvmKubernetesMemoryMonitor.sh --namespace my-namespace --pod my-pod \\
      --container my-container

  Monitoring native memory only with custom dump interval:

    ./jvmKubernetesMemoryMonitor.sh --namespace my-namespace --pod my-pod \\
      --container my-container -- --dump-interval 60 --no-heap-dumps

  Monitoring native memory detail with diffs:

      ./jvmKubernetesMemoryMonitor.sh --namespace my-namespace --pod my-pod \\
      --container my-container -- --no-heap-dumps --baseline --detail
EOF
}

# NB - This requires proper GNU getopt with --long argument support, on some OSes e.g. Darwin, they have the original
# getopt without this support in which case this will fail and not set any arguments and then we'll bail out in our
# argument checks after the argument processing loop
TEMP=$(getopt -o c:l:n:p: \
              --long container:,debug-container:,debug-container-image:,dump-location:,namespace:,pod:,help \
              -n 'jvmKubernetesMemoryMonitor.sh' -- "$@")

if [ $? != 0 ] ; then 
  echo "Bad Arguments encountered" >&2
  exit 1
fi
eval set -- "$TEMP"

NAMESPACE=default
POD=
CONTAINER=
DUMP_LOCATION=/tmp/dumps
DEBUG_CONTAINER=jvm-memory-monitor
DEBUG_CONTAINER_IMAGE=rvesse/jvm-memory-monitor:latest

while [ true ]; do
  case "$1" in
    -c | --container )
      CONTAINER=$2
      shift 2
      ;;
    --help )
      showHelp
      exit 0
      ;;
    -l | --dump-location )
      DUMP_LOCATION=$2
      shift 2
      ;;
    -n | --namespace )
      NAMESPACE=$2
      shift 2
      ;;
    -p | --pod )
      POD=$2
      shift 2
      ;;
    --debug-container )
      DEBUG_CONTAINER=$2
      shift 2
      ;;
    --debug-container-image )
      DEBUG_CONTAINER_IMAGE=$2
      shift 2
      ;;
    -- )
      shift
      break
      ;;
    * ) 
      echo "Unexpected option $1, ignored" >&2
      break 
      ;;
  esac
done

title "JVM Memory Monitor for Kubernetes"

if [ -z "${POD}" ]; then
  echo "MUST specify a Kubernetes Pod Name via the -p/--pod option"
  exit 2
elif ! kubectl get pod -n "${NAMESPACE}" "${POD}" >/dev/null 2>&1; then
  echo "Kubenetes Pod ${POD} does not exist in namespace ${NAMESPACE}"
  exit 2
fi

if [ -z "${CONTAINER}" ]; then
  echo "MUST specify a Container Name via the -c/--container option"
  exit 3
fi

mkdir -p ${DUMP_LOCATION}
if [ ! -d "${DUMP_LOCATION}" ]; then
  echo "DUMP_LOCATION ${DUMP_LOCATION} is not a directory or could not be created"
  exit 4
fi

echo "Monitoring JVM memory for container ${CONTAINER} in pod ${POD} in namespace ${NAMESPACE} every ${DUMP_INTERVAL} seconds to ${DUMP_LOCATION}"
echo "Using debug container ${DEBUG_CONTAINER} with image ${DEBUG_CONTAINER_IMAGE}"
echo ""

function onInterrupt() {
    echo "Stopping monitoring JVM memory..."
    exit 0
}

function echorun() {
  echo "$@"
  "$@"
}

function findDebugContainerStatus() {
  local DESIRED_STATUS=${1:-terminated}
  kubectl get pod -n ${NAMESPACE} ${POD} \
                 -o jsonpath="{.status.ephemeralContainerStatuses[?(@.name=='${DEBUG_CONTAINER}')].state.${DESIRED_STATUS}}" 2>&1
}

# Detect whether the relevant debug container is already attached to the target pod, if not we will need to use kubectl 
# debug to attach one before we can start dumping the heap
kubectl get pod -n ${NAMESPACE} ${POD} \
        -o jsonpath="{.spec.ephemeralContainers[*].name}" | grep "${DEBUG_CONTAINER}" > /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "Debug container ${DEBUG_CONTAINER} not found in pod ${POD}, attaching it now..."
  echorun kubectl debug -n ${NAMESPACE} --image=${DEBUG_CONTAINER_IMAGE} \
          --image-pull-policy Always \
          --container=${DEBUG_CONTAINER} --target=${CONTAINER} ${POD} \
          --arguments-only -- "$@"
  echo "Waiting to allow debug container to start running..."
  START=${SECONDS}
  while [ true ]; do
    sleep 5
    STATUS=$(findDebugContainerStatus "running")
    if [ -n "${STATUS}" ]; then
      echo "Debug container now running after $(( ${SECONDS} - ${START})) seconds!"
      break
    fi
    STATUS=$(findDebugContainerStatus)
    if [ -n "${STATUS}" ]; then
      echo "Debug container terminated unexpectedly, see logs for details:"
      blankLine
      echo "${STATUS}"
      blankLine
      kubectl logs -n ${NAMESPACE} -c ${DEBUG_CONTAINER} ${POD}
      blankLine
      exit 1
    fi

    ELAPSED=$(( ${SECONDS} - ${START}))
    if [ ${ELAPSED} -gt 60 ]; then
      echo "Debug container failed to reach running state within 60 seconds!"
      exit 1
    fi
  
  done
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
STATUS=$()
if [ -n "${STATUS}" ]; then
  echo "Debug container ${DEBUG_CONTAINER} has exited:"
  echo "${STATUS}" | jq
  blankLine
  echo "If you want to restart memory monitoring please relaunch this script and supply a new debug container name"
  exit 0
fi

exit 0