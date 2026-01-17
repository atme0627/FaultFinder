package jisd.fl.probe.info;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.domain.SuspiciousReturnsSearcher;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.susp.*;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.util.NewPropertyLoader;
import jisd.fl.core.entity.MethodElementName;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SuspiciousExpressionTest {
    SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();
    SuspiciousReturnsSearcher searcher = new SuspiciousReturnsSearcher();
    /**
     * SuspiciousExpression.toString()がexpectedと同じである要素がリスト内にあるかを確かめるMatcher
     */
    private static Matcher<SuspiciousExpression> hasToString(final String expected) {
        return new FeatureMatcher<>(
                equalTo(expected),
                "SuspiciousReturnValue whose toString()",
                "toString()"
        ) {
            @Override
            protected String featureValueOf(SuspiciousExpression actual) {
                return actual.toString();
            }
        };
    }

    @Nested
    class searchSuspiciousReturns {
        String testFqcn = "org.sample.MethodCallingTest";
        private String getFqmn(String testMethodName){
            return testFqcn + "#" + testMethodName + "()";
        }

        @BeforeEach
        void initProperty() {
            Dotenv dotenv = Dotenv.load();
            Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
            NewPropertyLoader.ProjectConfig config = new NewPropertyLoader.ProjectConfig(
                    testProjectDir,
                    Path.of("src/main/java"),
                    Path.of("src/test/java"),
                    Path.of("build/classes/java/main"),
                    Path.of("build/classes/java/test")
            );
            NewPropertyLoader.setProjectConfig(config);
        }

        @Test
        void polymorphism() {
            String testMethodName = "polymorphism";
            MethodElementName locateClass = new MethodElementName(testFqcn);
            int locateLine = 19;
            SuspiciousVariable suspVariable = new SuspiciousVariable(
                    new MethodElementName(getFqmn(testMethodName)),
                    getFqmn(testMethodName),
                    "totalArea",
                    "32.0",
                    true,
                    false
            );

            SuspiciousAssignment suspAssignment = factory.createAssignment(
                    new MethodElementName(getFqmn(testMethodName)),
                    locateClass,
                    locateLine,
                    suspVariable
            );

            List<SuspiciousReturnValue> actualResult = searcher.search(suspAssignment);
            //actualResult.forEach(System.out::println);
            assertThat(actualResult, hasSize(3));
            assertThat(actualResult, hasItems(
                hasToString(
                "[ SUSPICIOUS RETURN VALUE ]\n" +
                        "    getArea(){\n" +
                        "       ...\n" +
                        "        // At org.sample.shape.Rectangle\n" +
                        "18:     return height * width;                             == 4.0     \n" +
                        "\n" +
                        "       ...\n" +
                        "    }"
                ),
                hasToString(
               "[ SUSPICIOUS RETURN VALUE ]\n" +
                        "    getArea(){\n" +
                        "       ...\n" +
                        "        // At org.sample.shape.Rectangle\n" +
                        "18:     return height * width;                             == 18.0    \n" +
                        "\n" +
                        "       ...\n" +
                        "    }"
                ),
                hasToString(
               "[ SUSPICIOUS RETURN VALUE ]\n" +
                        "    getArea(){\n" +
                        "       ...\n" +
                        "        // At org.sample.shape.Triangle\n" +
                        "18:     return (double) (base * height) / 2;               == 10.0    \n" +
                        "\n" +
                        "       ...\n" +
                        "    }"
                )
            ));
        }

        @Test
        void polymorphismLoop(){
            String testMethodName = "polymorphismLoop";
            MethodElementName locateClass = new MethodElementName(testFqcn);
            int locateLine = 34;
            SuspiciousVariable suspVariable = new SuspiciousVariable(
                    new MethodElementName(getFqmn(testMethodName)),
                    getFqmn(testMethodName),
                    "totalArea",
                    "32.0",
                    true,
                    false
            );

            SuspiciousAssignment suspAssignment = factory.createAssignment(
                    new MethodElementName(getFqmn(testMethodName)),
                    locateClass,
                    locateLine,
                    suspVariable
            );

            List<SuspiciousReturnValue> result = searcher.search(suspAssignment);
            result.forEach(System.out::println);
        }

        @Test
        void polymorphismLoopReturn(){
            String testMethodName = "polymorphismLoopReturn";
            MethodElementName locateClass = new MethodElementName("org.sample.shape.Shape");
            int locateLine = 30;

            SuspiciousReturnValue suspReturn = factory.createReturnValue(
                    new MethodElementName(getFqmn(testMethodName)),
                    locateClass,
                    locateLine,
                    "8"
            );

            List<SuspiciousReturnValue> result = searcher.search(suspReturn);
            result.forEach(System.out::println);
        }

        @Test
            //メソッド呼び出しの引数で呼ばれているメソッドの特定
        void polymorphismLoopArgument() {
            String testMethodName = "polymorphismLoopArgument";
            MethodElementName locateClass = new MethodElementName("org.sample.MethodCallingTest");
            int locateLine = 70;

            SuspiciousArgument suspArg = factory.createArgument(
                    new MethodElementName(getFqmn(testMethodName)),
                    locateClass,
                    locateLine,
                    "18.0",
                    new MethodElementName("max"),
                    1,-1
            );

            List<SuspiciousReturnValue> result = searcher.search(suspArg);
            result.forEach(System.out::println);
        }

        @Test
        void chaining(){

        }
        @Test
        void nestedCalling(){

        }
        @Test
        void chainingAndNested(){

        }
        @Test
        void staticMethodCalling(){

        }
        @Test
        void createObjAndCallMethod(){

        }
        @Test
        void arrayAccess(){

        }
        @Test
        void callingViaCast(){

        }
        @Test
        void lambdaInArgument(){

        }
        @Test
        void recursion(){

        }
    }
}