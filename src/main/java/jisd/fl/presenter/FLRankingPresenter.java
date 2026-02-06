package jisd.fl.presenter;

import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.FLRankingElement;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

public class FLRankingPresenter {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[90m";
    private static final String TEAL = "\u001B[38;5;79m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String CYAN_DIM = "\u001B[38;5;66m";

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
        int count = min(top, ranking.getSize());
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        List<String> lineNumbers = new ArrayList<>();
        for(int i = 0; i < count; i++){
            CodeElementIdentifier<?> id = ranking.at(i).getCodeElementName();
            if(id instanceof LineElementName line) {
                shortClassNames.add(line.methodElementName.classElementName.compressedName());
                shortMethodNames.add(line.methodElementName.compressedMethodName());
                lineNumbers.add(String.valueOf(line.line));
            } else if(id instanceof MethodElementName method) {
                shortClassNames.add(method.classElementName.compressedName());
                shortMethodNames.add(method.compressedMethodName());
                lineNumbers.add("");
            } else if(id instanceof ClassElementName cls) {
                shortClassNames.add(cls.compressedName());
                shortMethodNames.add("---");
                lineNumbers.add("");
            } else {
                shortClassNames.add(id.compressedName());
                shortMethodNames.add("---");
                lineNumbers.add("");
            }
        }

        int classLen = Math.max("CLASS NAME".length(), shortClassNames.stream().mapToInt(String::length).max().orElse(0));
        int methodLen = Math.max("METHOD NAME".length(), shortMethodNames.stream().mapToInt(String::length).max().orElse(0));
        int lineLen = Math.max("LINE".length(), lineNumbers.stream().mapToInt(String::length).max().orElse(0));

        // 各列の書式: " %Nd " で統一（前後にスペース1つ）
        String V = DIM + "│" + RESET;
        // #列=4幅, RANK列=4幅
        String header = V + String.format("  %3s ", "#") + V + String.format(" %4s ", "RANK") + V
                + " " + StringUtils.padRight("CLASS NAME", classLen) + " " + V
                + " " + StringUtils.padLeft("METHOD NAME", methodLen) + " " + V
                + " " + StringUtils.padLeft("LINE", lineLen) + " " + V
                + " SUSP SCORE " + V;
        int visibleLen = 6 + 6 + (classLen + 2) + (methodLen + 2) + (lineLen + 2) + 12 + 8;
        String partition = DIM + "═".repeat(visibleLen) + RESET;

        System.out.println(BOLD + "[  SBFL RANKING  ]" + RESET);
        System.out.println(partition);
        System.out.println(BOLD + header + RESET);
        System.out.println(partition);

        int previousRank = 1;
        String prevClass = "";
        String prevMethod = "";
        for(int i = 0; i < count; i++){
            FLRankingElement element = ranking.at(i);
            //同率を考慮する
            int rank;
            if(i == 0) {
                rank = 1;
            } else {
                rank = (element.getSuspScore() == ranking.at(i - 1).getSuspScore()) ? previousRank : i + 1;
            }

            String cls = shortClassNames.get(i);
            String mth = shortMethodNames.get(i);
            // 同率かつ同一 class/method なら省略
            boolean sameTie = (rank == previousRank && i > 0);
            String dispClass = (sameTie && cls.equals(prevClass)) ? "" : cls;
            String dispMethod = (sameTie && cls.equals(prevClass) && mth.equals(prevMethod)) ? "" : mth;
            // RANK は同率の2行目以降を非表示
            String dispRank = (sameTie) ? "      " : CYAN_DIM + String.format(" %4d ", rank) + RESET;

            System.out.println(V + CYAN + String.format("  %3d ", i + 1) + RESET + V + dispRank + V
                    + " " + TEAL + StringUtils.padRight(dispClass, classLen) + RESET + " " + V
                    + " " + StringUtils.padLeft(dispMethod, methodLen) + " " + V
                    + " " + StringUtils.padLeft(lineNumbers.get(i), lineLen) + " " + V
                    + YELLOW + String.format(" %10.4f ", element.getSuspScore()) + RESET + V);

            previousRank = rank;
            prevClass = cls;
            prevMethod = mth;
        }
        System.out.println(partition);
        System.out.println();
    }
}
