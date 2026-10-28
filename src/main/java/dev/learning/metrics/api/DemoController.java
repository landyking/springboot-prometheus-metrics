package dev.learning.metrics.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import dev.learning.metrics.service.MemoryPressureService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DemoController {
    private final AtomicLong orderSequence = new AtomicLong();
    private final Counter ordersCreated;
    private final Counter ordersRejected;
    private final Timer computeTimer;
    private final MemoryPressureService memoryPressureService;

    public DemoController(MeterRegistry registry, MemoryPressureService memoryPressureService) {
        this.memoryPressureService = memoryPressureService;
        ordersCreated = registry.counter("demo.orders", "result", "created");
        ordersRejected = registry.counter("demo.orders", "result", "rejected");
        computeTimer = Timer.builder("demo.compute")
                .description("Time spent doing simulated CPU work")
                .publishPercentileHistogram()
                .register(registry);
    }

    @PostMapping("/memory-pressure")
    public Map<String, Object> memoryPressure(
            @RequestParam(defaultValue = "16") int megabytes) {
        return memoryPressureService.createPressure(megabytes);
    }

    @DeleteMapping("/memory-pressure")
    public Map<String, Object> clearMemoryPressure() {
        return memoryPressureService.clear();
    }

    @GetMapping("/greeting")
    public Map<String, Object> greeting(@RequestParam(defaultValue = "learner") String name)
            throws InterruptedException {
        // Small latency jitter makes the latency graph more realistic.
        Thread.sleep(ThreadLocalRandom.current().nextLong(5, 35));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Hello, " + name + "!");
        response.put("time", Instant.now().toString());
        return response;
    }

    @GetMapping("/compute")
    public Map<String, Object> compute(@RequestParam(defaultValue = "20000") int size) {
        int safeSize = Math.max(1_000, Math.min(size, 200_000));
        long result = computeTimer.record(() -> calculate(safeSize));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("iterations", safeSize);
        response.put("checksum", result);
        return response;
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(40, 180));

        // Intentional failures make the error-rate panel useful.
        if (ThreadLocalRandom.current().nextInt(10) == 0) {
            ordersRejected.increment();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Simulated inventory shortage");
        }

        ordersCreated.increment();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", orderSequence.incrementAndGet());
        response.put("status", "created");
        return response;
    }

    private long calculate(int size) {
        long value = 0;
        for (int i = 1; i <= size; i++) {
            value = (value + (long) i * i) % 1_000_000_007;
        }
        return value;
    }
}
