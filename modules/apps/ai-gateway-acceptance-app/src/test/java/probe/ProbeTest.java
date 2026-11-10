package probe;
import org.junit.jupiter.api.*;
// P01 LeafReportRoundTripFunctionalTest 의 OTR 입력과 같은 시험. Gradle test 는 이 패키지를 실행하지 않고 EvidenceBlockWriterTest 의 Launcher 만 실행한다.
@Tag("stage-F4") @Tag("label-MOCK_API")
class ProbeTest {
    @Test @Tag("AC-24-JOB") void succeeds() { Assertions.assertEquals(4, 2 + 2); }
    @Test @Tag("AC-24-AUTHZ") void fails() { Assertions.fail("OTR_SENTINEL_PRIVATE_FAILURE"); }
    @Test @Tag("AC-24-LOT-ACTIVE") @Disabled("OTR_SENTINEL_PRIVATE_DISABLED") void disabled() { Assertions.fail(); }
    @Test @Tag("AC-24-LOT-MULTI") void aborted() { Assumptions.assumeTrue(false, "OTR_SENTINEL_PRIVATE_ABORT"); }
    @Test @Tag("AC-24-LOT-ENDED") void sharedPass() { Assertions.assertTrue(true); }
    @Test @Tag("AC-24-LOT-ENDED") void sharedFail() { Assertions.fail("OTR_SENTINEL_PRIVATE_SHARED"); }
}
