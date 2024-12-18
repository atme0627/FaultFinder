package jisd.fl.probe;

import jisd.debug.Debugger;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class Probe extends AbstractProbe{

    public Probe(FailedAssertInfo assertInfo){
        super(assertInfo);
    }

    //assertInfoで指定されたtypeのクラスの中で
    //失敗テスト実行時に、actualに一致した瞬間に呼び出しているメソッドを返す。
    @Override
    public ProbeResult run(int sleepTime) {
        //targetのfieldを直接probe
        VariableInfo variableInfo = assertInfo.getVariableInfo();
        ProbeResult result = probing(sleepTime, variableInfo);

        //メソッドを呼び出したメソッドをコールスタックから取得
        disableStdOut("    >> Probe Info: Searching caller method from call stack.");
        Pair<Integer, Integer> probeLines = result.getProbeLines();
        String callerMethod = getCallerMethod(probeLines, variableInfo);
        result.setCallerMethod(callerMethod);

        //callerメソッドが呼び出したメソッドをカバレッジから取得
        disableStdOut("    >> Probe Info: Searching sibling method from coverage.");
        String callerClass = callerMethod.split("#")[0];
        Set<String> siblingMethods;
        try {
             siblingMethods = getSiblingMethods(callerClass, result.getCallerMethod());
        }
        catch (IOException | InterruptedException e){
            throw new RuntimeException(e);
        }
        result.setSiblingMethods(siblingMethods);
        enableStdOut();
        return result;
    }

    String getCallerMethod(Pair<Integer, Integer> probeLines, VariableInfo variableInfo) {
        PrintStream stdout = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        dbg = createDebugger();
        dbg.setMain(variableInfo.getLocateClass());
        dbg.stopAt(probeLines.getLeft());
        disableStdOut("    >> Probe Info: Running debugger.");
        dbg.run(2000);
        System.setOut(ps);
        dbg.where();
        System.setOut(stdout);

        //callerMethodをシグニチャ付きで取得する
        String[] stackTrace = bos.toString().split("\\n");
        String callerMethod = stackTrace[2];
        StringBuilder callerClassBuilder = new StringBuilder(callerMethod);
        callerClassBuilder.setCharAt(callerClassBuilder.lastIndexOf("."), '#');
        String callerClass = callerClassBuilder.substring(callerClassBuilder.indexOf("]") + 2).split("#")[0];
        int line = Integer.parseInt(callerMethod.substring(callerMethod.indexOf("(") + 1, callerMethod.length() - 1).substring(6));
        return StaticAnalyzer.getMethodNameFormLine(callerClass, line);
    }

    Set<String> getSiblingMethods(String callerClass, String callerMethod) throws IOException, InterruptedException {
        Set<String> siblingMethods = new HashSet<>();
        return siblingMethods;
    }

    //動的解析
    //テストケース実行時の実際に呼び出されているメソット群を返す
    //locateMethodはフルネーム、シグニチャあり
    Set<String> getCalleeMethods(String testMethod,
                                 String locateMethod){
        System.out.println("    >>> Probe Info: Correcting callee methods.");
        System.out.println("    >>> Probe Info: Target method --> " + locateMethod);

        Set<String> calleeMethods = new HashSet<>();
        String locateClass = locateMethod.split("#")[0];
        List<Integer> methodCallingLines = StaticAnalyzer.getMethodCallingLine(locateMethod);

        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        Debugger dbg = TestUtil.testDebuggerFactory(testMethod);
        dbg.setMain(locateClass);
        dbg.setSrcDir(targetSrcDir);

        disableStdOut("");
        StackTrace st;
        PrintStream stdOut = System.out;

        for(int l : methodCallingLines){
            dbg.stopAt(l);
        }

        dbg.run(2000);
        //callerMethodを取得
        st = getStackTrace(dbg, stdOut);
        String callerMethod = st.getMethod(1);

        for(int i = 0; i < methodCallingLines.size(); i++) {
            boolean finished = false;
            //TODO: メソッド呼び出しが行われるまで
            dbg.step();
            while (!finished) {
                st = getStackTrace(dbg, stdOut);
                dbg.stepOut();
                calleeMethods.add(st.getMethod(0));

                dbg.step();
                st = getStackTrace(dbg, stdOut);
                if (st.getMethod(0).equals(locateMethod.substring(0, locateMethod.lastIndexOf("(")))
                        || st.getMethod(0).equals(callerMethod)) {
                    finished = true;
                }
            }
            //すでにbreakpointにいる場合はスキップしない
            if(!methodCallingLines.contains(dbg.loc().getLineNumber())) {
                dbg.cont(2000);
            }
        }

        enableStdOut();
        return calleeMethods;
    }

    private StackTrace getStackTrace(Debugger dbg, PrintStream stdOut){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);

        System.setOut(ps);
        bos.reset();
        dbg.where();
        System.setOut(stdOut);

        return new StackTrace(bos.toString());
    }
}
