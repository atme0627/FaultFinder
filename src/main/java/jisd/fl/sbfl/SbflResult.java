package jisd.fl.sbfl;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

public class SbflResult {
    List<Pair<String, Double>> result;

    public SbflResult(){
        result = new ArrayList<>();
    }

    public void setElement(String element, SbflStatus status, Formula f){
        Pair<String, Double> p = Pair.of(element, status.getSuspiciousness(f));
        if(!p.getRight().isNaN()){
            result.add(p);
        }
    }

    public void sort(){
        result.sort((o1, o2)->{
            return o2.getRight().compareTo(o1.getRight());
        });
    }

    public int getSize(){
        return result.size();
    }

    public void printFLResults() {
        printFLResults(getSize());
    }

    public void printFLResults(int top){
        for(int i = 0; i < min(top, getSize()); i++){
            Pair<String, Double> element = result.get(i);
            System.out.println((i+1) + ": " + element.getLeft() + "  susp: " + element.getRight());
        }
    }

    public String getMethodOfRank(int rank){
        if(!rankValidCheck(rank)) return "";
        return result.get(rank - 1).getLeft();
    }

    public boolean rankValidCheck(int rank){
        if(rank > getSize()) {
            System.err.println("Set valid rank.");
            return false;
        }
        return true;
    }

    public double getSuspicious(String targetElementName){
        Pair<String, Double> element = searchElement(targetElementName);
        return element.getRight();
    }

    public void setSuspicious(String targetElementName, double suspicious){
        Pair<String, Double> element = searchElement(targetElementName);
        element.setValue(suspicious);
    }

    private Pair<String, Double> searchElement(String targetElementName){
        for(Pair<String, Double> element : result){
            if(element.getLeft().equals(targetElementName)) return element;
        }
        System.err.println("Element not found. name: " + targetElementName);
        return null;
    }
}
