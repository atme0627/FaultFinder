package test;

import package4Test.*;

public class SimpleCase {
    private int data;

    public SimpleCase(){
        SimpleCase(10);
    }

    public SimpleCase(int x){
        this.data = x;
    }

    public int methodA(){
        return 10;
    }

    protected double methodB(){
        return 1.0;
    }

    private void methodC(){
    }
}
