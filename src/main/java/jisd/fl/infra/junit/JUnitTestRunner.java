package jisd.fl.infra.junit;

import jisd.fl.core.entity.element.MethodElementName;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public class JUnitTestRunner {
    static public TestRunResult runSingleTest(MethodElementName testMethodName, Appendable log) {
        try {
            System.out.println("RUN TEST: " + testMethodName);
            String junitTestMethodName = testMethodName.fullyQualifiedName().contains("(") ? testMethodName.fullyQualifiedName().split("\\(")[0] : testMethodName.fullyQualifiedName();
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(
                            selectMethod(junitTestMethodName)
                    ).build();

            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();

            launcher.execute(request, listener);
            TestExecutionSummary summary = listener.getSummary();
            String summaryText = renderSummary(summary);
            boolean passed = summary.getTotalFailureCount() == 0;
            logLine(log, "TEST RESULT: " + (passed ? "PASS" : "FAIL"));

            return new TestRunResult(
                    passed,
                    summary.getContainersFoundCount(),
                    summary.getTotalFailureCount(),
                    summaryText
            );
        } catch (IOException e){
            try {
                logLine(log, "RUNNER ERROR: " + e);
            } catch (IOException ignore){}
            return new TestRunResult(false, 0, 0, "");
        }
    }

    // log を StringBuilder / System.err / PrintWriter 等に繋げられるように
    private static void logLine(Appendable log, String s) throws IOException {
        if (log != null) log.append(s).append('\n');
    }

    private static String renderSummary(org.junit.platform.launcher.listeners.TestExecutionSummary summary) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // failures + summary を文字列として返す
        summary.printFailuresTo(pw);
        summary.printTo(pw);

        pw.flush();
        return sw.toString();
    }

    public record TestRunResult(
            boolean passed,
            long totalTestCount,
            long totalFailureCount,
            String summaryText
    ) {}
}
