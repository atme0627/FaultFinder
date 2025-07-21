package jisd.fl.sbfl;

import jisd.fl.coverage.Granularity;
import jisd.fl.util.analyze.CodeElementName;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class FLRanking {
    List<FLRankingElement> result = new ArrayList<>();
    final Granularity granularity;
    private Set<String> highlightMethods = new HashSet<>();

    public FLRanking(Granularity granularity){
        this.granularity = granularity;
    }

    public void setElement(CodeElementName element, SbflStatus status, Formula f){
        result.add(new FLRankingElement(element, status.getSuspiciousness(f)));
    }

    public void sort(){
        result.sort(FLRankingElement::compareTo);
    }

    public int getSize(){
        return result.size();
    }

    public void printFLResults() {
        printFLResults(getSize());
    }

    public void printFLResults(int top){
        sort();
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        for(int i = 0; i < min(top, getSize()); i++){
            shortClassNames.add(result.get(i).getCodeElementName().compressedClassName());
            shortMethodNames.add(result.get(i).getCodeElementName().compressedShortMethodName());
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
            FLRankingElement element = result.get(i);
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
                    String.format("  %.4f  ", element.getSuspiciousnessScore()) + " |" + coloerEnd);
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
        FLRankingElement target = searchElement(elementName).orElseThrow(() -> new RuntimeException(elementName + " is not exist."));

        int rankTieIgnored = 0;
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
        FLRankingElement target = searchElement(elementName).orElseThrow(() -> new RuntimeException(elementName + " is not exist."));
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
        Optional<FLRankingElement> element = searchElement(targetElementName);
        if(element.isPresent()){
            return element.get().getSuspiciousnessScore();
        }
        throw new RuntimeException(targetElementName + " is not exist.");
    }

    public void updateSuspiciousScore(String targetElementName, double suspicious){
        Optional<FLRankingElement> oldElement = searchElement(targetElementName);
        if(oldElement.isPresent()) {
            oldElement.get().updateSuspiciousnessScore(suspicious);
            return;
        }
        throw new RuntimeException(targetElementName + " is not exist.");
    }

    private Optional<FLRankingElement> searchElement(String targetElementName){
        for(FLRankingElement element : result){
            if(element.toString().equals(targetElementName)) return Optional.of(element);
        }
        return Optional.empty();
    }

    public boolean isElementExist(String targetElementName){
        for(FLRankingElement element : result){
            if(element.getCodeElementName().equals(targetElementName)) return true;
        }
        return false;
    }

    public boolean isTop(String targetElementName){
        Optional<FLRankingElement> element = searchElement(targetElementName);
        if(element.isPresent()){
            return element.get().isSameScore(result.get(0));
        }
        throw new RuntimeException(targetElementName + " is not exist.");
    }

    public Set<String> getAllElements() {
        return result.stream()
                .map(FLRankingElement::getCodeElementName)
                .map(CodeElementName::toString)
                .collect(Collectors.toSet());
    }

    public void setHighlightMethods(Set<String> highlightMethods) {
        this.highlightMethods = highlightMethods;
    }

}
