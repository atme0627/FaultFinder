package jisd.fixture;

public class ValueChangingLineFinderFixture {
    private int f = 0; // @FIELD_ASSIGN

    public static int localCase() {
        int x = 0;          // @DECL
        x = 10;             // @ASSIGN1
        x++;                // @UNARY
        return x;
    }

    public static int multiLineAssign() {
        int x = 0;
        x =                 // @ML_BEGIN
                10 +
                        20;         // @ML_END
        return x;
    }

    public static int multiLineDeclaration() {
        int x =              // @ML_DECL_BEGIN
                10 + 20;     // @ML_DECL_END
        return x;
    }

    public static int arrayAssign() {
        int[] a = {0, 0};
        a[0] = 1;           // @ARR_ASSIGN
        return a[0];
    }

    public int fieldAssign() {
        this.f = 1; // @FIELD_ASSIGN_IN_METHOD
        return this.f;
    }
}
