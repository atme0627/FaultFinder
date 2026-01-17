package jisd.fl.infra.junit;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.jvm.JUnitLaunchSpecFactory;
import jisd.fl.infra.jvm.JVMLauncher;
import jisd.fl.infra.jvm.JVMProcess;

import java.io.IOException;
import java.util.Map;

public class JUnitDebugger extends EnhancedDebugger {
    public JUnitDebugger(MethodElementName testMethod) {
        super(createVM(testMethod));
    }

    private static String JVMMain(MethodElementName testMethod){
        return "jisd.fl.infra.junit.JUnitTestLauncher " + testMethod.getFullyQualifiedMethodName();
    }

    public static String getJVMOption(){
        return "-cp " + "./build/classes/java/main"
                + ":" + PropertyLoader.getTargetBinDir().toString()
                + ":" + PropertyLoader.getTestBinDir().toString()
                + ":" + "locallib/junit-dependency/*";
    }

    static protected VirtualMachine createVM(MethodElementName testMethod){
        try {
            String hostName = "localhost";
            String port = "5000";
            JVMProcess p = JVMLauncher.launch(JUnitLaunchSpecFactory.withJDWP(testMethod, hostName + ":" + port));
            VirtualMachineManager vmManager = Bootstrap.virtualMachineManager();
            AttachingConnector socket = vmManager.attachingConnectors().stream()
                    .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("SocketAttach connector not found"));
            Map<String, Connector.Argument> args = socket.defaultArguments();
            args.get("hostname").setValue(hostName);
            args.get("port").setValue(port);
            return socket.attach(args);
        }
        catch (IOException | IllegalConnectorArgumentsException e){
            throw new RuntimeException(e);
        }
    }
}
