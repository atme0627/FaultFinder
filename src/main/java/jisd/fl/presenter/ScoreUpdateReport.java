package jisd.fl.presenter;

import jisd.fl.core.entity.FLRankingElement;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

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
        // 短縮クラス名・メソッド名・行番号の計算
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        List<String> lineNumbers = new ArrayList<>();
        for (ChangeEntry entry : changes) {
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
        String header = String.format("| %s | %s | %s | %-18s |",
                StringUtils.padRight("CLASS NAME", classLen), StringUtils.padLeft("METHOD NAME", methodLen),
                StringUtils.padLeft("LINE", lineLen), "OLD -> NEW");
        String partition = "=".repeat(header.length());

        System.out.println(partition);
        System.out.println(header);
        System.out.println(partition);

        // 内容の出力
        for (int i = 0; i < changes.size(); i++) {
            ChangeEntry e = changes.get(i);
            String row = String.format("| %s | %s | %s |  %6.4f -> %6.4f  |",
                    StringUtils.padRight(shortClassNames.get(i), classLen),
                    StringUtils.padLeft(shortMethodNames.get(i), methodLen),
                    StringUtils.padLeft(lineNumbers.get(i), lineLen),
                    e.oldScore(), e.newScore());
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
}