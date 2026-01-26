package jisd.fl.infra.junit;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.infra.jvm.JUnitLaunchSpecFactory;
import jisd.fl.infra.jvm.JVMLauncher;
import jisd.fl.infra.jvm.JVMProcess;

import java.io.IOException;

public class JUnitDebugger extends EnhancedDebugger {
    static private final String hostName = "localhost";
    static private final String port = "5001";

    public JUnitDebugger(MethodElementName testMethod) {
        super(createJVM(testMethod, hostName, port), hostName, port);
    }

    static private JVMProcess createJVM(MethodElementName testMethod, String hostName, String port) {
        try {
            return JVMLauncher.launch(JUnitLaunchSpecFactory.withJDWP(testMethod, hostName + ":" + port));
        }
        catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}
