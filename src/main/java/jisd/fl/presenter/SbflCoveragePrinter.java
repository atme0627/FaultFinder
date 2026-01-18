package jisd.fl.presenter;

import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.core.entity.coverage.SbflCoverageView;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.infra.jacoco.ClassSbflCoverage;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.sbfl.coverage.Granularity;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class SbflCoveragePrinter {

    /**
     * Prints project coverage of given granularity to stdout.
     */
    public void print(ProjectSbflCoverage project, Granularity granularity) {
        System.out.println("=== SBFL COVERAGE (" + granularity + ") ===");

        project.coveredClasses().forEach(cov -> printClass(cov, granularity));

        System.out.println("=== END ===");
    }

    private void printClass(ClassSbflCoverage cov, Granularity granularity) {
        ClassElementName cls = cov.targetClass;

        System.out.println();
        System.out.println("[CLASS] " + cls.fullyQualifiedName());
        System.out.println("Totals: pass=" + cov.totalPass() + " fail=" + cov.totalFail());

        switch (granularity) {
            case CLASS -> {
                SbflCounts cell = cov.classCounts();
                System.out.println("| ELEMENT | EP | EF | NP | NF |");
                System.out.println("|--------|---:|---:|---:|---:|");
                System.out.println("| " + cls.compressedName() + " | " +
                        cell.ep() + " | " + cell.ef() + " | " + cell.np() + " | " + cell.nf() + " |");
            }
            case METHOD -> printView("METHOD", cov.methodCoverageView());
            case LINE -> printView("LINE", cov.lineCoverageView());
        }
    }

    private <E extends CodeElementIdentifier<E>> void printView(String label, SbflCoverageView<E> view) {
        SortedMap<E, SbflCounts> m = new TreeMap<>();
        view.entries().forEach(en -> m.put(en.getKey(), en.getValue()));

        System.out.println("| " + label + " | EP | EF | NP | NF |");
        System.out.println("|--------|---:|---:|---:|---:|");

        for (Map.Entry<E, SbflCounts> e : m.entrySet()) {
            SbflCounts c = e.getValue();
            System.out.println("| " + e.getKey() + " | " +
                    c.ep() + " | " + c.ef() + " | " + c.np() + " | " + c.nf() + " |");
        }
    }
}
