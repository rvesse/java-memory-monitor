# Java Memory Monitor for Kubernetes

This repository provides scripts and images designed to solve the problem of monitoring memory usage of a Java
application running in a K8S container when that container has a minimal image that omits standard Java debug tools like
`jcmd`, `jmap` etc.

It provides two components that support this, plus additional [Development Helpers](#development-helpers).

1. [`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh)
2. [`jvmKubernetesMemoryMonitor.sh`](#jvmkubernetesmemorymonitorsh)

> **IMPORTANT** Both these scripts rely upon GNU `getopt` for option processing, on some OS, e.g. Mac OS X, an older
> version of `getopt` is installed by default.  Mac users may install this via `brew install gnu-getopt` and then modify
> their `PATH` to make it the default `getopt` used.

## `jvmMemoryMonitor.sh`

This script is a Bash script designed to run inside a container image which shares a process namespace with another
container which has the Java process whose memory you wish to monitor.  This repository builds a
`rvesse/java-memory-monitor:latest` image with the latest version of this script, the development helper
[`docker-build.sh`](#docker-buildsh) can also build an image on demand.

Run the script with just the `--help` option to see help for the script.

The script detects the Java process (or may be configured explicitly with a Java process to monitor) and then
periodically takes memory dumps, two kinds of memory dumps may be taken:

- Java [Heap Dumps](#heap-dumps) (disabled by the `--no-heap-dumps` option)
- Java [Native Memory Tracking](#native-memory-tracking) (disabled by the `--no-native-memory` option)

Dumps are taken every 180 seconds (3 minutes) by default but this can be configured via the `--dump-interval` option.
You may also choose to limit how long the script takes dumps for via the `--limit` option, when specified dumps are only
taken for the specified limit after which time the script exits.

### Heap Dumps

Heap Dumps are triggered via the `jmap` tool, the heap dump is written to `/tmp/` in the application container where the
Java process is running.

### Native Memory Tracking

Native Memory Tracking is obtained by calling the `jcmd` tool with the `VM.native_memory` option.  These dumps are
written to `/tmp/` in the debug container (**not** the application container!).

> **IMPORTANT** For this to work properly the application you are monitoring **MUST** have been started with the
> appropriate JVM option, i.e., `-XX:NativeMemoryTracking=summary` or `-XX:NativeMemoryTracking=detail` as appropriate.

You can obtain either `summary` reports or `detail` reports by specifying either the `--summary` or `--detail` options
to the script.  Additionally if you are looking to understand memory usage over time, or find memory leaks, then you can
generate dumps with diffs versus a baseline by specifying the `--baseline` option.  When that option is specified a
baseline is taken and then subsequent dumps are diffs from that original baseline.

#### Native Memory Dump Cleanup

Native memory dumps are typically small (2-4KB) but the script will periodically remove old dump files to avoid ever
growing disk usage.  By default this happens every 900 seconds (15 minutes) but this can be configured via the
`--cleanup-interval` option.

### Dead Process Detection

If the monitored Java process died the default behaviour of the script is to try and re-detect the Java process and
restart monitoring of the new Java process.  This of course assumes that when the monitored Java process dies it gets
automatically restarted, if this is not the case then you should set the `--no-continue-on-jvm-failure` option so that
the script does not run forever.

Of course in some deployment scenarios, e.g. K8S pod, the failure of the application container may be due to the pod
being terminated in which case all containers, including any debug containers running this script will be forcibly
terminated.

### Signals

A `SIGINT` or `SIGTERM` to the script will abort memory monitoring and cause the script to exit.

## `jvmKubernetesMemoryMonitor.sh`

This script is a Bash script designed to run on a developers machine, it handles the coordination of attaching the debug
container with the [`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh) in it to an application container in a K8S pod on your
K8S cluster.  Once the debug container is attached it watches the logs from the debug container and when it detects new
memory dumps available transfers them to a location on your local machine.

At a minimum you need to do the following:

```bash
./jvmKubernetesMemoryMonitor.sh --namespace your-namespace --pod your-pod --container app-container
```

If you wish to customise the memory monitoring options specify the `--` arguments separator and then any options to the
[`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh) script e.g.

```bash
./jvmKubernetesMemoryMonitor.sh --namespace your-namespace --pod your-pod --container app-container \
  --baseline --detail --no-heap-dumps --dump-interval 60
```
Would monitor only native memory in detail with diffs against a baseline every 60 seconds.

Run the script with the `--help` option for full script help.

## Development Helpers

### Loiter

The `java-loiter:latest` image, and the `Loiter` Java application are a trivial toy Java application used to help test
and develop the [`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh) script.

This app sits in an infinite loop, every iteration it allocates a 32-128MB direct byte buffer (to simulate using some
off-heap memory) and then sleeps for a while.  Every 10th iteration it forces a GC which gives the JVM chance to free up
unused off-heap memory.

This can be built either via the [Docker Compose](#docker-compose) file or the [`docker-build.sh`](#docker-buildsh)
script.

### Docker Compose

A Docker Compose file is provided that makes it possible to test the [`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh)
script.  Firstly start the toy Java application:

```bash
docker compose up --build -d loiter
```

Then start the memory monitor:

```bash
docker compose up --build -d memory-monitor
```

You can then view the logs of the memory monitor to see it working:

```bash
docker logs memory-monitor-1
```

You can customise the `command` for the `memory-monitor` service in the compose file if you wish to experiment with
different options to the memory monitor.

### `docker-build.sh`

This script builds both the `java-memory-monitor:latest` image that packages the
[`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh) script and the `java-loiter:latest` image which is used for testing by the
[Docker Compose](#docker-compose) file.

This script attemps to build a multi-platform image for both `linux/amd64` and `linux/arm64` platforms, if when running
the script you receive the following message:

> ERROR: failed to build: Multi-platform build is not supported for the docker driver.
> Switch to a different driver, or turn on the containerd image store, and try again.
> Learn more at https://docs.docker.com/go/build-multi-platform/
> Docker Build failed

Then you need to use a different builder i.e.

```bash
docker buildx use your-multiplatform-builder
./docker-build.sh
```

By default the script only builds the images locally, if you wish to push them to a repository so you can use those
images in a K8S cluster, then you can supply a repository name/URL as the first option and the image will be tagged and
pushed as `your-repository/java-memory-monitor:latest` e.g.

```bash
./docker-build.sh your-repository
```