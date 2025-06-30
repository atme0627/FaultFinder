package jisd.fl.report;

import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * スコア更新処理の結果を保持し、整形してコンソールに出力する責務を持つクラス。
 * FaultFinderから表示ロジックを分離するために作成されました。
 */
public class ScoreUpdateReport {

    private final List<ChangeEntry> changes = new ArrayList<>();
    private final String operationName;

    /**
     * コンストラクタ
     * @param operationName "REMOVE", "SUSP" など、レポートのタイトルとして表示する操作名。
     */
    public ScoreUpdateReport(String operationName) {
        this.operationName = operationName;
    }

    /**
     * スコアの変更を記録します。
     * @param method   対象のメソッド名
     * @param oldScore 変更前のスコア
     * @param newScore 変更後のスコア
     */
    public void recordChange(String method, Double oldScore, Double newScore) {
        changes.add(new ChangeEntry(method, oldScore, newScore));
    }

    /**
     * 記録されたすべてのスコア変更をコンソールに整形して出力します。
     */
    public void print() {
        if (changes.isEmpty()) {
            return;
        }
        // NOTE: 元のIblResult.print()のロジックをそのままここに移動します。
        // 短縮クラス名・メソッド名の計算
        List<String> shortClassNames = new ArrayList<>();
        List<String> shortMethodNames = new ArrayList<>();
        for (ChangeEntry entry : changes) {
            String longClassName = entry.method.split("#")[0];
            String longMethodName = entry.method;

            // クラス名を短縮 (例: org.apache.commons.math3.optim -> o.a.c.m.optim)
            StringBuilder shortClassName = new StringBuilder();
            String[] packages = longClassName.split("\\.");
            for (int j = 0; j < packages.length - 2; j++) {
                shortClassName.append(packages[j].charAt(0)).append(".");
            }
            shortClassName.append(packages[packages.length - 2]).append(".").append(packages[packages.length - 1]);

            // メソッド名に開始行を追加
            Map<String, Pair<Integer, Integer>> rangeOfMethods;
            try {
                rangeOfMethods = StaticAnalyzer.getRangeOfAllMethods(new CodeElementName(longClassName));
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            int startLineOfMethod = rangeOfMethods.get(longMethodName).getLeft();
            String methodName = longMethodName.split("#")[1].split("\\(")[0];
            String shortMethodName = String.format("%s(...) line: %4d", methodName, startLineOfMethod);

            shortClassNames.add(shortClassName.toString());
            shortMethodNames.add(shortMethodName);
        }

        // 表示幅の計算
        int classLength = shortClassNames.stream().map(String::length).max(Integer::compareTo).orElse(10);
        int methodLength = shortMethodNames.stream().map(String::length).max(Integer::compareTo).orElse(20);

        // ヘッダーの生成
        String header = String.format("| %-" + classLength + "s | %-" + methodLength + "s | %-18s |", "CLASS NAME", "METHOD NAME", "OLD -> NEW");
        String partition = StringUtils.repeat("=", header.length());

        System.out.println("[  " + operationName + "  ]");
        System.out.println(partition);
        System.out.println(header);
        System.out.println(partition);

        // 内容の出力
        for (int i = 0; i < changes.size(); i++) {
            ChangeEntry e = changes.get(i);
            String row = String.format("| %-" + classLength + "s | %-" + methodLength + "s | %6.4f -> %6.4f |",
                    shortClassNames.get(i), shortMethodNames.get(i), e.oldScore, e.newScore);
            System.out.println(row);
        }
        System.out.println(partition);
        System.out.println();
    }

    /**
     * 1つのスコア変更を表す内部データクラス。
     */
    private static class ChangeEntry {
        final String method;
        final Double oldScore;
        final Double newScore;

        ChangeEntry(String method, Double oldScore, Double newScore) {
            this.method = method;
            this.oldScore = oldScore;
            this.newScore = newScore;
        }
    }
}