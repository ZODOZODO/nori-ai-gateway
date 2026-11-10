package com.nori.ai.gateway.app.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class RouteClassifier {
    private final List<PathPattern> jobs = List.of("/v1/query","/v1/conversations/{id}/messages","/v1/analyses").stream()
        .map(PathPatternParser.defaultInstance::parse).toList();
    public boolean isJob(HttpServletRequest request) {
        var path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        return "POST".equals(request.getMethod()) && jobs.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
