package jisd.fl.infra.jacoco;

import jisd.fl.core.entity.coverage.ClassCoverageEntry;
import jisd.fl.core.entity.coverage.LineCoverageEntry;
import jisd.fl.core.entity.coverage.MethodCoverageEntry;
import jisd.fl.core.entity.coverage.SbflCoverageProvider;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.infra.javaparser.JavaParserLineElementNameResolverFactory;
import org.jacoco.core.analysis.IClassCoverage;

import java.nio.file.NoSuchFileException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ProjectSbflCoverage implements SbflCoverageProvider {

    public final Map<ClassElementName, ClassSbflCoverage> byClass = new LinkedHashMap<>();

    public void accept(IClassCoverage cc, boolean testPassed){
        ClassElementName e = toClassElementName(cc);
        ClassSbflCoverage cov = getOrCreate(e);
        cov.accept(cc, testPassed);
    }

    private static ClassElementName toClassElementName(IClassCoverage cc){
        String internalClassName = cc.getName();
        String fqcn = internalClassName.replace('/', '.');
        return new ClassElementName(fqcn);
    }

    private ClassSbflCoverage getOrCreate(ClassElementName e)  {
        return byClass.computeIfAbsent(e, c -> {
            try {
                return new ClassSbflCoverage(e, JavaParserLineElementNameResolverFactory.create(c));
            } catch (NoSuchFileException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public Stream<ClassSbflCoverage> coveredClasses() {
        return byClass.values().stream().filter(ClassSbflCoverage::hasAnyCoverage);
    }

    @Override
    public Stream<ClassCoverageEntry> classCoverageEntries(){
        return coveredClasses().map(cov -> new ClassCoverageEntry(cov.targetClass, cov.classCounts()));
    }

    @Override
    public Stream<MethodCoverageEntry> methodCoverageEntries(boolean hideZeroElements){
        return coveredClasses().flatMap(cov -> {
            Stream<MethodCoverageEntry> s =
                    cov.methodCoverageView().entries()
                            .map(en -> new MethodCoverageEntry(en.getKey(), en.getValue()));
            if(!hideZeroElements) return s;
            return s.filter(e -> e.counts().ep() + e.counts().ef() > 0);
        });
    }

    @Override
    public Stream<LineCoverageEntry> lineCoverageEntries(boolean hideZeroElements){
        return coveredClasses().flatMap(cov -> {
            Stream<LineCoverageEntry> s =
                    cov.lineCoverageView().entries()
                            .map(en -> new LineCoverageEntry(en.getKey(), en.getValue()));
            if(!hideZeroElements) return s;
            return s.filter(e -> e.counts().ep() + e.counts().ef() > 0);
        });
    }

    public void clear(){
        byClass.clear();
    }
}
