package jisd.fl.infra.junit;

import jisd.fl.core.entity.element.MethodElementName;

public class JUnitTestLauncher {
    String testClassName;
    String testMethodName;

    //testMethodNameはカッコつけたら動かない
    //testMethodNameはclassを含む書き方
    public JUnitTestLauncher(String testMethodName){
        this.testClassName = testMethodName.split("#")[0];
        this.testMethodName = testMethodName;
    }

    //args[0]: method名
    public static void main(String[] args) {
        MethodElementName testMethodName = new MethodElementName(args[0]);
        JUnitTestRunner.TestRunResult result = JUnitTestRunner.runSingleTest(testMethodName, System.out);
        System.out.println(result.summaryText());
        System.exit(result.passed() ? 0 : 1);
    }
}
