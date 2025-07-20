package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.LineElementName;
import jisd.fl.util.analyze.MethodElementName;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class SbflResult {
    List<ResultElement> result = new ArrayList<>();
    final Granularity granularity;
    private Set<String> highlightMethods = new HashSet<>();

    public SbflResult(Granularity granularity){
        this.granularity = granularity;
    }

    public void setElement(CodeElementName element, SbflStatus status, Formula f){
        result.add(new ResultElement(element, status.getSuspiciousness(f)));
    }

    public void sort(){
        result.sort(ResultElement::compareTo);
    }

    public int getSize(){
        return result.size();
    }

    public void printFLResults() {
        printFLResults(getSize());
    }

    //ランキングの短縮版
    public void printFLResults(int top, CoverageCollection cc){
        sort();
        List<String> shortClassNames = new ArrayList<>();
       List<String> shortMethodNames = new ArrayList<>();
        for(int i = 0; i < min(top, getSize()); i++){
            shortClassNames.add(result.get(i).e.compressedClassName());
            shortMethodNames.add(result.get(i).e.compressedShortMethodName());
        }

        int classLength = shortClassNames.stream().map(String::length).max(Integer::compareTo).get();
        int methodLength = shortMethodNames.stream().map(String::length).max(Integer::compareTo).get();

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
            ResultElement element = result.get(i);
            //同率を考慮する
            int rank = 0;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(element.isSameScore(result.get(i-1))){
                    rank = previousRank;
                }
                else {
                    rank = i+1;
                }
            }

            String colorBegin = "";
            String coloerEnd = "";
            String className = element.e().getFullyQualifiedClassName();
            SbflStatus stat = cc.getCoverageOfTarget(className, Granularity.METHOD).get(element.e);
            if(highlightMethods.contains(element.toString())){
                colorBegin = "\u001b[00;41m";
                coloerEnd = "\u001b[00m";
            }
            System.out.println(colorBegin + "| " + String.format("%3d ", i + 1) + " | " + String.format("%3d ", rank) + " | " +
                    StringUtils.leftPad(shortClassNames.get(i), classLength) + " | " +
                    StringUtils.leftPad(shortMethodNames.get(i), methodLength) + " | " +
                    String.format("  %.4f  ", element.sbflScore()) +
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
        sort();
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        for(int i = 0; i < min(top, getSize()); i++){
            shortClassNames.add(result.get(i).e.compressedClassName());
            shortMethodNames.add(result.get(i).e.compressedShortMethodName());
        }

        int classLength = shortClassNames.stream().map(String::length).max(Integer::compareTo).get();
        int methodLength = shortMethodNames.stream().map(String::length).max(Integer::compareTo).get();

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
            ResultElement element = result.get(i);
            //同率を考慮する
            int rank = 0;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(element.isSameScore(result.get(i-1))){
                    rank = previousRank;
                }
                else {
                    rank = i+1;
                }
            }

            String colorBegin = "";
            String coloerEnd = "";
            if(highlightMethods.contains(element.toString())){
                colorBegin = "\u001b[00;41m";
                coloerEnd = "\u001b[00m";
            }
            System.out.println(colorBegin + "| " + String.format("%3d ", i + 1) + " | " + String.format("%3d ", rank) + " | " +
                    StringUtils.leftPad(shortClassNames.get(i), classLength) + " | " +
                    StringUtils.leftPad(shortMethodNames.get(i), methodLength) + " | " +
                    String.format("  %.4f  ", element.sbflScore()) + " |" + coloerEnd);
            previousRank = rank;
        }
        System.out.println(partition);
        System.out.println();
    }

    public String getElementAtPlace(int place){
        if(!rankValidCheck(place)) return "";
        return result.get(place - 1).toString();
    }

    //同率も考慮した絶対順位
    public double getRankOfElement(String elementName){
        if(!isElementExist(elementName)) {
            System.err.println(elementName + " is not exist.");
            return -1;
        }

        int rankTieIgnored = 0;
        ResultElement target = searchElement(elementName);
        for(int i = 0; i < getSize(); i++){
            if(target.compareTo(result.get(i)) < 0) {
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
        ResultElement target = searchElement(elementName);
        int numOfTie = 0;
        for(int i = 0; i < getSize(); i++){
            if(target.compareTo(result.get(i)) == 0) numOfTie++;
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
        ResultElement element = searchElement(targetElementName);
        return element.sbflScore();
    }

    public void setSuspicious(String targetElementName, double suspicious){
        if(!isElementExist(targetElementName)) {
            System.err.println(targetElementName + " is not exist.");
            return;
        }
        ResultElement oldElement = searchElement(targetElementName);
        ResultElement newElement = new ResultElement(oldElement.e(), suspicious);

        result.remove(oldElement);
        result.add(newElement);
    }

    private ResultElement searchElement(String targetElementName){
        for(ResultElement element : result){
            if(element.toString().equals(targetElementName)) return element;
        }
        System.err.println("Element not found. name: " + targetElementName);
        return null;
    }

    public boolean isElementExist(String targetElementName){
        for(ResultElement element : result){
            if(element.e().equals(targetElementName)) return true;
        }
        return false;
    }

    public boolean isTop(String targetElementName){
        ResultElement e = searchElement(targetElementName);
        return e.isSameScore(result.get(0));
    }

    public Set<String> getAllElements() {
        return result.stream()
                .map(ResultElement::e)
                .map(CodeElementName::toString)
                .collect(Collectors.toSet());
    }

    public void setHighlightMethods(Set<String> highlightMethods) {
        this.highlightMethods = highlightMethods;
    }

    record ResultElement(CodeElementName e, double sbflScore) implements Comparable<ResultElement> {
        @Override
        public int compareTo(ResultElement o) {
            return isSameScore(o) ? e.compareTo(o.e) : -Double.compare(this.sbflScore, o.sbflScore);
        }

        //小数点以下4桁までで比較
        private boolean isSameScore(ResultElement e){
            return String.format("%.4f", this.sbflScore).equals(String.format("%.4f", e.sbflScore));
        }
    }
}
