package sample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodCallTest {
    MethodCall mc = new MethodCall();

    @Test
    void methodCall1() {
        int actual = mc.methodCalling(2, 3);
        int expected = 11;
        assertEquals(expected, actual);
    }
}