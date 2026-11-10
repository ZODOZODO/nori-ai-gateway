package com.nori.ai.gateway.tcmock;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MockCallCounter {
    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final java.util.List<Set<String>> authorizationRequests = new java.util.concurrent.CopyOnWriteArrayList<>();
    public void recordAuthorization(Set<String> permissions) { increment("authorize"); authorizationRequests.add(Set.copyOf(permissions)); }
    public java.util.List<Set<String>> authorizationRequests() { return java.util.List.copyOf(authorizationRequests); }
    public void increment(String operation) { counts.computeIfAbsent(operation, key -> new AtomicLong()).incrementAndGet(); }
    public long count(String operation) { var value = counts.get(operation); return value == null ? 0 : value.get(); }
    public Map<String, Long> snapshot() {
        Map<String, Long> result = new TreeMap<>(); counts.forEach((key, value) -> result.put(key, value.get())); return Map.copyOf(result);
    }
    public void reset() { counts.clear(); authorizationRequests.clear(); }
}
