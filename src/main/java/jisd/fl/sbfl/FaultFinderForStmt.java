package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.report.ScoreUpdateReport;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Set;

public class FaultFinderForStmt extends FaultFinder{
    public FaultFinderForStmt(CoverageCollection covForTestSuite, Formula f) {
        super(covForTestSuite, Granularity.LINE, f);
    }

    @Override
    public void remove(int rank) {
        ScoreUpdateReport report = new ScoreUpdateReport("REMOVE");

        String targetStmt = sbflResult.getElementAtPlace(rank);
        String[] parts = targetStmt.split(" ---");
        String className = parts[0];
        int lineNumber = Integer.parseInt(parts[1].trim());

        System.out.println("[  REMOVE  ] " + targetStmt);
        report.recordChange(targetStmt, sbflResult.getSuspicious(targetStmt), 0.0);
        sbflResult.setSuspicious(targetStmt, 0);

        try {
            CodeElementName codeElementName = new CodeElementName(className);
            String targetMethodFqmn = StaticAnalyzer.getMethodNameFormLine(codeElementName, lineNumber);
            Map<String, Pair<Integer, Integer>> methodRanges = StaticAnalyzer.getRangeOfAllMethods(codeElementName);
            Pair<Integer, Integer> targetMethodRange = methodRanges.get(targetMethodFqmn);

            if (targetMethodRange == null) {
                System.err.println("Could not find method range for: " + targetMethodFqmn);
                return;
            }

            int methodStartLine = targetMethodRange.getLeft();
            int methodEndLine = targetMethodRange.getRight();

            Set<String> allElements = sbflResult.getAllElements();

            for (String element : allElements) {
                String[] elementParts = element.split(" ---");
                if (elementParts.length < 2) continue; // メソッド単位の要素などをスキップ

                String elementClassName = elementParts[0];
                int elementLineNumber = Integer.parseInt(elementParts[1].trim());

                // 同じクラスで、かつ対象メソッドの行範囲内にある要素のみを更新対象とする
                if (elementClassName.equals(className) &&
                    elementLineNumber >= methodStartLine &&
                    elementLineNumber <= methodEndLine &&
                    !element.equals(targetStmt)) {

                    double preScore = sbflResult.getSuspicious(element);
                    double newScore = preScore * getRemoveConst();
                    sbflResult.setSuspicious(element, newScore);
                    report.recordChange(element, preScore, newScore);
                }
            }

        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        report.print();
        sbflResult.sort();
        sbflResult.printFLResults(getRankingSize());
    }

    @Override
    public void susp(int rank) {
        ScoreUpdateReport report = new ScoreUpdateReport("SUSP");

        String targetStmt = sbflResult.getElementAtPlace(rank);
        String[] parts = targetStmt.split(" ---");
        String className = parts[0];
        int lineNumber = Integer.parseInt(parts[1].trim());

        System.out.println("[  SUSP  ] " + targetStmt);
        report.recordChange(targetStmt, sbflResult.getSuspicious(targetStmt), 0.0);
        sbflResult.setSuspicious(targetStmt, 0);

        try {
            CodeElementName codeElementName = new CodeElementName(className);
            String targetMethodFqmn = StaticAnalyzer.getMethodNameFormLine(codeElementName, lineNumber);
            Map<String, Pair<Integer, Integer>> methodRanges = StaticAnalyzer.getRangeOfAllMethods(codeElementName);
            Pair<Integer, Integer> targetMethodRange = methodRanges.get(targetMethodFqmn);

            if (targetMethodRange == null) {
                System.err.println("Could not find method range for: " + targetMethodFqmn);
                return;
            }

            int methodStartLine = targetMethodRange.getLeft();
            int methodEndLine = targetMethodRange.getRight();

            Set<String> allElements = sbflResult.getAllElements(); // SbflResultにgetAllElements()を追加する必要があります

            for (String element : allElements) {
                String[] elementParts = element.split(" ---");
                if (elementParts.length < 2) continue; // メソッド単位の要素などをスキップ

                String elementClassName = elementParts[0];
                int elementLineNumber = Integer.parseInt(elementParts[1].trim());

                // 同じクラスで、かつ対象メソッドの行範囲内にある要素のみを更新対象とする
                if (elementClassName.equals(className) &&
                    elementLineNumber >= methodStartLine &&
                    elementLineNumber <= methodEndLine &&
                    !element.equals(targetStmt)) {

                    double preScore = sbflResult.getSuspicious(element);
                    double newScore = preScore + getSuspConst();
                    sbflResult.setSuspicious(element, newScore);
                    report.recordChange(element, preScore, newScore);
                }
            }

        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }

        report.print();
        sbflResult.sort();
        sbflResult.printFLResults(getRankingSize());
    }
}
