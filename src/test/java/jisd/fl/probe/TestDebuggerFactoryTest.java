package jisd.fl.probe;

import jisd.debug.Debugger;
import jisd.fl.testutil.TestDebuggerFactory;
import org.junit.jupiter.api.Test;

class TestDebuggerFactoryTest {
    TestDebuggerFactory td = new TestDebuggerFactory();

    @Test
    void compileTestClassTest() {
        //String javaFilePath = "./src/test/java/src4test/SampleTest.java";
        //String binDir = "./build/classes/java/main/";
        //String junitStandaloneDir = "./locallib";

//        try{
//            Files.delete(Paths.get("./."));
//        }catch(IOException e){
//            System.out.println(e);
//        }

        String testSrcPath = "/Users/ezaki/IdeaProjects/proj4test/src/test/java/demo/SortTest.java";
        String targetBinDir = "/Users/ezaki/IdeaProjects/proj4test/build/classes/java/main";
        String junitStandaloneDir = "/Users/ezaki/Desktop/tmp";
        td.compileTestClass(testSrcPath, targetBinDir, junitStandaloneDir);
    }

    @Test
    void createTest() {
        String testClassName = "src4test.SampleTest";
        String testMethodName = "sample2";
        String javaFilePath = "./src/test/java/src4test/SampleTest.java";
        String binDir = "./build/classes/java/main/";
        String junitStandaloneDir = "./locallib";

        Debugger dbg = td.create(testClassName, testMethodName, javaFilePath, binDir, junitStandaloneDir);
        dbg.run(1000);
    }

}