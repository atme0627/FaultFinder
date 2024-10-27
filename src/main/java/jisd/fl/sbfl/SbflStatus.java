package jisd.fl.sbfl;

public class SbflStatus {
    private int ep = 0;
    private int ef = 0;
    private int np = 0;
    private int nf = 0;

    public SbflStatus(boolean isExecuted, boolean isPassed){
        updateStatus(isExecuted, isPassed);
    }

    private SbflStatus(){
    }

    public void updateStatus(boolean isExecuted, boolean isPassed){
        if(isExecuted){
            if(isPassed){
                ep = getEp() + 1;
            }
            else {
                ef = getEf() + 1;
            }
        }
        else {
            if(isPassed){
                np = getNp() + 1;
            }
            else {
                nf = getNf() + 1;
            }
        }
    }

    public double getSuspiciousness(Formula formula){
        return formula.calc(this);
    }

    public SbflStatus combine(SbflStatus status){
        SbflStatus newStatus = new SbflStatus();
        newStatus.ep = this.ep + status.ep;
        newStatus.ef = this.ef + status.ef;
        newStatus.np = this.np + status.np;
        newStatus.nf = this.nf + status.nf;
        return newStatus;
    }

    int getEp() {
        return ep;
    }

    int getEf() {
        return ef;
    }

    int getNp() {
        return np;
    }

    int getNf() {
        return nf;
    }

    @Override
    public String toString(){
            return Integer.toString(ep) + " " + Integer.toString(ef) +
                    " " + Integer.toString(np) + " " + Integer.toString(nf);
    }
}
