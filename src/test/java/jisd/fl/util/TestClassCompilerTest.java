package jisd.fl.util;

import org.junit.jupiter.api.Test;

class TestClassCompilerTest {

    @Test
    void compileTestClassTest() {
        TestClassCompiler tcc = new TestClassCompiler();
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
        tcc.compileTestClass(testSrcPath, targetBinDir);
    }
}