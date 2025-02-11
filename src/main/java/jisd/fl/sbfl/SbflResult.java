package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.min;

public class SbflResult {
    List<MutablePair<String, Double>> result;
    private Set<String> highlightMethods = new HashSet<>();

    public SbflResult(){
        result = new ArrayList<>();
    }

    public void setElement(String element, SbflStatus status, Formula f){
        MutablePair<String, Double> p = MutablePair.of(element, status.getSuspiciousness(f));
        //if(!p.getRight().isNaN()){
            result.add(p);
        //}
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

    public void printFLResults(int top, CoverageCollection cc){
        Pair<Integer, Integer> l = maxLengthOfName(top);
        int classLength = l.getLeft();
        int methodLength = l.getRight();

        String header = "|      | RANK |" +
                StringUtils.repeat(' ', classLength - "CLASS NAME".length()) + " CLASS NAME " +
                "|" + StringUtils.repeat(' ', methodLength - "METHOD NAME".length()) + " METHOD NAME " +
                "| SUSP SCORE ||  EP  |  EF  |  NP  |  NF  |";
        String partition = StringUtils.repeat('=', header.length());

        System.out.println("[  SBFL RANKING  ]");
        System.out.println(partition);
        System.out.println(header);
        System.out.println(partition);
        int previousRank = 1;
        for(int i = 0; i < min(top, getSize()); i++){
            MutablePair<String, Double> element = result.get(i);
            //同率を考慮する
            int rank = 0;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(isSameScore(element, result.get(i-1))){
                    rank = previousRank;
                }
                else {
                    rank = i+1;
                }
            }

            String colorBegin = "";
            String coloerEnd = "";
            String className = element.getLeft().split("#")[0];
            String methodName = element.getLeft();
            SbflStatus stat = cc.getCoverageOfTarget(className, Granularity.METHOD).get(methodName);
            if(highlightMethods.contains(element.getLeft())){
                colorBegin = "\u001b[00;41m";
                coloerEnd = "\u001b[00m";
            }
            System.out.println(colorBegin + "| " + String.format("%3d ", i + 1) + " | " + String.format("%3d ", rank) + " | " +
                    StringUtils.leftPad(element.getLeft().split("#")[0], classLength) + " | " +
                    StringUtils.leftPad(element.getLeft().split("#")[1], methodLength) + " | " +
                    String.format("  %.4f  ", element.getRight()) +
                    " || " + StringUtils.leftPad(String.valueOf(stat.ep), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(stat.ef), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(stat.np), 4) +
                    " | " + StringUtils.leftPad(String.valueOf(stat.nf), 4) +
                    " |" + coloerEnd);
            previousRank = rank;
        }
        System.out.println(partition);
        System.out.println();
    }


    public void printFLResults(int top){
        Pair<Integer, Integer> l = maxLengthOfName(top);
        int classLength = l.getLeft();
        int methodLength = l.getRight();

        String header = "|      | RANK |" +
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
            MutablePair<String, Double> element = result.get(i);
            //同率を考慮する
            int rank = 0;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(isSameScore(element, result.get(i-1))){
                    rank = previousRank;
                }
                else {
                    rank = i+1;
                }
            }

            String colorBegin = "";
            String coloerEnd = "";
            if(highlightMethods.contains(element.getLeft())){
                colorBegin = "\u001b[00;41m";
                coloerEnd = "\u001b[00m";
            }
            System.out.println(colorBegin + "| " + String.format("%3d ", i + 1) + " | " + String.format("%3d ", rank) + " | " +
                    StringUtils.leftPad(element.getLeft().split("#")[0], classLength) + " | " +
                    StringUtils.leftPad(element.getLeft().split("#")[1], methodLength) + " | " +
                    String.format("  %.4f  ", element.getRight()) + " |" + coloerEnd);
            previousRank = rank;
        }
        System.out.println(partition);
        System.out.println();
    }

    public String getElementAtPlace(int place){
        if(!rankValidCheck(place)) return "";
        return result.get(place - 1).getLeft();
    }

    //同率も考慮した絶対順位
    public double getRankOfElement(String elementName){
        if(!isElementExist(elementName)) {
            System.err.println(elementName + " is not exist.");
            return -1;
        }

        int rankTieIgnored = 0;
        MutablePair<String, Double> target = searchElement(elementName);
        for(int i = 0; i < getSize(); i++){
            if(compare(target, result.get(i)) < 0) {
                rankTieIgnored++;
            }
            else {
                break;
            }
        }

        int numOfTie = getNumOfTie(elementName);
        return rankTieIgnored + (double) (numOfTie + 1) / 2;
    }

    public int getNumOfTie(String elementName){
        MutablePair<String, Double> target = searchElement(elementName);
        int numOfTie = 0;
        for(int i = 0; i < getSize(); i++){
            if(compare(target, result.get(i)) == 0) numOfTie++;
        }
        return numOfTie;
    }

    public boolean rankValidCheck(int rank){
        if(rank > getSize()) {
            System.err.println("Set valid rank.");
            return false;
        }
        return true;
    }

    public double getSuspicious(String targetElementName){
        if(!isElementExist(targetElementName)) {
            System.err.println(targetElementName + " is not exist.");
            return -1.0;
        }
        MutablePair<String, Double> element = searchElement(targetElementName);
        return element.getRight();
    }

    public void setSuspicious(String targetElementName, double suspicious){
        if(!isElementExist(targetElementName)) {
            System.err.println(targetElementName + " is not exist.");
            return;
        }
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

    public boolean isTop(String targetElementName){
        MutablePair<String, Double> e = searchElement(targetElementName);
        return isSameScore(e, result.get(0));
    }

    private Pair<Integer, Integer> maxLengthOfName(int top){
        int classLength = 0;
        int methodLength = 0;


        for(int i = 0; i < top; i++){
            MutablePair<String, Double> e = result.get(i);
            classLength = Math.max(classLength, e.getLeft().split("#")[0].length());
            methodLength = Math.max(methodLength, e.getLeft().split("#")[1].length());
        }

        return Pair.of(classLength, methodLength);
    }

    //小数点以下4桁までで比較
    private boolean isSameScore(MutablePair<String, Double> e1, MutablePair<String, Double> e2){
        return String.format("%.4f", e1.getRight()).equals(String.format("%.4f", e2.getRight()));
    }

    private int compare(MutablePair<String, Double> e1, MutablePair<String, Double> e2){
        if(isSameScore(e1, e2)) return 0;
        return e1.getRight().compareTo(e2.getRight());
    }

    public Set<String> getHighlightMethods() {
        return highlightMethods;
    }

    public void setHighlightMethods(Set<String> highlightMethods) {
        this.highlightMethods = highlightMethods;
    }
}
