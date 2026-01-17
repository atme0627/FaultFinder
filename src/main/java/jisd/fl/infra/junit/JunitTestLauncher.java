package jisd.fl.infra.junit;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public class JunitTestLauncher {
    String testClassName;
    String testMethodName;

    //testMethodNameはカッコつけたら動かない
    //testMethodNameはclassを含む書き方
    public JunitTestLauncher(String testMethodName){
        this.testClassName = testMethodName.split("#")[0];
        this.testMethodName = testMethodName;
    }

    //args[0]: method名
    public static void main(String[] args) {
        String testMethodName = args[0];
        JunitTestLauncher tl = new JunitTestLauncher(testMethodName);
        boolean isTestPassed = tl.runTest();
        System.exit(isTestPassed ? 0 : 1);
    }

    public boolean runTest() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        selectMethod(testMethodName)
                ).build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);

        System.out.println("EXEC: " + testMethodName);
        launcher.execute(request);
        listener.getSummary().printFailuresTo(new PrintWriter(System.out));
        listener.getSummary().printTo(new PrintWriter(System.out));
        boolean isTestPassed = listener.getSummary().getTotalFailureCount() == 0;

        System.out.println("TestResult: " + (isTestPassed ? "o" : "x"));
        return isTestPassed;
    }
}
