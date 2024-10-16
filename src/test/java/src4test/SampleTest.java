package src4test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleTest {
    @Test
    void sample1() {
        int a = 2;
        int b = 1;

        int c = a - b; // typo '-' --> '+'
        assertEquals(3, c); //actual : 1
    }
}

