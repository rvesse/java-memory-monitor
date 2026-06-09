#!/usr/bin/env bash

# Helper Functions
function blankLine() {
    echo ""
}

function title() {
  local TITLE=$1
  local TITLE_CHAR=${2:-"-"}
  blankLine
  echo "${TITLE}"
  printf "%${#TITLE}s" | sed "s/ /${TITLE_CHAR}/g"
  blankLine
}

title "JVM Memory Monitor" "="

# Command Line Argument Handling
function showHelp() {
  title "jvmMemoryMonitor.sh Usage"
  blankLine
  cat <<EOF
This script periodically takes dumps of heap and/or Java native memory tracking information 
from a Java Process.  It is designed to be run as a debug container that is sharing the process
namespace of the JVM application whose memory usage you wish to monitor.

By default the JVM process to monitor is auto-detected by finding the first JVM process reported
by the jps command (other than jps itself!)

OPTIONS

  -b
  --baseline

    Enables baseline for native memory tracking.

    When set the native memory dumps will include +/- markers indicating how memory usage 
    changed over time.

  -c
  --continue-on-jvm-failure
  --no-continue-on-jvm-failure

    Sets that if the monitored JVM process fails should the monitor re-detect the primary JVM
    process.

    Enabled by default, disabled by the --no-continue-on-jvm-failure option.  When disabled
    the script exits once the monitored JVM is no longer alive.

  --cleanup-interval <seconds>

    Sets the cleanup interval in seconds after which any native memory dump files are 
    automatically removed.

    Defaults to 900 seconds (15 minutes).

  -d
  --detail
  -s
  --summary
  --no-native-memory

    Sets the native memory tracking mode to detail or summary.  In order for native memory
    to be tracked the Java process MUST have been started with the corresponding JVM option
    i.e. -XX:NativeMemoryTracking=summary or -XX:NativeMemoryTracking=detail as appropriate.

    Enabled in summary mode by default, disabled by the --no-native-memory option.

  -h
  --heap-dumps
  --no-heap-dumps

    Sets that the heap of the monitored JVM should be periodically dumped.

    Enabled by default, disabled by the --no-heap-dumps option.

  --help

    Prints this help message and exits.

  -i <seconds>
  --dump-interval <seconds>

    Specifies the interval of taking heap and/or native memory dumps from the JVM process.

    Defaults to 180 seconds (3 minutes)

  -j <pid>
  --java-pid <pid>

    Specifies the PID of the Java process to monitor, this skips the auto-detection of the
    JVM process to monitor and only monitors the given Java PID.  If the given PID is not
    a valid Java process then the script will exit immediately.

    This option also enforces the --no-continue-on-jvm-failure option regardless of options
    specified by the user.

  -l <seconds>
  --limit <seconds>

    Specifies the maximum amount of time in seconds that the JVM process will be monitored
    for.  Once this limit is reached then the script will exit.

  --scale KB|MB|GB

    Specifies the scale to track native memory in, one of KB, MB or GB, defaults to MB.

USAGE EXAMPLES

  Track native memory every 5 minutes with baseline:

    jvmMemoryMonitor.sh --baseline --no-heap-dumps --summary --dump-interval 300

  Track heap dump every 10 minutes:

    jvmMemoryMonitor.sh --no-native-memory --heap-dumps --dump-interval 600

  Track native memory for a specific Java process:

    jvmMemoryMonitor.sh --no-heap-dumps --summary --java-pid 1234

EOF
}

TEMP=$(getopt -o bcdhi:j:l:s \
              --long baseline,continue-on-jvm-failure,detail,heap-dumps,dump-interval:,java-pid:,limit:,summary,no-continue-on-jvm-failure,no-heap-dumps,no-native-memory,cleanup-interval:,scale:,help \
              -n 'jvmHeapMonitor.sh' -- "$@")

if [ $? != 0 ] ; then 
  echo "Bad Arguments encountered" >&2
  exit 1
fi
eval set -- "$TEMP"

BASELINE=
CONTINUE_ON_JVM_FAILURE=true
CLEANUP_INTERVAL=900
DUMP_INTERVAL=180
JAVA_PID=
LIMIT=-1
HEAP_DUMPS=true
NATIVE_MEMORY=summary
SCALE=MB

while [ true ]; do
  case "$1" in
    -b | --baseline ) 
      BASELINE=true;
      shift 
      ;;
    -c | --continue-on-jvm-failure ) 
      CONTINUE_ON_JVM_FAILURE=true
      shift 
      ;;
    --cleanup-interval )
      CLEANUP_INTERVAL=$2
      if [ "${CLEANUP_INTERVAL}" -le 0 ]; then
        echo "Cleanup interval must be a positive integer representing the number of seconds to wait between cleaning up dumps" >&2
        exit 1
      fi
      shift
      ;;
    -d | --detail ) 
      NATIVE_MEMORY=detail
      shift 
      ;;
    -h | --heap-dumps ) 
      HEAP_DUMPS=true
      shift 
      ;;
    -i | --dump-interval ) 
      DUMP_INTERVAL="$2"
      if [ "${DUMP_INTERVAL}" -le 0 ]; then
        echo "Dump interval must be a positive integer representing the number of seconds to wait between dumps" >&2
        exit 1
      fi
      shift 2
      ;;
    -j | --java-pid )
      JAVA_PID="$2"
      shift 2
      jps | grep "${JAVA_PID}" > /dev/null 2>&1
      if [ $? -ne 0 ]; then
        echo "Specified Java PID ${JAVA_PID} is not a valid Java Process"
        exit 1
      fi
      ;;
    -l | --limit )
      LIMIT="$2"
      if [ $LIMIT -le 0 ]; then
        echo "Limit must be a positive integer representing the maximum number of seconds to run for before exiting" >&2
        exit 1
      fi
      shift 2
      ;;
    -s | --summary ) 
      NATIVE_MEMORY=summary
      shift 
      ;;
    --scale)
      SCALE=$2
      case "${SCALE}" in
        KB|MB|GB) ;;
        *)
          echo "Scale must be one of KB, MB or GB, invalid scale ${SCALE} provided"
          exit 1
      esac
      shift 2
      ;;
    --no-continue-on-jvm-failure ) 
      CONTINUE_ON_JVM_FAILURE=
      shift 
      ;;
    --no-heap-dumps ) 
      HEAP_DUMPS=
      shift 
      ;;
    --no-native-memory )
      NATIVE_MEMORY=
      shift
      ;;
    --help )
      showHelp
      exit 0
      ;;
    -- )
      break
      ;;
    * ) 
      echo "Unexpected option $1, ignored" >&2
      break 
      ;;
  esac
done

# Must have either heap dumps or native memory tracking enabled, otherwise there is no point in running the monitor
if [ -z "${HEAP_DUMPS}" ] && [ -z "${NATIVE_MEMORY}" ]; then
  echo "At least one of heap dumps or native memory dumps MUST be enabled" >&2
  exit 1
fi

function enabledOrDisabled() {
  local CAPTION=$1
  local ENABLED=$2
  echo -n "${CAPTION} "
  if [ -n "${ENABLED}" ]; then
    echo "Enabled"
  else
    echo "Disabled"
  fi
}

title "Configuration"
echo -n "Java PID? "
if [ -n "${JAVA_PID}" ]; then
  # If a specific Java PID is specified then --no-continue-on-jvm-failure is implied
  echo "${JAVA_PID}"
  CONTINUE_ON_JVM_FAILURE=
else
  echo "Auto-detected"
fi
enabledOrDisabled "  Continue on JVM Failure:" ${CONTINUE_ON_JVM_FAILURE}
enabledOrDisabled "Heap Dumps:" ${HEAP_DUMPS}
enabledOrDisabled "Native Memory Tracking:" ${NATIVE_MEMORY}
if [ -n "${NATIVE_MEMORY}" ]; then
  enabledOrDisabled "  Baseline:" ${BASELINE}
  echo "  Tracking Mode: ${NATIVE_MEMORY}"
fi
echo "Dump Interval: ${DUMP_INTERVAL} seconds"
echo "  Cleanup Interval: ${CLEANUP_INTERVAL} seconds"
if [ "${LIMIT}" -gt 0 ]; then
  echo "  Limit: ${LIMIT} seconds"
else
  echo "  Limit: No limit"
fi

# Detect the Java Process ID
function detectJavaProcess() {
    local EXIT_ON_ERROR=${1:-true}
    title "Java Processes"
    jps -l
    blankLine
    echo "Detecting primary Java process to monitor..."
    JAVA_PID=$(jps | grep -v Jps | awk '{print $1}' | head -n 1)
    if [ -z "${JAVA_PID}" ]; then
        if [ "${EXIT_ON_ERROR}" == "true" ]; then
          echo "ERROR: No Java process found, exiting"
          exit 1
        else
          echo "WARN: No Java process found, will keep trying to detect it until we find one to monitor"
          return
        fi
    fi
    echo "Monitoring memory for Java process with PID ${JAVA_PID} and dumping every ${DUMP_INTERVAL} seconds"
    title "Java Command Line:"
    jcmd ${JAVA_PID} VM.command_line
}
if [ -z "${JAVA_PID}" ]; then
  detectJavaProcess
fi

function onInterrupt() {
    echo "Stopping monitoring JVM memory..."
    exit 0
}

function sleepBeforeNextDump() {
    if [ ${ELAPSED} -lt 0 ]; then
      ELAPSED=$(( ${SECONDS} - ${START} ))
    fi
    SLEEP_TIME=$(( ${DUMP_INTERVAL} - ${ELAPSED} ))
    if [ "${SLEEP_TIME}" -lt 0 ]; then
      echo "No need to wait before the next dump, starting immediately..."
    else
      echo "Waiting for ${SLEEP_TIME} seconds before the next dump..."
      sleep ${SLEEP_TIME}
    fi
}

trap onInterrupt SIGINT SIGTERM

SCRIPT_START=${SECONDS}
LAST_CLEANUP=${SECONDS}
while true; do
  START=${SECONDS}
  ELAPSED=-1

  # Check we haven't reached our time limit
  if [ "${LIMIT}" -gt 0 ]; then
    TOTAL_ELAPSED=$(( ${SECONDS} - ${SCRIPT_START}))
    if [ "${TOTAL_ELAPSED}" -ge "${LIMIT}" ]; then
      echo "Monitoring hit limit of ${LIMIT} seconds, exiting..." >&2
      exit 1
    fi
  fi

  # Check whether we need to trigger a cleanup
  CLEANUP_ELAPSED=$(( ${SECONDS} - ${LAST_CLEANUP}))
  if [ "${CLEANUP_ELAPSED}" -ge "${CLEANUP_INTERVAL}" ]; then
    title "Dump Cleanup"
    echo "Cleaning up native memory dumps..."
    rm -Rf /tmp/native-memory*
    echo "Cleanup completed"
    LAST_CLEANUP=${SECONDS}
  fi

  # If our monitored Java process has died we may need to re-detect it now
  if [ -z "${JAVA_PID}" ]; then
    if [ -z "${CONTINUE_ON_JVM_FAILURE}" ]; then
      echo "Monitored Java process is no longer alive and continue on JVM failure is disabled, exiting..." >&2
      exit 1
    fi

    echo "No Java process currently detected to monitor, attempting to detect it again..."
    detectJavaProcess false
    if [ -z "${JAVA_PID}" ]; then
      echo "Still no Java process detected, waiting for ${DUMP_INTERVAL} seconds before trying again..."
      sleepBeforeNextDump
      continue
    fi

    # If we redetected the Java PID and we were taking native memory diffs we need to re-establish a new baseline
    if [[ "${NATIVE_MEMORY}" = *.diff ]]; then
      BASELINE=true
    fi
  fi

  # Determine the name for the next dumps
  NAME="$(date +%Y%m%d_%H%M%S)"
  DUMP_NAME="heap-dump-${NAME}.hprof"
  NATIVE_DUMP_NAME="native-memory-${NAME}.txt"

  title "Dump ${NAME}"

  # Take a Heap Dump if eanbled
  if [ -n "${HEAP_DUMPS}" ]; then
    echo "Dumping JVM heap to ${DUMP_NAME}..."
    jmap -dump:file=/tmp/${DUMP_NAME} ${JAVA_PID}
    if [ $? -ne 0 ]; then
      echo "Failed to dump JVM heap, checking whether the monitored Java process is still alive..."
      jps | grep "${JAVA_PID}" > /dev/null 2>&1
      if [ $? -ne 0 ]; then
          echo "Monitored Java process with PID ${JAVA_PID} is no longer alive, will re-detect the Java process to monitor for the next dump..."
          JAVA_PID=""
          sleepBeforeNextDump
          continue
      fi
    else
      echo "[READY] /tmp/${DUMP_NAME}"
    fi
  fi

  # Track native memory if enabled
  if [ -n "${NATIVE_MEMORY}" ]; then
    echo "Dumping JVM Native Memory to ${NATIVE_DUMP_NAME}..."

    # Establish the baseline if request
    if [ -n "${BASELINE}" ]; then
      # Reset the variable to blank so we ONLY create a baseline once
      BASELINE=
      echo "Establishing a native memory baseline for Java PID ${JAVA_PID}"
      jcmd ${JAVA_PID} VM.native_memory baseline scale=${SCALE}
      # If in baseline mode we want future native memory dumps to be diff's against this baseline
      if [[ "${NATIVE_MEMORY}" != *.diff ]]; then
        NATIVE_MEMORY=${NATIVE_MEMORY}.diff
      fi
    else
      # Capture current native memory usage, if 
      jcmd ${JAVA_PID} VM.native_memory ${NATIVE_MEMORY} scale=${SCALE} > /tmp/${NATIVE_DUMP_NAME}
      if [ $? -ne 0 ]; then
        echo "Failed to track native memory, checking whether the monitored Java process is still alive..."
        jps | grep "${JAVA_PID}" > /dev/null 2>&1
        if [ $? -ne 0 ]; then
            echo "Monitored Java process with PID ${JAVA_PID} is no longer alive, will re-detect the Java process to monitor for the next dump..."
            JAVA_PID=""
            sleepBeforeNextDump
            continue
        fi
      else
        echo "[READY] /tmp/${NATIVE_DUMP_NAME}"
      fi
    fi
  fi

  sleepBeforeNextDump
done