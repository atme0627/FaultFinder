package jisd.fl.fixture;

public class ValueChangingLineFinderFixture {

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

    public static int arrayAssign() {
        int[] a = {0, 0};
        a[0] = 1;           // @ARR_ASSIGN
        return a[0];
    }
}
