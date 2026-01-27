package jisd.fl.core.domain.internal;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.util.PropertyLoader;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class ValueChangingLineFinderTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static PropertyLoader.ProjectConfig original;

    @BeforeAll
    static void setUpProjectConfigForFixtures() {
        original = PropertyLoader.getTargetProjectConfig();

        var cfg = new PropertyLoader.ProjectConfig(
                PROJECT_ROOT.resolve("src/test/resources/fixtures"),
                Path.of("parse/src/java"),
                original.testSrcPath(),
                original.targetBinPath(),
                original.testBinPath()
        );

        PropertyLoader.setProjectConfig(cfg);
    }

    @AfterAll
    static void restoreProjectConfig() {
        if (original != null) {
            PropertyLoader.setProjectConfig(original);
        }
    }

    private static int lineOfFixture(String marker) throws Exception {
        String resPath = "/fixtures/parse/src/java/jisd/fl/fixture/ValueChangingLineFinderFixture.java";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                ValueChangingLineFinderTest.class.getResourceAsStream(resPath),
                StandardCharsets.UTF_8
        ))) {
            String line;
            int lineno = 0;
            while ((line = br.readLine()) != null) {
                lineno++;
                if (line.contains(marker)) return lineno;
            }
        }
        throw new IllegalStateException("Marker not found: " + marker);
    }

    private static SuspiciousLocalVariable localVar(String locateMethodFqmn, String varName) {
        return new SuspiciousLocalVariable(
                new MethodElementName("dummy.Dummy#dummy()"),
                locateMethodFqmn,
                varName,
                "DUMMY_ACTUAL",
                true,
                false
        );
    }

    private static SuspiciousFieldVariable fieldVar(String locateClass, String varName) {
        return new SuspiciousFieldVariable(
                new MethodElementName("dummy.Dummy#dummy()"),
                new ClassElementName(locateClass),
                varName,
                "DUMMY_ACTUAL",
                true
        );
    }


    @Test
    void localCase_includes_decl_assign_unary_lines() throws Exception {
        SuspiciousLocalVariable sv = localVar("jisd.fl.fixture.ValueChangingLineFinderFixture#localCase()", "x");

        int decl = lineOfFixture("@DECL");
        int assign1 = lineOfFixture("@ASSIGN1");
        int unary = lineOfFixture("@UNARY");

        List<Integer> lines = ValueChangingLineFinder.find(sv);

        assertTrue(lines.contains(decl));
        assertTrue(lines.contains(assign1));
        assertTrue(lines.contains(unary));
    }

    @Test
    void multiLineAssignCauseLines_includes_begin_to_end_range() throws Exception {
        SuspiciousLocalVariable sv = localVar("jisd.fl.fixture.ValueChangingLineFinderFixture#multiLineAssign()", "x");

        int begin = lineOfFixture("@ML_BEGIN");
        int end = lineOfFixture("@ML_END");

        List<Integer> lines = ValueChangingLineFinder.findCauseLines(sv);
        assertTrue(lines.contains(begin));
        assertFalse(lines.contains(end));
    }

    @Test
    void multiLineAssignBpLines_includes_begin_to_end_range() throws Exception {
        SuspiciousLocalVariable sv = localVar("jisd.fl.fixture.ValueChangingLineFinderFixture#multiLineAssign()", "x");

        int begin = lineOfFixture("@ML_BEGIN");
        int end = lineOfFixture("@ML_END");

        List<Integer> lines = ValueChangingLineFinder.findBreakpointLines(sv);

        for (int ln : IntStream.rangeClosed(begin, end).toArray()) {
            assertTrue(lines.contains(ln), "行が見つかりません: " + ln + " in " + lines);
        }
    }

    @Test
    void arrayAssign_includes_array_assignment_line() throws Exception {
        SuspiciousLocalVariable sv = localVar("jisd.fl.fixture.ValueChangingLineFinderFixture#arrayAssign()", "a");

        int arrAssign = lineOfFixture("@ARR_ASSIGN");

        List<Integer> lines = ValueChangingLineFinder.find(sv);

        assertTrue(lines.contains(arrAssign));
    }

    @Test
    void fieldAssign_includes_field_assignment_line() throws Exception {
        SuspiciousFieldVariable sv = fieldVar("jisd.fl.fixture.ValueChangingLineFinderFixture", "f");

        int fieldAssign = lineOfFixture("@FIELD_ASSIGN");
        int fieldAssignInMethod = lineOfFixture("@FIELD_ASSIGN_IN_METHOD");

        List<Integer> lines = ValueChangingLineFinder.find(sv);

        assertTrue(lines.contains(fieldAssign));
        assertTrue(lines.contains(fieldAssignInMethod));
    }
}
