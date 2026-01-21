package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SbflCoverageView<E extends CodeElementIdentifier<E>> {
    private final ElementIDRegistry<E> ids;
    private final SbflCountsTable counts;
    private final int totalPass;
    private final int totalFail;

    public SbflCoverageView(ElementIDRegistry<E> ids, SbflCountsTable counts, int totalPass, int totalFail) {
        this.ids = ids;
        this.counts = counts;
        this.totalPass = totalPass;
        this.totalFail = totalFail;
    }

    public int totalFail(){return totalFail;}
    public int totalPass(){return totalPass;}

    public SbflCounts countsOf(E e){
         Optional<Integer> oid = ids.getIdIfPresent(e);
         if(oid.isEmpty()) return new SbflCounts(0, 0, totalPass, totalFail);
         int id = oid.get();
         int ep = counts.getEp(id);
         int ef = counts.getEf(id);
         int np = totalPass - ep;
         int nf = totalFail - ef;
         return new SbflCounts(ep, ef, np, nf);
    }

    public Stream<E> elements() {
        return IntStream.range(0, ids.size()).mapToObj(ids::elementOf);
    }

    public Stream<Map.Entry<E, SbflCounts>> entries(){
        return IntStream.range(0, ids.size()).mapToObj(id -> {
            E e = ids.elementOf(id);
            int ep = counts.getEp(id);
            int ef = counts.getEf(id);
            int np = totalPass - ep;
            int nf = totalFail - ef;
            return new AbstractMap.SimpleImmutableEntry<>(e, new SbflCounts(ep, ef, np, nf));
        });
    }
}
