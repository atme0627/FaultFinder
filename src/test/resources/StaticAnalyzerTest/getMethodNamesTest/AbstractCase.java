package test;

import package4Test.*;

public abstract class AbstractCase {
    private int data;

    public AbstractCase(int x){
        this.data = x;
    }

    public int methodA(){
        return 10;
    }

    abstract void abstractMethod1();
    abstract int abstractMethod2(int a, int b);
}
