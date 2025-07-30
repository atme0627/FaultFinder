package demo;

public class Utils {
    public Utils() {
    }

    public int add(int a, int b){
        return a + b;
    }

    public int mult(int a, int b){
        return a - b; //buggy: - --> *
    }

}
