package sample;

class MethodCall {
    public MethodCall() {
    }

    public int methodCalling(int x, int y){
        Utils util = new Utils();
        int result = util.add(x, y) + util.mult(x, y);
        return result;
    }
}