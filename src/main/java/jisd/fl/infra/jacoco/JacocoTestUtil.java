package jisd.fl.infra.jacoco;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.util.PropertyLoader;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public  class JacocoTestUtil {
    public static Set<MethodElementName> getTestMethods(ClassElementName testMethodName){
        //テスト対象クラスの.classを含むディレクトリを動的にロード
        //テストクラスはコンパイル済みと仮定
        URL[] url;
        try {
            url = new URL[]{Paths.get(PropertyLoader.getTestBinDir().toString()).toUri().toURL()};
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader testClassLoader = new URLClassLoader(url, original)){
            //ClassLoaderを切り替え
            Thread.currentThread().setContextClassLoader(testClassLoader);
            //対象のテストクラスをロード
            Class<?> targetTestClass = testClassLoader.loadClass(testMethodName.fullyQualifiedClassName());

            // JUnit Launcher API を使用してテストを検出
            DiscoverySelector selector = DiscoverySelectors.selectClass(targetTestClass);
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selector)
                    .build();

            Launcher launcher = LauncherFactory.create();
            TestPlan testPlan = launcher.discover(request);

            return testPlan.getRoots().stream()
                    .flatMap(root -> testPlan.getDescendants(root).stream())
                    .filter(TestIdentifier::isTest)
                    .map(JacocoTestUtil::getFQMNName)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static Optional<MethodElementName> getFQMNName(TestIdentifier id) {
        if(id.getSource().isEmpty()) return Optional.empty();
        MethodSource source = (MethodSource) id.getSource().get();
        String className = source.getClassName();
        String methodName = source.getMethodName();
        return Optional.of(new MethodElementName(className + "#" + methodName + "()"));
    }
}