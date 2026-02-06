package jisd.fl.presenter;

import jisd.fl.core.entity.FLRankingElement;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * スコア更新処理の結果を保持し、整形してコンソールに出力する責務を持つクラス。
 */
public class ScoreUpdateReport {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[90m";
    private static final String TEAL = "\u001B[38;5;79m";
    private static final String YELLOW = "\u001B[33m";

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
        // 更新後のスコア（降順）、次に要素名でソート
        List<ChangeEntry> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator
                .comparingDouble((ChangeEntry e) -> e.newScore()).reversed()
                .thenComparing(e -> e.updatedElement.getCodeElementName().toString()));

        // 短縮クラス名・メソッド名・行番号の計算
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        List<String> lineNumbers = new ArrayList<>();
        for (ChangeEntry entry : sorted) {
            CodeElementIdentifier<?> id = entry.updatedElement.getCodeElementName();
            if (id instanceof LineElementName line) {
                shortClassNames.add(line.methodElementName.classElementName.compressedName());
                shortMethodNames.add(line.methodElementName.compressedMethodName());
                lineNumbers.add(String.valueOf(line.line));
            } else if (id instanceof MethodElementName method) {
                shortClassNames.add(method.classElementName.compressedName());
                shortMethodNames.add(method.compressedMethodName());
                lineNumbers.add("");
            } else if (id instanceof ClassElementName cls) {
                shortClassNames.add(cls.compressedName());
                shortMethodNames.add("---");
                lineNumbers.add("");
            } else {
                shortClassNames.add(id.compressedName());
                shortMethodNames.add("---");
                lineNumbers.add("");
            }
        }

        // 表示幅の計算
        int classLen = Math.max("CLASS NAME".length(), shortClassNames.stream().mapToInt(String::length).max().orElse(0));
        int methodLen = Math.max("METHOD NAME".length(), shortMethodNames.stream().mapToInt(String::length).max().orElse(0));
        int lineLen = Math.max("LINE".length(), lineNumbers.stream().mapToInt(String::length).max().orElse(0));

        // ヘッダーの生成
        String V = DIM + "│" + RESET;
        String header = V + " " + StringUtils.padRight("CLASS NAME", classLen) + " " + V
                + " " + StringUtils.padLeft("METHOD NAME", methodLen) + " " + V
                + " " + StringUtils.padLeft("LINE", lineLen) + " " + V
                + "    OLD -> NEW     " + V;
        int visibleLen = (classLen + 2) + (methodLen + 2) + (lineLen + 2) + 19 + 5;
        String partition = DIM + "═".repeat(visibleLen) + RESET;

        System.out.println(partition);
        System.out.println(BOLD + header + RESET);
        System.out.println(partition);

        // 内容の出力
        for (int i = 0; i < sorted.size(); i++) {
            ChangeEntry e = sorted.get(i);
            System.out.println(V + " " + TEAL + StringUtils.padRight(shortClassNames.get(i), classLen) + RESET + " " + V
                    + " " + StringUtils.padLeft(shortMethodNames.get(i), methodLen) + " " + V
                    + " " + StringUtils.padLeft(lineNumbers.get(i), lineLen) + " " + V
                    + YELLOW + String.format("  %6.4f -> %6.4f ", e.oldScore(), e.newScore()) + RESET + V);
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
}