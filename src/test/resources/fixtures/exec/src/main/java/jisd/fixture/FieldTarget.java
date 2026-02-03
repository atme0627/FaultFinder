package jisd.fixture;

/**
 * テスト対象クラス: フィールドを持ち、複数メソッドから変更される
 */
public class FieldTarget {
    int value;

    void initialize() {
        this.value = 0;
    }

    void setValue(int v) {
        this.value = v;
    }

    void increment() {
        this.value = this.value + 1;
    }

    void prepareAndSet(int v) {
        initialize();
        setValue(v);
    }
}
