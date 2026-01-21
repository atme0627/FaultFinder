package jisd.fl.infra.junit;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.infra.javaparser.JavaParserClassNameExtractor;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.entity.element.MethodElementName;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/**
 * ある失敗テストを実行し、失敗したAssert行、またはクラッシュ時に最後に実行された行を返す。
 * SuspiciousVariableFinderでその行に含まれる変数の情報を抽出する。
 */
public class JUnitTestLauncherForFinder {
    private final MethodElementName testMethodName;
    private final Set<String> targetClassNames;
    public JUnitTestLauncherForFinder(MethodElementName targetTestMethod) {
        this.testMethodName = targetTestMethod;
        this.targetClassNames = JavaParserClassNameExtractor.getClassNames();
        targetClassNames.addAll(JavaParserClassNameExtractor.getClassNames(Path.of(PropertyLoader.getTestSrcDir().toString())));
    }

    /**
     * テストを実行し、失敗（AssertionError や例外スロー）があった場合は
     * その最初のスタックフレームから行番号を返す。成功時は OptionalInt.empty()。
     */
    public Optional<TestFailureInfo> runTestAndGetFailureLine() {
        //テスト対象クラスの.classを含むディレクトリを動的にロード
        //テストクラスはコンパイル済みと仮定
        URL[] url;
        try {
            //TODO: 使わないようにする!
            url = new URL[]{Paths.get("/Users/ezaki/IdeaProjects/FaultFinder/classesForDebug/").toUri().toURL(), Paths.get("locallib/junit-dependency/*").toUri().toURL()};
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader testClassLoader = new URLClassLoader(url, original)) {
            //ClassLoaderを切り替え
            Thread.currentThread().setContextClassLoader(testClassLoader);
            //対象のテストクラスをロード
            Class<?> targetTestClass = testClassLoader.loadClass(testMethodName.fullyQualifiedClassName());


            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectMethod(targetTestClass, testMethodName.shortMethodName()))
                    .build();

            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.registerTestExecutionListeners(listener);

            System.out.println("EXEC: " + testMethodName);
            launcher.execute(request);
            listener.getSummary().printFailuresTo(new PrintWriter(System.out));
            listener.getSummary().printTo(new PrintWriter(System.out));

            TestExecutionSummary summary = listener.getSummary();
            // 失敗があれば最初の Failure を取り、例外のスタックトレースを調べる
            if (summary.getTotalFailureCount() > 0) {
                TestExecutionSummary.Failure firstFailure = summary.getFailures().get(0);
                Throwable ex = firstFailure.getException();


                for (StackTraceElement ste : ex.getStackTrace()) {
                    if (targetClassNames.contains(ste.getClassName())){
                        // テストクラスのフレームがあればその行番号を返す
                        return Optional.of(new TestFailureInfo(ste.getLineNumber(), new ClassElementName(ste.getClassName())));
                    }
                }
                System.err.println("Failed to findSuspiciousVariableInAssertLine the line number of the failure. " + Arrays.stream(ex.getStackTrace()).limit(10).map(StackTraceElement::toString).collect(Collectors.joining("\n")));
                return Optional.empty();
            }

            // 成功
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public record TestFailureInfo(int line, ClassElementName locateClass){
    }
}
