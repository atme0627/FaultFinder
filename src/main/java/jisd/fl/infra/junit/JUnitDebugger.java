package jisd.fl.infra.junit;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.util.PropertyLoader;

public class JUnitDebugger extends EnhancedDebugger {
    public JUnitDebugger(MethodElementName testMethod) {
        super(JVMMain(testMethod), getJVMOption());
    }

    private static String JVMMain(MethodElementName testMethod){
        return "jisd.fl.infra.junit.TestLauncher " + testMethod.getFullyQualifiedMethodName();
    }

    public static String getJVMOption(){
        return "-cp " + "./build/classes/java/main"
                + ":" + PropertyLoader.getTargetBinDir()
                + ":" + PropertyLoader.getTestBinDir()
                + ":" + PropertyLoader.getJunitClassPaths();
    }
}
