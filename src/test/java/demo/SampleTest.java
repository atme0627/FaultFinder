package demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleTest {
    @Test
    void sampleTest() {
        Calc calc = new Calc();
        int a = 2;
        int b = 1;
        int c = 2;
        int actual = calc.methodCalling(a, b + c);
        int expected = 11;
        // org.opentest4j.AssertionFailedError: expected: <11> but was: <4>
        assertEquals(expected, actual);
    }
}
