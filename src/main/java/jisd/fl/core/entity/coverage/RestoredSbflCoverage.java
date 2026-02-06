package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CSV から復元した SBFL カバレッジデータを保持するクラス。
 */
public class RestoredSbflCoverage implements SbflCoverageProvider {

    private final int totalPass;
    private final int totalFail;
    private final List<LineCoverageEntry> lineEntries;

    public RestoredSbflCoverage(int totalPass, int totalFail, List<LineCoverageEntry> lineEntries) {
        this.totalPass = totalPass;
        this.totalFail = totalFail;
        this.lineEntries = List.copyOf(lineEntries);
    }

    public int totalPass() {
        return totalPass;
    }

    public int totalFail() {
        return totalFail;
    }

    @Override
    public Stream<LineCoverageEntry> lineCoverageEntries(boolean hideZeroElements) {
        Stream<LineCoverageEntry> s = lineEntries.stream();
        if (!hideZeroElements) return s;
        return s.filter(e -> e.counts().ep() + e.counts().ef() > 0);
    }

    @Override
    public Stream<MethodCoverageEntry> methodCoverageEntries(boolean hideZeroElements) {
        Map<MethodElementName, SbflCounts> aggregated = new LinkedHashMap<>();

        for (LineCoverageEntry entry : lineEntries) {
            MethodElementName method = entry.e().methodElementName;
            SbflCounts counts = entry.counts();

            aggregated.merge(method, counts, (existing, incoming) -> new SbflCounts(
                    Math.max(existing.ep(), incoming.ep()),
                    Math.max(existing.ef(), incoming.ef()),
                    Math.min(existing.np(), incoming.np()),
                    Math.min(existing.nf(), incoming.nf())
            ));
        }

        Stream<MethodCoverageEntry> s = aggregated.entrySet().stream()
                .map(e -> new MethodCoverageEntry(e.getKey(), e.getValue()));

        if (!hideZeroElements) return s;
        return s.filter(e -> e.counts().ep() + e.counts().ef() > 0);
    }

    @Override
    public Stream<ClassCoverageEntry> classCoverageEntries() {
        Map<ClassElementName, SbflCounts> aggregated = new LinkedHashMap<>();

        for (LineCoverageEntry entry : lineEntries) {
            ClassElementName className = entry.e().methodElementName.classElementName;
            SbflCounts counts = entry.counts();

            aggregated.merge(className, counts, (existing, incoming) -> new SbflCounts(
                    Math.max(existing.ep(), incoming.ep()),
                    Math.max(existing.ef(), incoming.ef()),
                    Math.min(existing.np(), incoming.np()),
                    Math.min(existing.nf(), incoming.nf())
            ));
        }

        return aggregated.entrySet().stream()
                .map(e -> new ClassCoverageEntry(e.getKey(), e.getValue()));
    }
}
