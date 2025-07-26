package experiment.defect4j;

import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.LineElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class Defects4jUtilTest {
    @BeforeEach
    void stopLog(){
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);
    }


    @Test
    void changeTargetVersionTest() {
        Defects4jUtil.changeTargetVersion("Math", 15);
        System.out.println(PropertyLoader.getProperty("targetSrcDir"));
        System.out.println(PropertyLoader.getProperty("junitConsoleLauncherPath"));
    }

    @Nested
    class extractBuggyLines {
        File d4jDir;
        @BeforeEach
        void init() {
            d4jDir = new File("/Users/ezaki/Desktop/research/d4jProjects");
            Defects4jUtil.changeTargetVersion("Lang", 1);
        }

        @Test
        void lang1() {
            int bugId = 1;
            List<LineElementName> result = Defects4jUtil.extractBuggyLines(targetProjectDir(bugId));

            assertThat(result, containsInAnyOrder(
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 467),
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 468),
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 471)
            ));
        }

        @Test
        void lang3() {
            int bugId = 3;
            List<LineElementName> result = Defects4jUtil.extractBuggyLines(targetProjectDir(bugId));

            assertThat(result, containsInAnyOrder(
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 593),
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 597),
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 601),
                    new LineElementName("org.apache.commons.lang3.math.NumberUtils#createNumber(String)", 605)
            ));
        }

        @Test
        void lang4() {
            int bugId = 4;
            List<LineElementName> result = Defects4jUtil.extractBuggyLines(targetProjectDir(bugId));

            assertThat(result, containsInAnyOrder(
                    new LineElementName("org.apache.commons.lang3.text.translate.LookupTranslator#<ulinit>()", 31),
                    new LineElementName("org.apache.commons.lang3.text.translate.LookupTranslator#LookupTranslator(CharSequence[][])", 46),
                    new LineElementName("org.apache.commons.lang3.text.translate.LookupTranslator#LookupTranslator(CharSequence[][])", 51),
                    new LineElementName("org.apache.commons.lang3.text.translate.LookupTranslator#translate(CharSequence, int, Writer)", 77)
            ));
        }


        private File targetProjectDir(int bugId) {
            return new File(Defects4jUtil.getProjectDir("Lang", bugId, true));
        }

    }
}
