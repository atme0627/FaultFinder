package demo;

public class Calc {
    public int methodCalling(int x, int y){
        Utils util = new Utils();
        int z = 0;
        int result = util.add(x, y) + util.mult(x, y) + z;
        return result;
    }
}
