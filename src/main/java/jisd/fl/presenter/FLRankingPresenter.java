package jisd.fl.presenter;

import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.FLRankingElement;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

public class FLRankingPresenter {
    private final FLRanking ranking;
    public FLRankingPresenter(FLRanking ranking){
        this.ranking = ranking;
    }

    public void addClassFilter(String pattern){

    }
    public void print(){

    }

    public void printFLResults() {
        printFLResults(ranking.getSize());
    }

    public void printFLResults(int top){
        ranking.sort();
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        for(int i = 0; i < min(top, ranking.getSize()); i++){
            shortClassNames.add(ranking.at(i).getCodeElementName().compressedClassName());
            shortMethodNames.add(ranking.at(i).getCodeElementName().compressedShortMethodName());
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
        for(int i = 0; i < min(top, ranking.getSize()); i++){
            FLRankingElement element = ranking.at(i);
            //同率を考慮する
            int rank = 0;
            if(i == 0) {
                rank = i+1;
            }
            else {
                if(element.compareTo(ranking.at(i-1)) == 0){
                    rank = previousRank;
                }
                else {
                    rank = i+1;
                }
            }

            String colorBegin = "";
            String coloerEnd = "";
            System.out.println(colorBegin + "| " + String.format("%3d ", i + 1) + " | " + String.format("%3d ", rank) + " | " +
                    leftPad(shortClassNames.get(i), classLength) + " | " +
                    rightPad(shortMethodNames.get(i), methodLength) + " | " +
                    String.format("  %.4f  ", element.getSuspScore()) + " |" + coloerEnd);
            previousRank = rank;
        }
        System.out.println(partition);
        System.out.println();
    }

    public static String leftPad(String str, int size){
        return ("%-" + size + "s").formatted(str);
    }

    public static String rightPad(String str, int size){
        return ("%" + size + "s").formatted(str);
    }
}
