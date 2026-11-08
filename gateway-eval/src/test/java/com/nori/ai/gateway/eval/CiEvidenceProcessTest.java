package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.ci.CiEvidence;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.nori.ai.gateway.eval.TestSupport.error;
import static org.assertj.core.api.Assertions.*;

/** git·docker 호출 경계: 시간 제한이 출력 읽기까지 적용되고, 실패 시 자기 자식을 남기지 않으며, 인터럽트 상태를 보존한다. */
class CiEvidenceProcessTest {
    /**
     * 자식 작업 디렉터리. @TempDir 을 쓰면 p15-r2-eval-ci-r1 에서 정리 단계 sharing violation 이 간헐 관측됐다(잠금을 쥔 프로세스는
     * 확인하지 못함 — 원인 미확정). 자식 생존 여부는 아래 liveChildren 단언이 직접 확인한다.
     */
    final Path directory = Path.of(System.getProperty("java.io.tmpdir"));

    /** 자식 JVM 이 실행하는 동작. JDK 클래스만 쓰므로 이 클래스 파일 위치만 classpath 로 준다. */
    public static final class Child {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "echo" -> System.out.print("child-ok\n");
                case "fail" -> System.exit(3);
                // 출력을 닫지 않고 멈춘다: readAllBytes 선행 구현이면 여기서 영원히 막힌다.
                case "stall" -> { System.out.print("partial"); System.out.flush(); Thread.sleep(Long.MAX_VALUE); }
                case "flood" -> { byte[] chunk = new byte[8192]; Arrays.fill(chunk, (byte) 'x'); while (true) System.out.write(chunk); }
                // 손자 프로세스가 stdout 파이프를 물려받아 쥔 채 남고 자식은 곧바로 성공 종료한다(종료 대기 뒤 읽기 대기가 따로 시간을 쓰는지 본다).
                case "holdpipe" -> { new ProcessBuilder(child("stall-pid", args[1])).redirectOutput(ProcessBuilder.Redirect.INHERIT).start(); Thread.sleep(4000); }
                case "stall-pid" -> { Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid())); Thread.sleep(Long.MAX_VALUE); }
                default -> throw new IllegalArgumentException();
            }
        }
    }

    private static List<String> child(String... arguments) {
        String java = ProcessHandle.current().info().command().orElseThrow();
        String classes;
        try { classes = Path.of(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); }
        catch (java.net.URISyntaxException e) { throw new IllegalStateException(e); }
        var command = new ArrayList<>(List.of(java, "-cp", classes, Child.class.getName()));
        command.addAll(List.of(arguments));
        return command;
    }
    private static long liveChildren() { return ProcessHandle.current().children().filter(ProcessHandle::isAlive).count(); }

    @Test void returnsOutputOfSuccessfulCommandAndRejectsNonZeroExit() throws Exception {
        var commands = CiEvidence.processes(Duration.ofSeconds(30), 1024);
        assertThat(commands.run(directory, child("echo"))).isEqualTo("child-ok\n");
        error(() -> commands.run(directory, child("fail")), 4, "COMMAND_FAILED:" + child("fail").getFirst());
    }

    @Test void timeoutAppliesWhileOutputIsStillOpenAndChildIsTerminated() throws Exception {
        long before = liveChildren();
        long started = System.nanoTime();
        error(() -> CiEvidence.processes(Duration.ofSeconds(3), 1024).run(directory, child("stall")), 4, "COMMAND_FAILED:" + child("stall").getFirst());
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
        assertThat(liveChildren()).isLessThanOrEqualTo(before);
    }

    @Test void unboundedOutputIsCutAtTheLimitAndChildIsTerminated() throws Exception {
        long before = liveChildren();
        error(() -> CiEvidence.processes(Duration.ofSeconds(30), 64 * 1024).run(directory, child("flood")), 4, "COMMAND_FAILED:" + child("flood").getFirst());
        assertThat(liveChildren()).isLessThanOrEqualTo(before);
    }

    @Test void interruptionIsPreservedAndChildIsTerminated() throws Exception {
        long before = liveChildren();
        Thread.currentThread().interrupt();
        try {
            error(() -> CiEvidence.processes(Duration.ofSeconds(30), 1024).run(directory, child("stall")), 4, "COMMAND_FAILED:" + child("stall").getFirst());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(liveChildren()).isLessThanOrEqualTo(before);
    }

    /** 시간 제한은 종료 대기와 출력 읽기 대기를 합친 하나의 마감 시각이다(각 단계에 전체 제한을 다시 주면 약 2배가 걸린다). */
    @Test void singleDeadlineCoversExitAndOutputDrain() throws Exception {
        Path pidFile = Files.createTempFile("aigw-holdpipe-", ".pid");
        try {
            long started = System.nanoTime();
            error(() -> CiEvidence.processes(Duration.ofSeconds(6), 1024).run(directory, child("holdpipe", pidFile.toString())), 4, "COMMAND_FAILED:" + child("echo").getFirst());
            // 자식 종료까지 약 4초 + 남은 마감 약 2초. 단계마다 6초를 다시 주면 10초 이상이 걸린다.
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(9));
        } finally {
            // 고아가 된 손자는 시험이 직접 정리한다(구현은 자기 자식·후손까지만 책임진다).
            String pid = Files.readString(pidFile).strip();
            if (!pid.isEmpty()) ProcessHandle.of(Long.parseLong(pid)).ifPresent(handle -> { handle.destroyForcibly(); handle.onExit().join(); });
            Files.deleteIfExists(pidFile);
        }
    }
}
