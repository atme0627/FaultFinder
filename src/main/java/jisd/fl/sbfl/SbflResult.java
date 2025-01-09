package jisd.fl.sbfl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.text.Format;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

public class SbflResult {
    List<MutablePair<String, Double>> result;

    public SbflResult(){
        result = new ArrayList<>();
    }

    public void setElement(String element, SbflStatus status, Formula f){
        MutablePair<String, Double> p = MutablePair.of(element, status.getSuspiciousness(f));
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
        Pair<Integer, Integer> l = maxLengthOfName();
        int classLength = l.getLeft();
        int methodLength = l.getRight();

        String header = "| RANK |" +
                StringUtils.repeat(' ', classLength - "CLASS NAME".length()) + " CLASS NAME " +
                        "|" + StringUtils.repeat(' ', methodLength - "METHOD NAME".length()) + " METHOD NAME " +
                "| SUSP SCORE |";
        String partition = StringUtils.repeat('=', header.length());

        System.out.println("[  SBFL RANKING  ]");
        System.out.println(partition);
        System.out.println(header);
        System.out.println(partition);
        int previousRank = 1;
        for(int i = 0; i < min(top, getSize()); i++){
            Pair<String, Double> element = result.get(i);
            //同率を考慮する
            int rank;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(String.format("%.4f", element.getRight()).equals(String.format("%.4f", result.get(i-1).getRight()))){
                    rank = previousRank;
                }
                else {
                    rank = i+1;
                }
            }

            System.out.println("| " + String.format("%3d ", rank) + " | " +
                    StringUtils.leftPad(element.getLeft().split("#")[0], classLength) + " | " +
                    StringUtils.leftPad(element.getLeft().split("#")[1], methodLength) + " | " +
                    String.format("  %.4f  ", element.getRight()) + " |");
            previousRank = rank;
        }
        System.out.println(partition);
        System.out.println();
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
        MutablePair<String, Double> element = searchElement(targetElementName);
        return element.getRight();
    }

    public void setSuspicious(String targetElementName, double suspicious){
        MutablePair<String, Double> element = searchElement(targetElementName);
        element.setValue(suspicious);
    }

    private MutablePair<String, Double> searchElement(String targetElementName){
        for(MutablePair<String, Double> element : result){
            if(element.getLeft().equals(targetElementName)) return element;
        }
        System.err.println("Element not found. name: " + targetElementName);
        return null;
    }

    public boolean isElementExist(String targetElementName){
        for(MutablePair<String, Double> element : result){
            if(element.getLeft().equals(targetElementName)) return true;
        }
        return false;
    }

    private Pair<Integer, Integer> maxLengthOfName(){
        int classLength = 0;
        int methodLength = 0;

        for(MutablePair<String, Double> e : result){
            classLength = Math.max(classLength, e.getLeft().split("#")[0].length());
            methodLength = Math.max(methodLength, e.getLeft().split("#")[1].length());
        }

        return Pair.of(classLength, methodLength);
    }
}
