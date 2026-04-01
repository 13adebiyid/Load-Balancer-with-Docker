# Distributed File Storage with Load Balancing

A distributed file storage system built in Java with a JavaFX desktop client, a custom TCP load balancer, Docker-managed storage nodes, and a dual-database (SQLite + MySQL) persistence layer. Files are split into encrypted chunks and distributed across storage containers; the load balancer decides which container handles each chunk based on real-time system load.

---

## What It Does

Users log in through a JavaFX GUI and can upload, download, share, and delete files. When a file is uploaded, it is split into 1 MB chunks, each chunk is AES-128 encrypted with a unique key, and the chunks are sent to the load balancer over TCP. The load balancer selects a storage container for each chunk and forwards it via SFTP. Chunk locations, encryption keys, and file metadata are persisted in a local SQLite database that periodically syncs to a remote MySQL instance. On download, the process reverses: chunks are fetched from the containers they were originally stored on, verified by SHA-256 checksum, decrypted, and reassembled in order.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Host Machine                                           │
│                                                         │
│  ┌──────────────┐   TCP :8080   ┌──────────────────┐   │
│  │  JavaFX GUI  │ ─────────────▶│  Load Balancer   │   │
│  │  (App.java)  │               │  Server :8080    │   │
│  └──────────────┘               └────────┬─────────┘   │
│                                          │ SFTP :22     │
│  ┌──────────────────┐                    │              │
│  │ Orchestrator     │◀── HTTP :8081 ─────┘              │
│  │ (port 8081)      │   (scale requests)                │
│  └────────┬─────────┘                                   │
│           │ docker-compose --scale                      │
└───────────┼─────────────────────────────────────────────┘
            │
            ▼  Docker Network: comp20081-network
  ┌──────────────────────────────────────────────┐
  │  storage1   storage2   storage3   storage4   │
  │  (Ubuntu containers with SSH/SFTP on :22)    │
  └──────────────────────────────────────────────┘
            │
  ┌─────────┴────────┐
  │  comp20081-mysql │  (metadata sync target)
  └──────────────────┘
```

There are three separate processes involved:

**JavaFX GUI** (`App.java`) — the desktop client. Users interact with a file manager, text editor, and an SSH remote terminal. All file I/O goes through a `LoadBalancerClient` that connects to the load balancer over TCP on port 8080.

**Load Balancer** (`LoadBalancerMain`) — a standalone TCP server listening on port 8080. It maintains a pool of `FileStorageContainer` objects and uses the active scheduling algorithm to select a container for each incoming chunk operation. Each accepted connection is handled by a thread from a fixed pool of 10. The load balancer runs two background `ScheduledExecutorService` jobs: one for periodic container health checks (every 300 seconds via SFTP write/delete), and one for auto-scaling decisions (also every 300 seconds).

**Container Orchestrator** (`OrchestratorMain`) — a minimal HTTP server on port 8081 that acts as the bridge between the JVM and the Docker daemon. The load balancer cannot call `docker-compose` directly from inside a container, so it sends HTTP requests to the orchestrator running on the host (`host.docker.internal:8081`), which then shells out to `docker-compose --scale`.

**Storage containers** — Ubuntu containers exposing SSH/SFTP on port 22. Container IDs follow the convention `container-N`; the hostname each container is reached at is derived by replacing the `container-` prefix with `storage`, so `container-1` resolves to `storage1` on the Docker network. All chunk reads and writes use JSch over SFTP.

---

## Scheduling Algorithms

The `LoadBalancer` class implements three selection strategies. The active algorithm is chosen automatically based on observed system load; the switch happens in `getNextContainer()` before every routing decision.

**Round Robin** — the default under normal load (fewer than 5 active connections per container on average). A simple cursor increments through the container list modulo its size. This is the right default: it spreads work evenly with zero overhead when no container is under pressure.

**Shortest Job First** (`shortestJobSelect`) — activated automatically when average connections exceed the 5-per-container threshold. It scans all containers and routes to whichever has the fewest active connections at that moment. This is effectively Least Connections scheduling under a different name, and it makes sense under high load when Round Robin's blind rotation would pile work onto already-busy nodes.

**Priority Scheduling** (`prioritySelect`) — implemented and wired up but never auto-triggered; it can be enabled manually by setting `currentAlgorithm = "Priority"`. Each container has an integer priority stored in `containerPriorities`. The selector always picks the highest-priority container, which creates a strict ordering useful for scenarios where certain nodes have more storage capacity or faster disks. The practical limitation is that priority values are all initialised to 1 and nothing in the current codebase updates them, so this path behaves identically to picking the first container.

The threshold logic means the system degrades gracefully: under normal conditions it cycles through containers predictably; under load it starts actively routing toward the least-busy node.

---

## Concurrency

**File locking.** `FileLockManager` is a process-wide singleton backed by a `ConcurrentHashMap<String, FileLock>`. Each logical file gets a `ReentrantLock` initialised with `fairness = true`. The fair mode enforces FIFO acquisition order — threads waiting for a lock are granted it in the order they asked. This is the starvation prevention mechanism: no thread can be indefinitely bypassed by later arrivals. Any operation (upload, download, delete) must hold the file's lock for its duration and releases it in a `finally` block. If a lock cannot be acquired within 120 seconds, the operation fails cleanly rather than blocking indefinitely.

**Connection tracking.** `FileStorageContainer` tracks concurrent connections with an `AtomicInteger`, giving the scheduling layer a lock-free, always-consistent view of per-container load. Increment and decrement are exposed as explicit methods, which the `ScalingTest` harness also uses to simulate load by directly manipulating connection counts.

**Server threading.** `LoadBalancerServer` uses `Executors.newFixedThreadPool(10)` — each incoming client connection is dispatched to the pool. Sockets have a 120-second read timeout to prevent stuck threads from holding pool slots indefinitely.

**UI threading.** The JavaFX GUI offloads uploads and downloads to background `Thread`/`Task` instances and marshals progress updates and alerts back to the FX application thread via `Platform.runLater()`.

**Database sync.** `MySQLDB` runs a `ScheduledExecutorService` that syncs SQLite → MySQL every 5 minutes using full-transaction upserts (`INSERT ... ON DUPLICATE KEY UPDATE`). A separate scheduled job calls `cleanupExpiredSessions()` hourly. Both user and file sync are wrapped in a single MySQL transaction so a partial failure rolls back the entire sync cycle.

---

## Running Locally with Docker

The system is built as two fat JARs by Maven and is intended to run inside Docker alongside the storage containers.

**Prerequisites:** Docker, Docker Compose, Java 11+, Maven 3.

**Build:**
```bash
cd cwk/JavaFXApplication1
mvn package
# produces:
#   target/gui-app.jar        (JavaFX client)
#   target/loadbalancer.jar   (load balancer server)
```

**Expected Docker Compose setup** (inferred from the code):

The `DockerAPI` class issues `docker-compose -f /app/docker-compose.yml up -d --scale comp20081-files-container=<N>`. The `DockerContainerManager` reaches the orchestrator at `host.docker.internal:8081`. Based on the hostnames, network names, and database connection strings embedded throughout the code, a `docker-compose.yml` should define:

```yaml
services:
  comp20081-mysql-db:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: comp20081
    networks:
      - comp20081-network

  load-balancer:
    build: .   # jar entry point: LoadBalancerMain
    ports:
      - "8080:8080"
    networks:
      - comp20081-network
    extra_hosts:
      - "host.docker.internal:host-gateway"

  comp20081-files-container:
    image: ubuntu:latest
    # requires openssh-server running on port 22 with root:root credentials
    networks:
      - comp20081-network

networks:
  comp20081-network:
```

**Start the orchestrator on the host** (so the load balancer can reach it at `host.docker.internal:8081`):
```bash
java -cp target/gui-app.jar com.mycompany.javafxapplication1.OrchestratorMain
```

**Start the load balancer:**
```bash
docker-compose up load-balancer
```

**Run the GUI client** (requires JavaFX runtime on the host):
```bash
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml \
     -jar target/gui-app.jar
```

The GUI connects to the load balancer at `localhost:8080`. Default admin credentials are `admin` / `admin`, created automatically on first launch.

---

## Tech Stack

| Layer | Technology |
|---|---|
| GUI | JavaFX 13 (FXML + controllers) |
| Build | Maven 3 with maven-shade-plugin (fat JARs) |
| Load balancer | Java sockets, `java.util.concurrent` thread pools |
| File I/O | JSch 0.1.55 (SFTP over SSH) |
| Encryption | AES-128 (`javax.crypto`), SHA-256 checksums |
| Local database | SQLite via `sqlite-jdbc 3.40.0` |
| Remote database | MySQL 8 via `mysql-connector-java 8.0.28` |
| Containerisation | Docker, Docker Compose |
| Target JVM | Java 11 |

---

## Key Design Decisions and Trade-offs

**Dual-database architecture.** SQLite serves as the fast local store — all metadata lookups during chunk routing happen against it without network round-trips. MySQL is the durable, shared record of truth, kept in sync asynchronously every 5 minutes. The trade-off is a window of inconsistency: if the process crashes mid-sync, MySQL may lag. For a system where the GUI and load balancer co-locate on one machine, this is a reasonable simplification.

**SFTP as the storage protocol.** Each chunk read or write opens a new SSH session and SFTP channel, transfers the bytes, and tears down the session. This is simple to implement and requires no custom server code on the storage containers — any Ubuntu image with `openssh-server` works. The cost is per-chunk TCP handshake and SSH negotiation overhead. A persistent connection pool or a custom binary protocol over a long-lived socket would significantly reduce latency for large files with many chunks.

**Automatic algorithm switching.** The load balancer does not expose algorithm selection to operators; it switches silently based on a connection threshold. This keeps the routing transparent to the client but means there is no way to override the algorithm for specific workloads without modifying code. The threshold (5 connections × number of containers) is hardcoded.

**Chunk size selection.** `FileChunker.getOptimalChunkSize` targets roughly 10 equal-sized chunks, capped at 10 MB each. For very small files the entire file becomes one chunk. The chunk size drives how many parallel SFTP sessions are opened during upload, so it directly affects throughput.

**Fair locking for starvation prevention.** Using `ReentrantLock(true)` guarantees that lock requests are served in FIFO order across all threads competing for the same file. The alternative — an unfair lock — is faster in high-contention scenarios but can starve long-waiting threads. For a file storage system where user-visible latency matters, the fairness guarantee is the right choice.

**Encryption key storage.** Per-chunk AES keys are stored in plaintext in the local SQLite `encryption_keys` table. The encryption protects data at rest on the storage containers, but not against anyone with read access to the SQLite database on the host. A proper key management layer would be needed before this is production-appropriate.
