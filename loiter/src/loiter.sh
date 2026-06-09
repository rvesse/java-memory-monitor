#!/usr/bin/env bash

RESTART_INTERVAL=${1:-30}
TRACKING_MODE=${2:-summary}

echo "Restart Interval: ${RESTART_INTERVAL} seconds"
echo "Native Memory Tracking Mode: ${TRACKING_MODE}"

while [ true ]; do
  echo "Starting Java loiter process..."
  java -XX:NativeMemoryTracking=${TRACKING_MODE} -cp /app Loiter

  echo "Java process has stopped, restarting in ${RESTART_INTERVAL} seconds..."
  sleep ${RESTART_INTERVAL}
done