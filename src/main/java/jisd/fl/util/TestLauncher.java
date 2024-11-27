package jisd.fl.util;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

//junit platform launcherを用いてテストケースを実行
public class TestLauncher implements Runnable {
    String testClassName;
    String testMethodName;

    //testMethodNameはカッコつけたら動かない
    public TestLauncher(String testClassName, String testMethodName){
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
    }

    public static void main(String[] args) {
        String testClassName = args[0];
        String testMethodName = args[1];
        TestLauncher tl = new TestLauncher(testClassName, testMethodName);
        tl.run();
    }

    @Override
    public void run() {
        TestUtil.compileTestClass(testClassName);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        selectMethod(testClassName + "#" + testMethodName)
                ).build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);

        launcher.execute(request);
        listener.getSummary().printTo(new PrintWriter(System.out));
    }
}
