import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SampleTest {
    SimpleCase simpleCase = new SimpleCase();

    @Test
    void case1(){
        int[] actual = simpleCase.sort(1, 2, 3);
        int[] expected = new int[]{1, 2, 3};
        assertArrayEquals(expected, actual);
    }

    @Test
    void case2(){
        int[] actual = simpleCase.sort(1, 3, 2);
        int[] expected = new int[]{1, 2, 3};
        assertArrayEquals(expected, actual);
    }

    @Test
    void case3(){
        int[] actual = simpleCase.sort(2, 1, 3);
        int[] expected = new int[]{1, 2, 3};
        assertArrayEquals(expected, actual);
    }

    @Test
    void case4(){
        int[] actual = simpleCase.sort(2, 3, 1);
        int[] expected = new int[]{1, 2, 3};
        assertArrayEquals(expected, actual);
    }

    @Test
    void case5(){
        int[] actual = simpleCase.sort(3, 1, 2);
        int[] expected = new int[]{1, 2, 3};
        assertArrayEquals(expected, actual);
    }

    @Test
    void case6(){
        int[] actual = simpleCase.sort(3, 2, 1);
        int[] expected = new int[]{1, 2, 3};
        assertArrayEquals(expected, actual);
    }
}
