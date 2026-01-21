package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.sbfl.Formula;

public record SbflCounts(int ep, int ef, int np, int nf) {
    public double getSuspiciousness(Formula formula){
        return formula.calc(this);
    }
}
