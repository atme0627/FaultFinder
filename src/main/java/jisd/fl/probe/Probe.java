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
    public ProbeResult run(int sleepTime) {
        //targetのfieldを直接probe
        VariableInfo variableInfo = assertInfo.getVariableInfo();
        ProbeResult result = probing(sleepTime, variableInfo);
        if(result.isArgument()){
            System.out.println("    >> Probe Info: There is no probe line in " + variableInfo.getLocateMethod());
            return result;
        }

        //メソッドを呼び出したメソッドをコールスタックから取得
        System.out.println("    >> Probe Info: Searching caller method from call stack.");
        Pair<Integer, Integer> probeLines = result.getProbeLines();
        Pair<Integer, String> callerMethod = getCallerMethod(probeLines, variableInfo.getLocateClass());
        result.setCallerMethod(callerMethod);

        //callerメソッドが呼び出したメソッドをカバレッジから取得
        System.out.println("    >> Probe Info: Searching sibling method");
        Set<String> siblingMethods;
        siblingMethods = getSiblingMethods(
                assertInfo.getTestMethodName(),
                result.getCallerMethod().getRight(),
                result.getProbeMethod());

        result.setSiblingMethods(siblingMethods);
        return result;
    }

    Set<String> getSiblingMethods(String testMethod, String callerMethod, String probeMethod) {
        Set<String> siblings =  this.getCalleeMethods(testMethod, callerMethod).getAllMethods();
        siblings.remove(probeMethod);
        return siblings;
    }

}
