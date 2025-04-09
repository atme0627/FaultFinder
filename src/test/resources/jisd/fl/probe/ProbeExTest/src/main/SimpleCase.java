class SimpleCase {

    public SimpleCase(){}

    public int[] sort(int a, int b, int c){
        int[] result = new int[]{a, b, c};
        if(a <= b){
            if(b <= c){
                return result;
            }
            else if(a <= c){
                result = swap(result, 1, 2);
                return result;
            }
            else {
                result = swap(result, 0, 2);
                result = swap(result, 1, 2);
                return result;
            }
        }
        else {
            if(a <= c){
                result = swap(result, 0, 1);
                return result;
            }
            else if(b <= c){
                result = swap(result, 0, 2);
                result = swap(result, 0, 1);
                return result;
            }
            else {
                result = swap(result, 0, 2);
                return result;
            }
        }
    }

    public int[] swap(int[] arr, int first, int second){
        int[] result = new int[3];
        for(int i = 0; i < arr.length; i++){
            if(i == first){
                result[i] = arr[second];
            }
            if(i == second){
                result[i] = arr[first];
            }
            result[i] = arr[i];
        }
        return result;
    }
}