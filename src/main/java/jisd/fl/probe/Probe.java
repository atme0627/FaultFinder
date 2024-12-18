package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.apache.commons.lang3.tuple.Pair;

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
        System.out.println("    >> Probe Info: Searching caller method from call stack.");
        Pair<Integer, Integer> probeLines = result.getProbeLines();
        String callerMethod = getCallerMethod(probeLines, variableInfo);
        result.setCallerMethod(callerMethod);

        //callerメソッドが呼び出したメソッドをカバレッジから取得
        System.out.println("    >> Probe Info: Searching sibling method");
        Set<String> siblingMethods;
        siblingMethods = getSiblingMethods(
                assertInfo.getTestMethodName(),
                result.getCallerMethod(),
                result.getProbeMethod());

        result.setSiblingMethods(siblingMethods);
        return result;
    }

    String getCallerMethod(Pair<Integer, Integer> probeLines, VariableInfo variableInfo) {
        dbg = createDebugger();
        dbg.setMain(variableInfo.getLocateClass());
        disableStdOut("    >> Probe Info: Running debugger.");
        dbg.stopAt(probeLines.getLeft());
        dbg.run(2000);
        enableStdOut();
        StackTrace st = getStackTrace(dbg);

        //callerMethodをシグニチャ付きで取得する
        return st.getMethodAndCallLocation(1).getRight();
    }

    Set<String> getSiblingMethods(String testMethod, String callerMethod, String probeMethod) {
        Set<String> siblings =  this.getCalleeMethods(testMethod, callerMethod).getAllMethods();
        siblings.remove(probeMethod);
        return siblings;
    }

}
