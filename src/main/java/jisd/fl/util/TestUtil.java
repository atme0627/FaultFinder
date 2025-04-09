package jisd.fl.util;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.fl.util.analyze.CodeElement;
import jisd.fl.util.analyze.JavaParserUtil;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.junit.jupiter.api.Test;

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
    @Deprecated
    public static void compileTestClass(String testClassName) {
        compileTestClass(new CodeElement(testClassName));
    }

    public static void compileTestClass(CodeElement targetTestClass) {
        FileUtil.initDirectory(PropertyLoader.getProperty("compiledWithJunitFilePath"));
        String[] args = {
                "-cp", PropertyLoader.getCpForCompileTestClass(),
                targetTestClass.getSrcPath().toString(),
                "-d", PropertyLoader.getProperty("compiledWithJunitFilePath")};
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }


    @Deprecated
    public static boolean execTestCaseWithJacocoAgent(String testMethodNameWithSignature, String execFileName) throws IOException, InterruptedException {
        return execTestCaseWithJacocoAgent(new CodeElement(testMethodNameWithSignature), execFileName);
    }
    //TestLauncherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1(int a)
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(CodeElement testMethod, String execFileName) throws IOException, InterruptedException {
        final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
        final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        String cmd =
                "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
                " -cp " + ":./.probe_test_classes"
                        + "./build/classes/java/main"
                        + ":" + targetBinDir
                        + ":" + junitClassPath
                + " jisd.fl.util.TestLauncher " + testMethod.getFullyQualifiedMethodName();
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();

        boolean DEBUG=false;
        if(DEBUG) {
            String line = null;
            System.out.println("STDOUT---------------");
            try (var buf = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while ((line = buf.readLine()) != null) System.out.println(line);
            }
            System.out.println("STDERR---------------");
            try (var buf = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                while ((line = buf.readLine()) != null) System.err.println(line);
            }
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

    @Deprecated
    public static Debugger testDebuggerFactory(String testMethodName) {
        return testDebuggerFactory(new CodeElement(testMethodName));
    }
    public static Debugger testDebuggerFactory(CodeElement testMethod) {
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        Debugger dbg;
        while(true) {
            try {
                dbg = new Debugger("jisd.fl.util.TestLauncher " + testMethod.getFullyQualifiedMethodName(),
                        "-cp " + "./build/classes/java/main"
                                + ":" + PropertyLoader.getProperty("compiledWithJunitFilePath")
                                + ":" + PropertyLoader.getCpForCompileTestClass());
                break;
            } catch (RuntimeException e1) {
                System.err.println(e1);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e2) {
                    throw new RuntimeException(e2);
                }
            }
        }
        dbg.setSrcDir(targetSrcDir, testSrcDir);
        DebugResult.setDefaultMaxRecordNoOfValue(500);
        return dbg;
    }


    @Deprecated
    public static Set<String> getTestMethods(String targetClassName)  {
        return getTestMethods(new CodeElement(targetClassName))
                .stream()
                .map(CodeElement::getFullyQualifiedMethodName)
                .collect(Collectors.toSet());
    }

    //targetSrcPathは最後"/"なし
    //targetClassNameはdemo.SortTestのように記述
    //返り値は demo.SortTest#test1(int a)の形式
    //publicメソッド以外は取得しない
    public static Set<CodeElement> getTestMethods(CodeElement targetClass)  {
        Set<CodeElement> methodNames = new LinkedHashSet<>();
        CompilationUnit unit;
        try {
            unit = JavaParserUtil.parseClass(targetClass);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        ClassOrInterfaceDeclaration classOrInterfaceDec = unit.getClassByName(targetClass.getShortClassName()).orElseThrow();

        //親クラスが存在する場合、再帰的にそのClassも探索
        PackageDeclaration parentPackage = unit.getPackageDeclaration().orElse(new PackageDeclaration(new Name("")));
        List<CodeElement> parentClasses =
            classOrInterfaceDec.getExtendedTypes().stream()
            .filter(parentShort -> !parentShort.getName().toString().equals(targetClass.getShortClassName()))
            .filter(parentShort -> !parentShort.getName().toString().equals("TestCase"))
            .map(parentShort -> new CodeElement(parentPackage.getNameAsString(), parentShort.getNameAsString()))
            .collect(Collectors.toList());
        parentClasses.stream().map(TestUtil::getTestMethods)
                .forEach(methodNames::addAll);

        //targetClass内のtestClassを探索
        if (!isJunit4Style(unit)) {
            //@Nestedクラスがある場合、再帰的に探索
            classOrInterfaceDec.findAll(ClassOrInterfaceDeclaration.class)
                    .stream()
                    .filter(c -> !c.equals(classOrInterfaceDec))
                    .forEach(c -> {
                        if(c.isAnnotationPresent("Nested")) {
                            methodNames.addAll(getTestMethods(new CodeElement(c)));
                        }
                        classOrInterfaceDec.remove(c);
                    });
        }
        classOrInterfaceDec.findAll(MethodDeclaration.class)
                .stream()
                .filter(md -> md.isAnnotationPresent("Test"))
                .map(CodeElement::new)
                .forEach(methodNames::add);

        return methodNames;
    }

    //あるクラス内にあるテストメソッドのみ集める（親クラス、入れ子クラスは考えない）
    private static Set<CodeElement> getTestMethodsInClass()

    //テストクラスがjunit4スタイルのものか判定
    private static boolean isJunit4Style(CompilationUnit unit){
        return unit.findAll(ImportDeclaration.class)
                .stream()
                .anyMatch(id -> id.getName().toString().equals("org.junit.Test"));
    }
}