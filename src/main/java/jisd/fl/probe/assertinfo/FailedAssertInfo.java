package jisd.fl.probe.assertinfo;

import com.github.javaparser.ast.body.VariableDeclarator;

//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
//typeNameがプリミティブ型の場合、fieldNameはprimitiveに
public abstract class FailedAssertInfo {
    private final AssertType assertType;
    private final String testClassName;
    private final String testMethodName;
    private final VariableInfo variableInfo;

    public FailedAssertInfo(AssertType assertType,
                            String testClassName,
                            String testMethodName,
                            VariableInfo variableInfo) {

        this.assertType = assertType;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.variableInfo = variableInfo;
    }

    public abstract Boolean eval(String variable);
    
    public AssertType getAssertType() {
        return assertType;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getTestMethodName() {
        return  testMethodName;
    }

    public VariableInfo getVariableInfo() {
        return variableInfo;
    }
}
