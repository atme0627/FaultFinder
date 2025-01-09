package jisd.fl.util;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;

import static java.lang.System.exit;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public class TestLauncher {
    String testClassName;
    String testMethodName;

    //testMethodNameはカッコつけたら動かない
    //testMethodNameはclassを含む書き方
    public TestLauncher(String testMethodName){
        this.testClassName = testMethodName.split("#")[0];
        this.testMethodName = testMethodName;
    }

    public static void main(String[] args) {
        String testMethodName = args[0];
        TestLauncher tl = new TestLauncher(testMethodName);
        boolean isTestPassed = tl.runTest();
        exit(isTestPassed ? 0 : 1);
    }

    public boolean runTest() {
        //TestUtil.compileTestClass(testClassName);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        selectMethod(testMethodName)
                ).build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);

        System.out.println("EXEC: " + testMethodName);
        launcher.execute(request);
        //listener.getSummary().printFailuresTo(new PrintWriter(System.out));
        //listener.getSummary().printTo(new PrintWriter(System.out));
        boolean isTestPassed = listener.getSummary().getTotalFailureCount() == 0;

        System.out.println("TestResult: " + (isTestPassed ? "o" : "x"));
        return isTestPassed;
    }
}
