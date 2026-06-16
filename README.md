# Java Memory Monitor for Kubernetes

This repository provides scripts and images designed to solve the problem of monitoring memory usage of a Java
application running in a K8S container when that application container has a minimal image that omits standard Java
debug tools like `jcmd`, `jmap` etc.

It provides several components that support this, plus additional [Development Helpers](#development-helpers).

1. [`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh)
2. [`mmapReport.sh`](#mmapreportsh)
3. [`jvmKubernetesMemoryMonitor.sh`](#jvmkubernetesmemorymonitorsh)

> **IMPORTANT** Several of these scripts rely upon GNU `getopt` for option processing, on some OS, e.g. Mac OS X, an
> older version of `getopt` is installed by default.  Mac users may install this via `brew install gnu-getopt` and then
> modify their `PATH` to make it the default `getopt` used.

## `jvmMemoryMonitor.sh`

This script is a Bash script designed to run inside a container image which shares a process namespace with another
container which has the Java process whose memory you wish to monitor.  This repository builds a
`rvesse/java-memory-monitor:latest` image with the latest version of this script, the development helper
[`docker-build.sh`](#docker-buildsh) can also build an image on demand.

Run the script with just the `--help` option to see help for the script.

The script detects the Java process (or may be configured explicitly with a Java process to monitor) and then
periodically takes memory dumps, the following kinds of memory dumps may be taken:

- Java [Heap Dumps](#heap-dumps) (disabled by the `--no-heap-dumps` option)
- Java [Native Memory Tracking](#native-memory-tracking) (disabled by the `--no-native-memory` option)
- [Memory Mapped Files](#memory-mapped-files) (enabled by the `--mapped-files` option)

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

### Memory Mapped Files

When the `--mapped-files` option is specified the script will also take a dump of the processes memory mapped file usage
using the [`mmapReport.sh`](#mmapreportsh) helper script.

> **NB** This dump is opt-in only, i.e. you must explicitly enable it, as opposed to the other dump types which you must
> explicitly disable to opt-out of.

### Memory Dump Cleanup

Memory dumps are typically small (1-10KB) but the script will periodically remove old dump files to avoid ever growing
disk usage.  By default this happens every 900 seconds (15 minutes) but this can be configured via the
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

## `mmapReport.sh`

This script is a Bash script designed to provide an overview of what memory mapped files a process is using and how much
of each file is currently resident in memory since the OS will page out mapped file segments automatically.  This script
works for any process that is currently running provided the OS has a `/proc` filesystem available to query the memory
maps information from.

This script is used as a helper by [`jvmMemoryMonitor.sh`](#jvmmemorymonitorsh) when the `-m` or `--mapped-files`
options are specified.  An example report looks like the following:

```
PID 7

Found 29 memory mapped files

Rss: 174508 KB
Referenced: 174508 KB

File #Maps Rss
/opt/java/openjdk/bin/java 3 12 KB
/opt/java/openjdk/lib/libjava.so 4 144 KB
/opt/java/openjdk/lib/libjimage.so 4 76 KB
/opt/java/openjdk/lib/libjli.so 4 100 KB
/opt/java/openjdk/lib/libnet.so 4 56 KB
/opt/java/openjdk/lib/libnio.so 4 88 KB
/opt/java/openjdk/lib/modules 1 1040 KB
/opt/java/openjdk/lib/server/classes.jsa 3 13956 KB
/opt/java/openjdk/lib/server/libjvm.so 4 15588 KB
/tmp/hsperfdata_root/7 1 32 KB
/usr/lib64/gconv/gconv-modules.cache 1 4 KB
/usr/lib64/libc.so.6 4 1292 KB
/usr/lib64/libdl.so.2 4 12 KB
/usr/lib64/libm.so.6 4 72 KB
/usr/lib64/libpthread.so.0 4 12 KB
/usr/lib64/librt.so.1 4 12 KB
/usr/lib/ld-linux-aarch64.so.1 3 188 KB
/usr/lib/locale/C.utf8/LC_CTYPE 1 124 KB
/usr/lib/locale/en_US.utf8/LC_ADDRESS 1 4 KB
/usr/lib/locale/en_US.utf8/LC_COLLATE 1 168 KB
/usr/lib/locale/en_US.utf8/LC_IDENTIFICATION 1 4 KB
/usr/lib/locale/en_US.utf8/LC_MEASUREMENT 1 4 KB
/usr/lib/locale/en_US.utf8/LC_MESSAGES/SYS_LC_MESSAGES 1 4 KB
/usr/lib/locale/en_US.utf8/LC_MONETARY 1 4 KB
/usr/lib/locale/en_US.utf8/LC_NAME 1 4 KB
/usr/lib/locale/en_US.utf8/LC_NUMERIC 1 4 KB
/usr/lib/locale/en_US.utf8/LC_PAPER 1 4 KB
/usr/lib/locale/en_US.utf8/LC_TELEPHONE 1 4 KB
/usr/lib/locale/en_US.utf8/LC_TIME 1 4 KB

Largest Memory Mapped File: /opt/java/openjdk/lib/server/libjvm.so 15588 KB
```

It first indicates the process PID and how many memory mapped files are currently in use by the process.  It then
provides summaries of the resident memory usage (`Rss`) and referenced memory usage by these files.  This is followed by
a table which indicates how much resident memory is used by each memory mapped file.  As files may not be mapped
completely into memory, only segments thereof, this table indicates the filename, the number of currently mapped
segments, and the total consumed resident memory for that file.  Finally the report indicates the memory mapped file
that is the largest, i.e., the one consuming the most resident memory.

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
off-heap memory), allocates a large array of on heap objects, and then sleeps for a while before releasing both memory
allocations.  Every 10th iteration it forces a GC which gives the JVM chance to free up unused off-heap memory.

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

> **NB** The script builds only for JDK 21 by default, you can build images directly using the build arg `JDK_VERSION`
> to build for alternative JDKs