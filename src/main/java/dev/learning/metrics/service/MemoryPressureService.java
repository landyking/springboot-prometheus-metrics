package dev.learning.metrics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class MemoryPressureService {
    private static final int CHUNK_BYTES = 256 * 1024;
    private static final long MAX_RETAINED_BYTES = 80L * 1024 * 1024;

    private final Deque<byte[]> retainedChunks = new ArrayDeque<>();
    private final AtomicLong retainedBytes = new AtomicLong();
    private final Counter allocatedBytes;

    public MemoryPressureService(MeterRegistry registry) {
        allocatedBytes = Counter.builder("demo.memory.allocated")
                .baseUnit("bytes")
                .description("Bytes allocated by the memory pressure endpoint")
                .register(registry);
        Gauge.builder("demo.memory.retained.bytes", retainedBytes, AtomicLong::get)
                .description("Bytes deliberately retained to create old-generation pressure")
                .baseUnit("bytes")
                .register(registry);
    }

    public synchronized Map<String, Object> createPressure(int requestedMegabytes) {
        int megabytes = Math.max(1, Math.min(requestedMegabytes, 32));
        int chunkCount = megabytes * 1024 * 1024 / CHUNK_BYTES;
        byte[][] temporary = new byte[chunkCount][];
        long checksum = 0;

        // Most chunks become garbage immediately, which drives young-generation GC.
        for (int i = 0; i < chunkCount; i++) {
            temporary[i] = new byte[CHUNK_BYTES];
            for (int offset = 0; offset < CHUNK_BYTES; offset += 4096) {
                temporary[i][offset] = (byte) (i + offset);
                checksum += temporary[i][offset];
            }
        }

        // Keep 25% alive, bounded at 80 MiB, to also create old-generation pressure.
        int chunksToRetain = Math.max(1, chunkCount / 4);
        for (int i = 0; i < chunksToRetain; i++) {
            retainedChunks.addLast(temporary[i]);
            retainedBytes.addAndGet(CHUNK_BYTES);
        }
        while (retainedBytes.get() > MAX_RETAINED_BYTES) {
            retainedChunks.removeFirst();
            retainedBytes.addAndGet(-CHUNK_BYTES);
        }

        allocatedBytes.increment((double) megabytes * 1024 * 1024);
        return status(megabytes, checksum);
    }

    public synchronized Map<String, Object> clear() {
        retainedChunks.clear();
        retainedBytes.set(0);
        return status(0, 0);
    }

    private Map<String, Object> status(int allocatedMegabytes, long checksum) {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("allocatedMegabytes", allocatedMegabytes);
        response.put("retainedMegabytes", retainedBytes.get() / (1024 * 1024));
        response.put("heapUsedMegabytes",
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        response.put("maxHeapMegabytes", runtime.maxMemory() / (1024 * 1024));
        response.put("checksum", checksum);
        return response;
    }
}
