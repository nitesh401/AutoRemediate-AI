package com.company.autoremediate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.model.TestSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestToolTest {
    @Test
    void aggregatesSurefireReports(@TempDir Path dir) throws Exception {
        Path reports = Files.createDirectories(dir.resolve("target/surefire-reports"));
        Files.writeString(reports.resolve("TEST-a.xml"),
                "<testsuite tests=\"3\" failures=\"1\" errors=\"0\" skipped=\"1\">"
                        + "<testcase classname=\"A\" name=\"ok\"/>"
                        + "<testcase classname=\"A\" name=\"bad\"><failure message=\"x\"/></testcase>"
                        + "<testcase classname=\"A\" name=\"skip\"><skipped/></testcase></testsuite>");
        Files.writeString(reports.resolve("TEST-b.xml"),
                "<testsuite tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        TestSummary s = new TestTool().summarize(dir);
        assertThat(s.run()).isEqualTo(5);
        assertThat(s.failures()).isEqualTo(1);
        assertThat(s.skipped()).isEqualTo(1);
        assertThat(s.failedTests()).containsExactly("A.bad");
        assertThat(s.passed()).isFalse();
    }

    @Test
    void unreadableReportIsNeverAPass(@TempDir Path dir) throws Exception {
        Path reports = Files.createDirectories(dir.resolve("target/surefire-reports"));
        Files.writeString(reports.resolve("TEST-broken.xml"), "<not-xml");
        assertThat(new TestTool().summarize(dir).passed()).isFalse();
    }

    @Test
    void emptyWhenNoReports(@TempDir Path dir) {
        assertThat(new TestTool().summarize(dir).run()).isZero();
    }
}
