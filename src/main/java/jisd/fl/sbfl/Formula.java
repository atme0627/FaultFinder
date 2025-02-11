package jisd.fl.sbfl;

public enum Formula {
    TARANTULA {
        @Override
        double calc(SbflStatus status){
            double ep = status.getEp();
            double ef = status.getEf();
            double np = status.getNp();
            double nf = status.getNf();

            return (ef / (ef + nf)) / ((ef / (ef + nf)) + (ep / (ep + np)));
        }
    },
    AMPLE {
        double calc(SbflStatus status){
            double ep = status.getEp();
            double ef = status.getEf();
            double np = status.getNp();
            double nf = status.getNf();

            return Math.abs((ef / (nf + ef)) - (ep / (np + ep)));
        }
    },
    OCHIAI {
        double calc(SbflStatus status){
            double ep = status.getEp();
            double ef = status.getEf();
            double nf = status.getNf();

            double result = ef / Math.sqrt((ef + nf) * (ef + ep));
            return Double.isNaN(result) ? 0 : result;
        }
    },
    JACCARD {
        double calc(SbflStatus status){
            double ep = status.getEp();
            double ef = status.getEf();
            double nf = status.getNf();

            return ef / (ef + nf + ep);
        }
    };

    abstract double calc(SbflStatus status);
}
