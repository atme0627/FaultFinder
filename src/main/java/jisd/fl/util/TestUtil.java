package jisd.fl.util;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public  class TestUtil {
    public static void compileTestClass(String testClassName) {
        final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        final String testBinDir = PropertyLoader.getProperty("testBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();

        FileUtil.initDirectory(compiledWithJunitFilePath);

        String[] args = {"-cp", junitClassPath + ":" + targetBinDir + ":" + testBinDir,  testSrcDir + "/" + testClassName.replace(".", "/") + ".java", "-d", compiledWithJunitFilePath};

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        //System.out.println("javac " + Arrays.toString(args));
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }

    //TestLauncherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1(int a)
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(String testMethodNameWithSignature, String execFileName) throws IOException, InterruptedException {
        final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
        final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String testBinDir = PropertyLoader.getProperty("testBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();

        String testMethodName = testMethodNameWithSignature.split("\\(")[0];
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;
        String junitTestSelectOption =" --select-method " + testMethodName;

//        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
//                " -jar " + junitConsoleLauncherPath + " -cp " + targetBinDir + ":" + testBinDir + ":" +
//                compiledWithJunitFilePath + junitTestSelectOption;

        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
                " -cp " + "./build/classes/java/main" + ":./.probe_test_classes" + ":" + targetBinDir + ":" + testBinDir + ":" + junitClassPath + " jisd.fl.util.TestLauncher " + testMethodName;

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();

//        //debug
        String line = null;
//        System.out.println("STDOUT---------------");
//        try ( var buf = new BufferedReader( new InputStreamReader( proc.getInputStream() ) ) ) {
//            while( ( line = buf.readLine() ) != null ) System.out.println( line );
//        }
        System.out.println("STDERR---------------");
        try ( var buf = new BufferedReader( new InputStreamReader( proc.getErrorStream() ) ) ) {
            while( ( line = buf.readLine() ) != null ) System.err.println( line );
        }

        //execファイルが生成されるまで待機
        while(true){
            File f = new File(generatedFilePath);
            if(f.exists()){
                break;
            }
        }
        //ファイルの生成が行われたことを出力
        System.out.println("Success to generate " + generatedFilePath + ".");
        System.out.println("testResult " + (proc.exitValue() == 0 ? "o" : "x"));
        return proc.exitValue() == 0;
    }

    public static Debugger testDebuggerFactory(String testMethodName) {
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String testBinDir = PropertyLoader.getProperty("testBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        Debugger dbg;
        while(true) {
            try {
                dbg = new Debugger("jisd.fl.util.TestLauncher " + testMethodName,
                        "-cp " + "./build/classes/java/main" + ":" + testBinDir + ":" + targetBinDir + ":" + junitClassPath);
                break;
            } catch (RuntimeException ignored) {
                System.err.println(ignored);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        dbg.setSrcDir(targetSrcDir, testSrcDir);
        DebugResult.setDefaultMaxRecordNoOfValue(500);
        return dbg;
    }

    //targetSrcPathは最後"/"なし
    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    //publicメソッド以外は取得しない
    //testMethodはprivateのものを含めないのでpublicOnlyをtrueに
    public static Set<String> getTestMethods(String targetClassName)  {
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        Set<String> methodNames = new LinkedHashSet<>();
        Function<CallableDeclaration<?>, String> methodNameBuilder = (n) -> (
                targetClassName.replace("/", ".") + "#" + n.getNameAsString());

        CompilationUnit unit;
        try {
            unit = JavaParserUtil.parseClass(targetClassName, true);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        String shortName = targetClassName.substring(targetClassName.lastIndexOf(".") + 1);
        ClassOrInterfaceDeclaration coid = unit.getClassByName(shortName).get();

        //テストクラスがjunit4スタイルのものか判定
        boolean junit4Style = false;
        List<ImportDeclaration> ids = unit.findAll(ImportDeclaration.class);
        for(ImportDeclaration id : ids){
            if(id.getName().toString().equals("org.junit.Test")) junit4Style = true;
        }

        //親クラスがいる場合、そっちのテストクラスも探す
        for(ClassOrInterfaceType parent : coid.getExtendedTypes()){
            if(parent.getName().toString().equals(shortName)) continue;
            if(parent.getName().toString().equals("TestCase")) continue;

            String fullName = StaticAnalyzer.getExtendedClassNameWithPackage(testSrcDir, parent.getNameAsString(), targetClassName);
            Set<String> parentMethods = getTestMethods(fullName);
            parentMethods = parentMethods
                    .stream()
                    .map((m)-> targetClassName + "#" + m.split("#")[1])
                    .collect(Collectors.toSet());
            methodNames.addAll(parentMethods);
        }

        if(!junit4Style) {
            List<ClassOrInterfaceDeclaration> childlen = coid.findAll(ClassOrInterfaceDeclaration.class);
            for (ClassOrInterfaceDeclaration child : childlen) {
                if (child.equals(coid)) continue;
                coid.remove(child);
            }
            List<MethodDeclaration> mds = coid.findAll(MethodDeclaration.class);
            for (MethodDeclaration md : mds) {
                if(md.isPublic()
                    && md.getAccessSpecifier() == AccessSpecifier.PUBLIC
                    && md.getType().isVoidType()
                    && md.getParameters().isEmpty()
                    && md.getAnnotations().isEmpty()
                    && md.findAncestor(MethodDeclaration.class).isEmpty()
                    && !md.getNameAsString().equals("setUp")
                    && !md.getNameAsString().equals("tearDown")){
                    methodNames.add(methodNameBuilder.apply(md));
                }
            }
        }
        else {
            List<MethodDeclaration> mds = coid.findAll(MethodDeclaration.class);
            for (MethodDeclaration md : mds) {
                if(md.isAnnotationPresent("Test")){
                    methodNames.add(methodNameBuilder.apply(md));
                }
            }
        }

        return methodNames;
    }
}