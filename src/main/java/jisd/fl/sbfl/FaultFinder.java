package jisd.fl.sbfl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FaultFinder {
    List<Map<String, Double>> suspList = new ArrayList<>();

    public FaultFinder(){

    }

    public void setSuspElement(Map<String, Double> element){
        suspList.add(element);
    }

    public List<Map<String, Double>> getFLResults(){

    }
}
