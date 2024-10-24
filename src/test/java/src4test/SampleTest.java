package src4test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleTest {
    @Test
    void sample1() {
        int a = 2;
        int b = 1;

        int c = a - b; // typo '-' --> '+'
        assertEquals(3, c); // actual : 1
    }

    @Test
    void sample2(){
        int a = 6;
        int b = 2;
        int x;

        int c;
        c  = a + b; // typo '+' --> '-'
        System.out.println("hello world from sample2");
        if(c > 6){
            x = 1;
        }
        else {
            x = 2;
        }

        assertEquals(4, c); // actual : 8
    }
}

