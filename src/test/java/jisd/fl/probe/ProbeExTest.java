package jisd.fl.probe;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import jisd.debug.Debugger;
import jisd.debug.JDIManager;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.CodeElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

class ProbeExTest {
    @Nested
    class d4jTest {
        String project = "Math";
        int bugId = 2;

        String testClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";
        String shortTestMethodName = "testMath1021";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        String variableName = "tmp2";
        boolean isPrimitive = true;
        boolean isField = false;
        boolean isArray = false;
        int arrayNth = -1;
        String actual = "-50";
        String locate = "org.apache.commons.math3.distribution.AbstractIntegerDistribution#inverseCumulativeProbability(double)";

        VariableInfo probeVariable = new VariableInfo(
                locate,
                variableName,
                isPrimitive,
                isField,
                isArray,
                arrayNth,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethodName,
                actual,
                probeVariable);
        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/test-classes");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/classes");
        }

        @Test
        void runTest() {
            ProbeEx prbEx = new ProbeEx(fai);
            ProbeExResult pr = prbEx.run(3000);
            pr.print();
        }

        @Test
        void VMLaunchDemo() throws IOException {
            //vm生成
            String main = TestUtil.getJVMMain(new CodeElementName(testMethodName));
            String options = TestUtil.getJVMOption();
            VirtualMachine vm;

            try {
                VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
                LaunchingConnector connector = vmm.defaultConnector();
                Map<String, Connector.Argument> cArgs = connector.defaultArguments();
                cArgs.get("options").setValue(options);
                cArgs.get("main").setValue(main);
                //起動後すぐにsuspendされるはず
                vm = connector.launch(cArgs);
            } catch (IllegalConnectorArgumentsException e) {
                throw new RuntimeException(e);
            } catch (VMStartException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Process proc = vm.process();
            vm.resume();
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
    }

    @Nested
    class simpleCaseTest {
        String testClassName = "sample.SampleTest";
        String shortTestMethodName = "case2";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        String variableName = "actual";
        boolean isPrimitive = true;
        boolean isField = false;
        boolean isArray = true;
        int arrayNth = 1;
        String actual = "3";
        String locate = testMethodName;

        VariableInfo probeVariable = new VariableInfo(
                locate,
                variableName,
                isPrimitive,
                isField,
                isArray,
                arrayNth,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethodName,
                actual,
                probeVariable);

        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");
        }

        @Test
        void runTest() {
            ProbeEx prbEx = new ProbeEx(fai);
            ProbeExResult pr = prbEx.run(2000);
            pr.print();
        }

        //特定のメソッドの呼び出しメソッドの一覧を取得
        @Test
        void getCallerDemo() throws InterruptedException {
            Debugger dbg = TestUtil.testDebuggerFactory(probeVariable.getLocateMethodElement());
            JDIManager jdi = (JDIManager) dbg.getVmManager();
            VirtualMachine vm = jdi.getJDI().vm();

            EventRequestManager manager = vm.eventRequestManager();
            MethodEntryRequest methodEntryRequest = manager.createMethodEntryRequest();
            methodEntryRequest.addClassFilter("sample.*");
            methodEntryRequest.enable();
            Thread testExec = new Thread(() -> {
                dbg.run(2000);
            });

            testExec.start();
            EventQueue queue = vm.eventQueue();
            while (true) {
                try {
                    EventSet eventSet = queue.remove();
                    for (Event ev : eventSet) {
                        if (ev instanceof MethodEntryEvent) {
                            handleMethodEntry((MethodEntryEvent) ev);
                        }
                    }
                    eventSet.resume();
                }
                catch (VMDisconnectedException e){
                    System.out.println("VM disconnected.");
                    break;
                }
            }
        }

        private void handleMethodEntry(MethodEntryEvent mEntry) {
            try {
                ThreadReference thread = mEntry.thread();
                List<StackFrame> frames = thread.frames();  // 0: 現在のメソッド, 1: 呼び出し元
                if (frames.size() > 1) {
                    // 呼び出し元フレームのロケーションを取得
                    Location callerLoc = frames.get(1).location();
                    Method callerMethod = callerLoc.method();

                    System.out.printf(
                            "== MethodEntry: %s.%s() called by %s.%s() [at %s:%d]%n",
                            mEntry.method().declaringType().name(),
                            mEntry.method().name(),
                            callerMethod.declaringType().name(),
                            callerMethod.name(),
                            callerLoc.sourceName(),
                            callerLoc.lineNumber()
                    );
                } else {
                    System.out.printf(
                            "== MethodEntry: %s.%s() called from native or bootstrap thread%n",
                            mEntry.method().declaringType().name(),
                            mEntry.method().name()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Test
        void VMLaunchDemo() throws IOException {
            //vm生成
            String main = TestUtil.getJVMMain(new CodeElementName(testMethodName));
            String options = TestUtil.getJVMOption();
            VirtualMachine vm;

            try {
                VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
                LaunchingConnector connector = vmm.defaultConnector();
                Map<String, Connector.Argument> cArgs = connector.defaultArguments();
                cArgs.get("options").setValue(options);
                cArgs.get("main").setValue(main);
                //起動後すぐにsuspendされるはず
                vm = connector.launch(cArgs);
            } catch (IllegalConnectorArgumentsException e) {
                throw new RuntimeException(e);
            } catch (VMStartException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Process proc = vm.process();
            vm.resume();
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
    }

    @Nested
    class methodCallTest {
        String testClassName = "sample.MethodCallTest";
        String shortTestMethodName = "methodCall1";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        String variableName = "result";
        boolean isPrimitive = true;
        boolean isField = false;
        boolean isArray = false;
        int arrayNth = -1;
        String actual = "11";
        String locate = "sample.MethodCall#methodCalling(int, int)";

        VariableInfo probeVariable = new VariableInfo(
                locate,
                variableName,
                isPrimitive,
                isField,
                isArray,
                arrayNth,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethodName,
                actual,
                probeVariable);

        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

            TestUtil.compileForDebug(new CodeElementName("sample.MethodCallTest"));
        }

        @Test
        void runTest() {
            ProbeEx prbEx = new ProbeEx(fai);
            ProbeExResult pr = prbEx.run(2000);
            pr.print();
        }
    }
}


