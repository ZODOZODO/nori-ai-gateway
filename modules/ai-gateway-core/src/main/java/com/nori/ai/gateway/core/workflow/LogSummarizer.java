package com.nori.ai.gateway.core.workflow;

import java.util.*;
import java.util.regex.Pattern;

public final class LogSummarizer {
    private static final Pattern TERMS=Pattern.compile("\\b(?:[A-Z][A-Z0-9_]*_[A-Z0-9_]+|(?:[a-zA-Z_$][\\w$]*\\.)*[A-Z][\\w$]*(?:Exception|Error))\\b");
    private static final Pattern TRACE=Pattern.compile("(?i)\\btrace(?:Id|_id)?[=: ]+([0-9a-f]{16,32})\\b");
    private static final Pattern LOGGER=Pattern.compile("\\b(?:[a-z][\\w$]*\\.){2,}[A-Z][\\w$]*\\b");
    public record Summary(List<String> errorCodes,List<String> exceptionClasses,List<String> loggerNames,List<String> traceIds) {
        public Summary {errorCodes=List.copyOf(errorCodes);exceptionClasses=List.copyOf(exceptionClasses);loggerNames=List.copyOf(loggerNames);traceIds=List.copyOf(traceIds);}
        public List<String> terms(){var values=new LinkedHashSet<String>();values.addAll(errorCodes);values.addAll(exceptionClasses);values.addAll(loggerNames);return List.copyOf(values);}
    }
    public Summary extract(List<String> lines) {
        Set<String> codes=new LinkedHashSet<>(),exceptions=new LinkedHashSet<>(),loggers=new LinkedHashSet<>(),traces=new LinkedHashSet<>();
        for(String line:lines){var terms=TERMS.matcher(line);while(terms.find()){String term=terms.group();if(term.endsWith("Exception")||term.endsWith("Error"))exceptions.add(term);else codes.add(term);}var logger=LOGGER.matcher(line);while(logger.find())loggers.add(logger.group());var trace=TRACE.matcher(line);while(trace.find())traces.add(trace.group(1));}
        return new Summary(List.copyOf(codes),List.copyOf(exceptions),List.copyOf(loggers),List.copyOf(traces));
    }
}
