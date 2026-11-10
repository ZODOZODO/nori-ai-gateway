package com.nori.ai.gateway.tcmock.verify;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 잔류 검사기(AC-41)다. 이름 붙은 텍스트 원천(DB 행·로그·감사·이벤트·응답·Evidence)을 평문·JSON 이스케이프(유니코드 이스케이프 포함)·
 * URL 디코드·base64/base64url 디코드(JWS payload 포함)와 각 디코드 결과의 JSON 이스케이프 해제로 펼쳐 sentinel을 찾는다.
 * 최소 sentinel 8바이트의 base64(패딩 제외 11자)까지 디코드 대상이다. 보고는 건수와 위치명·인코딩 종류만 담고 원문은 담지 않는다.
 */
public final class ResidueScanner {
    public record Match(String location, String encoding) {}
    public record Report(int matchCount, List<Match> matches) {
        public Report { matches = List.copyOf(matches); }
        @Override public String toString() { return "ResidueReport[matchCount=" + matchCount + ", locations=" + matches + "]"; }
    }
    private static final Pattern BASE64_TOKEN = Pattern.compile("[A-Za-z0-9+/_-]{11,}={0,2}");
    private static final Pattern HEX4 = Pattern.compile("[0-9A-Fa-f]{4}");
    private ResidueScanner() {}

    public static Report scan(Map<String,String> sources, Set<String> sentinels) {
        if (sentinels.isEmpty() || sentinels.stream().anyMatch(value -> value == null || value.length() < 8)) throw new IllegalArgumentException("검사 sentinel이 필요합니다");
        var matches = new ArrayList<Match>();
        for (var source : new TreeMap<>(sources).entrySet()) {
            for (var view : views(source.getValue()).entrySet())
                for (String sentinel : sentinels) if (view.getValue().contains(sentinel)) matches.add(new Match(source.getKey(), view.getKey()));
        }
        return new Report(matches.size(), matches);
    }
    /** 디코드 결과가 바탕 텍스트와 같으면 그 view는 두지 않아 같은 원문을 인코딩별로 중복 보고하지 않는다. */
    private static Map<String,String> views(String text) {
        var views = new LinkedHashMap<String,String>();
        views.put("PLAIN", text);
        derived(views, "JSON_UNESCAPED", jsonUnescaped(text), text);
        String url;
        try { url = URLDecoder.decode(text, StandardCharsets.UTF_8); } catch (IllegalArgumentException malformed) { url = text; }
        if (!url.equals(text)) {
            views.put("URL_DECODED", url);
            derived(views, "URL_JSON_UNESCAPED", jsonUnescaped(url), url);
        }
        var decoded = new StringBuilder();
        var tokens = BASE64_TOKEN.matcher(text);
        // JWS의 header.payload.signature는 '.'로 나뉘므로 각 조각이 따로 디코드됩니다.
        while (tokens.find()) decoded.append(decode(tokens.group())).append('\n');
        views.put("BASE64_DECODED", decoded.toString());
        derived(views, "BASE64_JSON_UNESCAPED", jsonUnescaped(decoded.toString()), decoded.toString());
        return views;
    }
    private static void derived(Map<String,String> views, String name, String value, String base) { if (!value.equals(base)) views.put(name, value); }
    /** JSON 문자열 이스케이프(따옴표·역슬래시·슬래시·제어 문자·4자리 hex 유니코드)를 푼다. 형식이 맞지 않는 이스케이프는 그대로 둔다. */
    private static String jsonUnescaped(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) { out.append(c); continue; }
            char next = text.charAt(i + 1);
            switch (next) {
                case '"', '\\', '/' -> { out.append(next); i++; }
                case 'b' -> { out.append('\b'); i++; }
                case 'f' -> { out.append('\f'); i++; }
                case 'n' -> { out.append('\n'); i++; }
                case 'r' -> { out.append('\r'); i++; }
                case 't' -> { out.append('\t'); i++; }
                case 'u' -> {
                    if (i + 6 <= text.length() && HEX4.matcher(text.substring(i + 2, i + 6)).matches()) { out.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16)); i += 5; }
                    else out.append(c);
                }
                default -> out.append(c);
            }
        }
        return out.toString();
    }
    private static String decode(String token) {
        // base64url(JWS·padding 생략)를 먼저, 표준 alphabet을 다음으로 시도합니다. 둘 다 아니면 이 조각은 디코드 대상이 아닙니다.
        try { return new String(Base64.getUrlDecoder().decode(token.replace("=", "")), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException notUrlSafe) {
            try { return new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException notBase64) { return ""; }
        }
    }
}
