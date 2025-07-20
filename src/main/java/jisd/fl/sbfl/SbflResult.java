package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.analyze.CodeElementName;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static jisd.fl.util.analyze.StaticAnalyzer.getRangeOfAllMethods;

public class SbflResult {
    List<ResultElement> result = new ArrayList<>();
    final Granularity granularity;
    private Set<String> highlightMethods = new HashSet<>();

    public SbflResult(Granularity granularity){
        this.granularity = granularity;
    }

    public void setElement(String element, SbflStatus status, Formula f){
        CodeElement e;
        if(element.contains("---")){
            e = new CodeElement(new CodeElementName(element.split("---")[0]), Integer.parseInt(element.split("---")[1]));
        }
        else {
            e = new CodeElement(new CodeElementName(element));
        }

        ResultElement r = new ResultElement(e, status.getSuspiciousness(f));
        result.add(r);
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
          String longClassName = result.get(i).e().getClassName();
          String longMethodName = result.get(i).e().getMethodName();


          StringBuilder shortClassName = new StringBuilder();
          StringBuilder shortMethodName = new StringBuilder();

          String[] packages = longClassName.split("\\.");
          for(int j = 0; j < packages.length - 2; j++){
              shortClassName.append(packages[j].charAt(0));
              shortClassName.append(".");
          }
          shortClassName.append(packages[packages.length - 2]);
          shortClassName.append(".");
          shortClassName.append(packages[packages.length - 1]);

          shortMethodName.append(longMethodName.split("#")[1].split("\\(")[0]);
          shortMethodName.append("(...) line: ");
          shortMethodName.append(result.get(i).e().line);

            shortClassNames.add(shortClassName.toString());
            shortMethodNames.add(shortMethodName.toString());
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
            String className = element.e().getClassName();
            String methodName = element.e().getShortMethodName();
            SbflStatus stat = cc.getCoverageOfTarget(className, Granularity.METHOD).get(methodName);
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
            String longClassName = result.get(i).e().getClassName();
            String longMethodName = result.get(i).e.getMethodName();

            StringBuilder shortClassName = new StringBuilder();
            StringBuilder shortMethodName = new StringBuilder();

            String[] packages = longClassName.split("\\.");
            for(int j = 0; j < packages.length - 2; j++){
                shortClassName.append(packages[j].charAt(0));
                shortClassName.append(".");
            }
            shortClassName.append(packages[packages.length - 2]);
            shortClassName.append(".");
            shortClassName.append(packages[packages.length - 1]);

            shortMethodName.append(longMethodName.split("#")[1].split("\\(")[0]);
            shortMethodName.append("(...) line: ");
            shortMethodName.append(result.get(i).e().line);

            shortClassNames.add(shortClassName.toString());
            shortMethodNames.add(shortMethodName.toString());
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
                .map(CodeElement::toString)
                .collect(Collectors.toSet());
    }

    public void setHighlightMethods(Set<String> highlightMethods) {
        this.highlightMethods = highlightMethods;
    }

    record ResultElement(CodeElement e, double sbflScore) implements Comparable<ResultElement> {
        @Override
        public int compareTo(ResultElement o) {
            return isSameScore(o) ? Integer.compare(this.e.line, o.e.line) : -Double.compare(this.sbflScore, o.sbflScore);
        }

        //小数点以下4桁までで比較
        private boolean isSameScore(ResultElement e){
            return String.format("%.4f", this.sbflScore).equals(String.format("%.4f", e.sbflScore));
        }
    }

    record CodeElement(CodeElementName e, int line){
        public CodeElement(CodeElementName e){
            this(e, -1);
        }

        @Override
        public String toString(){
            if(line == -1){
                return e.getFullyQualifiedMethodName();
            }
            else {
                return e.getFullyQualifiedMethodName() + " : " + line;
            }
        }

        public String getMethodName(){
            return e.getFullyQualifiedMethodName();
        }

        public String getShortMethodName(){
            return e.getShortMethodName();
        }

        public String getClassName(){
            return e.getFullyQualifiedClassName();
        }
    }
}
