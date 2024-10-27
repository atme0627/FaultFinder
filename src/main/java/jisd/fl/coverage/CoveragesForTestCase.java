package jisd.fl.coverage;

import java.util.Set;

public class CoveragesForTestCase extends Coverages {
    private final String testMethodName;


    public CoveragesForTestCase(String testClassName, Set<String> targetClassNames, String testMethodName) {
        super(testClassName, targetClassNames);
        this.testMethodName = testMethodName;
    }

    public String getTestMethodName() {
        return testMethodName;
    }
}
