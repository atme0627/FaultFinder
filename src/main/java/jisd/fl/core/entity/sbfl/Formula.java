package jisd.fl.core.entity.sbfl;

import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.sbfl.SbflStatus;

public enum Formula {
    TARAnTULA {
        @Override
        public double calc(SbflCounts counts){
            double ep = counts.ep();
            double ef = counts.ef();
            double np = counts.np();
            double nf = counts.nf();

            return (ef / (ef + nf)) / ((ef / (ef + nf)) + (ep / (ep + np)));
        }
    },
    AMPLe {
        public double calc(SbflCounts counts){
            double ep = counts.ep();
            double ef = counts.ef();
            double np = counts.np();
            double nf = counts.nf();

            return Math.abs((ef / (nf + ef)) - (ep / (np + ep)));
        }
    },
    OCHIAI {
        public double calc(SbflCounts counts){
            double ep = counts.ep();
            double ef = counts.ef();
            double nf = counts.nf();

            double result = ef / Math.sqrt((ef + nf) * (ef + ep));
            return Double.isNaN(result) ? 0 : result;
        }
    },
    JACCARD {
        public double calc(SbflCounts counts){
            double ep = counts.ep();
            double ef = counts.ef();
            double nf = counts.nf();

            return ef / (ef + nf + ep);
        }
    };

    public abstract double calc(SbflCounts counts);
}
