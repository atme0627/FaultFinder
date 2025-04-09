package test;

import package4Test.*;

public class SimpleCase {
    private int data;

    public SimpleCase(){
        SimpleCase(10);
    }

    public int methodA(int a){
        if(a <= 10){
            return 0;
        }
        else {
            return 1;
        }
    }

    private void methodB(){
        int a = 10 * 2
                + 20 * 3
                + 30 * 4;

        return a * 2
                + a * a;
    }
}
