package jisd.fl.infra.junit;

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
import java.util.*;
import java.util.stream.Collectors;

/**
 * JUnitTestFinder クラスは指定したテストクラスに含まれる JUnit テストメソッドを動的に検索するためのユーティリティクラス。
 * JUnit Launcher API を使用してテストメソッドを検出する。
 *
 * 解析対象のテストコードをclasspathに含めたJVM上で実行することを想定
 */
public  class JUnitTestFinder {
    public static List<MethodElementName> getTestMethods(ClassElementName testClassName){
        //対象のテストクラスを取得
        Class<?> targetTestClass = null;
        try {
            targetTestClass = Class.forName(testClassName.fullyQualifiedClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Failed to find test class: " + testClassName.fullyQualifiedClassName() +
                    ". Make sure the class exists and is in the classpath.", e);

        }

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
                .map(JUnitTestFinder::getFQMNName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private static Optional<MethodElementName> getFQMNName(TestIdentifier id) {
        if(id.getSource().isEmpty()) return Optional.empty();
        MethodSource source = (MethodSource) id.getSource().get();
        String className = source.getClassName();
        String methodName = source.getMethodName();
        return Optional.of(new MethodElementName(className + "#" + methodName + "()"));
    }
}