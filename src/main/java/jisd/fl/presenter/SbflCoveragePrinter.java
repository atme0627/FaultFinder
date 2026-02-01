package jisd.fl.presenter;

import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.core.entity.coverage.SbflCoverageView;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.util.ToolPaths;
import jisd.fl.infra.jacoco.ClassSbflCoverage;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.core.entity.sbfl.Granularity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class SbflCoveragePrinter {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[90m";
    private static final String TEAL = "\u001B[38;5;79m";
    private static final String YELLOW = "\u001B[33m";

    /**
     * Prints project coverage of given granularity to stdout.
     */
    public void print(ProjectSbflCoverage project, Granularity granularity) {
        System.out.println(BOLD + "[  SBFL COVERAGE (" + granularity + ")  ]" + RESET);

        project.coveredClasses().forEach(cov -> printClass(cov, granularity));

        System.out.println();
    }

    private void printClass(ClassSbflCoverage cov, Granularity granularity) {
        ClassElementName cls = cov.targetClass;

        System.out.println();
        System.out.println(BOLD + TEAL + cls.fullyQualifiedName() + RESET);
        System.out.println(DIM + "pass=" + cov.totalPass() + " fail=" + cov.totalFail() + RESET);

        switch (granularity) {
            case CLASS -> {
                SbflCounts cell = cov.classCounts();
                List<String> names = List.of(cls.compressedName());
                List<SbflCounts> counts = List.of(cell);
                printTable("ELEMENT", names, counts, Collections.emptyList());
            }
            case METHOD -> printView("METHOD", cov.methodCoverageView());
            case LINE -> printLineView(cls, cov.lineCoverageView());
        }
    }

    private <E extends CodeElementIdentifier<E>> void printView(String label, SbflCoverageView<E> view) {
        SortedMap<E, SbflCounts> m = new TreeMap<>();
        view.entries().forEach(en -> m.put(en.getKey(), en.getValue()));

        List<String> names = new ArrayList<>();
        List<SbflCounts> counts = new ArrayList<>();
        for (Map.Entry<E, SbflCounts> e : m.entrySet()) {
            names.add(compressedElement(e.getKey()));
            counts.add(e.getValue());
        }
        printTable(label, names, counts, Collections.emptyList());
    }

    private void printLineView(ClassElementName cls, SbflCoverageView<LineElementName> view) {
        SortedMap<LineElementName, SbflCounts> m = new TreeMap<>();
        view.entries().forEach(en -> m.put(en.getKey(), en.getValue()));

        List<String> sourceLines = readSourceLines(cls);
        SbflCounts zero = new SbflCounts(0, 0, 0, 0);

        // カバレッジ対象行の最小〜最大行を取得し、間の行も含める
        int minLine = m.firstKey().line;
        int maxLine = m.lastKey().line;
        // 最終行の後に閉じ括弧があれば含める
        while (maxLine < sourceLines.size()) {
            String next = sourceLines.get(maxLine).strip(); // maxLine+1 行目 (0-indexed: maxLine)
            if (next.equals("}") || next.isEmpty()) {
                maxLine++;
            } else {
                break;
            }
        }

        List<String> names = new ArrayList<>();
        List<SbflCounts> counts = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (int lineNum = minLine; lineNum <= maxLine; lineNum++) {
            String src = (lineNum > 0 && lineNum <= sourceLines.size())
                    ? sourceLines.get(lineNum - 1).strip() : "";
            names.add(String.valueOf(lineNum));
            sources.add(src);
            final int ln = lineNum;
            SbflCounts c = m.entrySet().stream()
                    .filter(e -> e.getKey().line == ln)
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(zero);
            counts.add(c);
        }
        printTable("LINE", names, counts, sources);
    }

    private void printTable(String label, List<String> names, List<SbflCounts> counts, List<String> sources) {
        boolean hasSrc = !sources.isEmpty() && sources.stream().anyMatch(s -> !s.isEmpty());

        int nameLen = Math.max(label.length(), names.stream().mapToInt(String::length).max().orElse(0));
        int numWidth = 4;

        String V = DIM + "│" + RESET;

        // ヘッダー構築（SOURCE列は最右端・罫線なし）
        StringBuilder hdr = new StringBuilder();
        hdr.append(V).append(" ").append(leftPad(label, nameLen)).append(" ");
        hdr.append(V).append(String.format(" %"+numWidth+"s ", "EP"));
        hdr.append(V).append(String.format(" %"+numWidth+"s ", "EF"));
        hdr.append(V).append(String.format(" %"+numWidth+"s ", "NP"));
        hdr.append(V).append(String.format(" %"+numWidth+"s ", "NF"));
        hdr.append(V);
        if (hasSrc) hdr.append(" SOURCE");

        // 罫線幅（SOURCE列を含めない）
        int visibleLen = (nameLen + 2) + 1 + (numWidth + 3) * 4 + 1;
        String partition = DIM + "═".repeat(visibleLen) + RESET;

        System.out.println(partition);
        System.out.println(BOLD + hdr + RESET);
        System.out.println(partition);

        for (int i = 0; i < names.size(); i++) {
            SbflCounts c = counts.get(i);
            StringBuilder row = new StringBuilder();
            row.append(V).append(" ").append(TEAL).append(leftPad(names.get(i), nameLen)).append(RESET).append(" ");
            row.append(V).append(fmtCount(c.ep(), numWidth));
            row.append(V).append(fmtCount(c.ef(), numWidth));
            row.append(V).append(fmtCount(c.np(), numWidth));
            row.append(V).append(fmtCount(c.nf(), numWidth));
            row.append(V);
            if (hasSrc) row.append(" ").append(DIM).append(sources.get(i)).append(RESET);
            System.out.println(row);
        }
        System.out.println(partition);
    }

    /** 0 なら空白、非 0 なら黄色数値 */
    private String fmtCount(int value, int width) {
        if (value == 0) {
            return " ".repeat(width + 2);
        }
        return YELLOW + String.format(" %" + width + "d ", value) + RESET;
    }

    private static List<String> readSourceLines(ClassElementName cls) {
        return ToolPaths.findSourceFilePath(cls)
                .map(SbflCoveragePrinter::readAllLinesSafe)
                .orElse(Collections.emptyList());
    }

    private static List<String> readAllLinesSafe(Path p) {
        try {
            return Files.readAllLines(p);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static String compressedElement(CodeElementIdentifier<?> id) {
        if (id instanceof LineElementName line) {
            return line.methodElementName.compressedMethodName() + " line: " + line.line;
        } else if (id instanceof MethodElementName method) {
            return method.compressedMethodName();
        } else {
            return id.compressedName();
        }
    }

    private static String leftPad(String str, int size) {
        return ("%-" + size + "s").formatted(str);
    }
}
