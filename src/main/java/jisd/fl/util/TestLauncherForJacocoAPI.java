package jisd.fl.util;

import java.util.function.BooleanSupplier;

public class TestLauncherForJacocoAPI extends TestLauncher implements BooleanSupplier {

    public TestLauncherForJacocoAPI(String testMethodName) {
        super(testMethodName);
    }

    @Override
    public boolean getAsBoolean() {
        return this.runTest();
    }
}
