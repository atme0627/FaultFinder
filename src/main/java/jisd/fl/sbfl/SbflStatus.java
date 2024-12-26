package jisd.fl.sbfl;

import java.io.Serializable;

public class SbflStatus implements Serializable {
    public int ep = 0;
    public int ef = 0;
    public int np = 0;
    public int nf = 0;

    public SbflStatus(boolean isExecuted, boolean isPassed){
        updateStatus(isExecuted, isPassed);
    }

    public SbflStatus(boolean isPassed, int e, int n){
        if(isPassed){
            this.ep = e;
            this.np = n;
        }
        else {
           this.ef = e;
           this.nf = n;
        }
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

    public boolean isElementExecuted(){
     return (ep != 0) || (ef != 0);
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
