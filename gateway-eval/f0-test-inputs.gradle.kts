import groovy.json.JsonSlurper
import java.security.MessageDigest

// F0 시험은 레포 루트의 사전등록 입력(골든셋·fixture·프롬프트·arg-limits·PREREG·eval/py), arg-limits 문서, main 소스 스캔,
// arg-limits CODE·SCHEMA 근거(워크스페이스 파일:줄)를 직접 읽습니다. 그 바이트를 루트 기준 정렬 상대 경로 + 내용 SHA-256 스냅샷 하나로 만들어
// Test 선언 입력과 루트 fingerprint 가 읽는 aigw.* 시스템 속성에 같은 값으로 결속합니다(파일 부재 = "MISSING").
fun aigwF0Sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
val aigwF0Files: List<File> = buildList {
    addAll(rootProject.fileTree("eval") { exclude("f0/results/**", "f0/chat-template/**", "**/__pycache__/**") }.files)
    add(rootProject.file("docs/gates/f0/arg-limits.md"))
    addAll(rootProject.fileTree("gateway-eval/src/main/java").files)
    val limits = rootProject.file("eval/f0/arg-limits-v1.json")
    val parsed = if (limits.isFile) runCatching { JsonSlurper().parse(limits) }.getOrNull() else null
    ((parsed as? Map<*, *>)?.get("fields") as? List<*>).orEmpty().forEach { field ->
        ((field as? Map<*, *>)?.get("evidence") as? List<*>).orEmpty().forEach { item ->
            val evidence = item as? Map<*, *>
            val ref = evidence?.get("ref") as? String
            if (evidence?.get("kind") in setOf("CODE", "SCHEMA") && ref != null && ref.contains(':')) add(File(rootDir.parentFile, ref.substringBeforeLast(':')))
        }
    }
}
val aigwF0InputsSha256: String = aigwF0Sha(aigwF0Files
    .map { rootDir.toPath().relativize(it.toPath().normalize()).toString().replace('\\', '/') to (if (it.isFile) aigwF0Sha(it.readBytes()) else "MISSING") }
    .distinctBy { it.first }.sortedBy { it.first }
    .joinToString("") { "${it.first}\t${it.second}\n" }.toByteArray(Charsets.UTF_8))
tasks.named<Test>("test") {
    inputs.property("aigwF0InputsSha256", aigwF0InputsSha256)
    systemProperty("aigw.f0InputsSha256", aigwF0InputsSha256)
}
