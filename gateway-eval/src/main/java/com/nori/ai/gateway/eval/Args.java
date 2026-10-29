package com.nori.ai.gateway.eval;

import java.nio.file.Path;
import java.util.*;

public final class Args {
    private final Map<String,List<String>> options = new LinkedHashMap<>();
    public Args(String[] arguments, int offset, Set<String> flags) {
        for (int i = offset; i < arguments.length; i++) {
            String key = arguments[i]; if (!key.startsWith("--")) throw new EvalException(2, "INVALID_ARGUMENTS");
            if (flags.contains(key)) options.computeIfAbsent(key, k -> new ArrayList<>()).add("true");
            else { if (++i >= arguments.length || arguments[i].startsWith("--")) throw new EvalException(2, "INVALID_ARGUMENTS"); options.computeIfAbsent(key, k -> new ArrayList<>()).add(arguments[i]); }
        }
    }
    public void only(String... allowed) { Set<String> keys = Set.of(allowed); if (!keys.containsAll(options.keySet())) throw new EvalException(2, "INVALID_ARGUMENTS"); }
    public String optional(String key) { List<String> values = options.get(key); if (values == null) return null; if (values.size() != 1) throw new EvalException(2, "DUPLICATE_ARGUMENT"); return values.getFirst(); }
    public String value(String key, String fallback) { String value = optional(key); return value == null ? fallback : value; }
    public String required(String key) { String value = optional(key); if (value == null || value.isBlank()) throw new EvalException(2, "ARGUMENT_MISSING"); return value; }
    public Path path(String key) { return Path.of(required(key)); }
    public List<String> all(String key) { return options.getOrDefault(key, List.of()); }
    public boolean flag(String key) { return options.containsKey(key); }
}
