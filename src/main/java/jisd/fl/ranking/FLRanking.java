package jisd.fl.ranking;

import jisd.fl.sbfl.Formula;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.analyze.CodeElementName;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class FLRanking {
    List<FLRankingElement> ranking = new ArrayList<>();
    final Granularity granularity;
    private Set<String> highlightMethods = new HashSet<>();

    public FLRanking(Granularity granularity){
        this.granularity = granularity;
    }

    public void setElement(CodeElementName element, SbflStatus status, Formula f){
        ranking.add(new FLRankingElement(element, status.getSuspiciousness(f)));
    }

    public void sort(){
        ranking.sort(FLRankingElement::compareTo);
    }

    public int getSize(){
        return ranking.size();
    }

    public void printFLResults() {
        printFLResults(getSize());
    }

    public void printFLResults(int top){
        sort();
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        for(int i = 0; i < min(top, getSize()); i++){
            shortClassNames.add(ranking.get(i).getCodeElementName().compressedClassName());
            shortMethodNames.add(ranking.get(i).getCodeElementName().compressedShortMethodName());
        }

        int classLength = shortClassNames.stream().map(String::length).max(Integer::compareTo).get();
        int methodLength = shortMethodNames.stream().map(String::length).max(Integer::compareTo).get();

        String header = "|      | RANK |" + leftPad(" CLASS NAME ", classLength) +
                "|" + rightPad(" METHOD NAME ", methodLength +2 )  +
                "| SUSP SCORE |";
        String partition = "=".repeat(header.length());

        System.out.println("[  SBFL RANKING  ]");
        System.out.println(partition);
        System.out.println(header);
        System.out.println(partition);
        int previousRank = 1;
        for(int i = 0; i < min(top, getSize()); i++){
            FLRankingElement element = ranking.get(i);
            //同率を考慮する
            int rank = 0;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(element.isSameScore(ranking.get(i-1))){
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
                    leftPad(shortClassNames.get(i), classLength) + " | " +
                    rightPad(shortMethodNames.get(i), methodLength) + " | " +
                    String.format("  %.4f  ", element.getSuspiciousnessScore()) + " |" + coloerEnd);
            previousRank = rank;
        }
        System.out.println(partition);
        System.out.println();
    }

    public Optional<FLRankingElement> getElementAtPlace(int place){
        return place <= ranking.size() ? Optional.of(ranking.get(place - 1)) : Optional.empty();
    }

    Optional<FLRankingElement> searchElement(CodeElementName target){
        for(FLRankingElement element : ranking){
            if(element.getCodeElementName().equals(target)) return Optional.of(element);
        }
        return Optional.empty();
    }


    public Set<FLRankingElement> getNeighborElements(FLRankingElement target){
        return ranking.stream()
                .filter(e -> e.isNeighbor(target) && !e.equals(target))
                .collect(Collectors.toSet());
    }

    //以下の文字列が含むもの以外を消去する。
    public void includeClassFilter(String pattern){
        ranking = ranking.stream().filter(re -> re.getCodeElementName().toString().contains(pattern)).collect(Collectors.toList());
    }

    /**
     * ランキングの要素を再計算
     * @param adjustments
     */
    public void adjustAll(List<ScoreAdjustment> adjustments){
        ScoreAdjuster.applyAll(this, adjustments);
    }

    public static String leftPad(String str, int size){
        return ("%-" + size + "s").formatted(str);
    }

    public static String rightPad(String str, int size){
        return ("%" + size + "s").formatted(str);
    }

}
