package jisd.fl.ranking.report;

import jisd.fl.ranking.FLRanking;
import jisd.fl.ranking.FLRankingElement;

import java.util.ArrayList;
import java.util.List;

/**
 * スコア更新処理の結果を保持し、整形してコンソールに出力する責務を持つクラス。
 */
public class ScoreUpdateReport {

    private final List<ChangeEntry> changes = new ArrayList<>();

    public void recordChange(FLRankingElement changeTarget) {
        changes.add(new ChangeEntry(changeTarget, changeTarget.getSuspScore()));
    }

    /**
     * 記録されたすべてのスコア変更をコンソールに整形して出力します。
     */
    public void print() {
        if (changes.isEmpty()) {
            return;
        }
        // 短縮クラス名・メソッド名の計算
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortElementNames = new ArrayList<>();
        for (ChangeEntry entry : changes) {
            shortClassNames.add(entry.updatedElement.getCodeElementName().compressedClassName());
            shortElementNames.add(entry.updatedElement.getCodeElementName().compressedShortMethodName());
        }

        // 表示幅の計算
        int classLength = shortClassNames.stream().map(String::length).max(Integer::compareTo).orElse(10);
        int elementLength = shortElementNames.stream().map(String::length).max(Integer::compareTo).orElse(20);

        // ヘッダーの生成
        String header = String.format("| %s | %s | %-18s |", leftPad("CLASS NAME", classLength), rightPad("ELEMENT NAME", elementLength), "OLD -> NEW");
        String partition = "=".repeat(header.length());

        System.out.println(partition);
        System.out.println(header);
        System.out.println(partition);

        // 内容の出力
        for (int i = 0; i < changes.size(); i++) {
            ChangeEntry e = changes.get(i);
            String row = String.format("| %s | %s |  %6.4f -> %6.4f  |",
                    leftPad(shortClassNames.get(i), classLength), rightPad(shortElementNames.get(i), elementLength), e.oldScore(), e.newScore());
            System.out.println(row);
        }
        System.out.println(partition);
        System.out.println();
    }

    /**
     * 1つのスコア変更を表す内部データクラス。
     */
    private record ChangeEntry(FLRankingElement updatedElement, Double oldScore) {
        public double newScore(){
            return updatedElement.getSuspScore();
    }

    }

    public static String leftPad(String str, int size){
        return ("%-" + size + "s").formatted(str);
    }

    public static String rightPad(String str, int size) {
        return ("%" + size + "s").formatted(str);
    }
}