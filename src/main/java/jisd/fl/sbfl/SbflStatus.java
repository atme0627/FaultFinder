package jisd.fl.sbfl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.core.entity.sbfl.Formula;

public class SbflStatus {
    public int ep = 0;
    public int ef = 0;
    public int np = 0;
    public int nf = 0;

    public SbflStatus(boolean isExecuted, boolean isPassed){
        updateStatus(isExecuted, isPassed);
    }

    @Deprecated
    public static SbflStatus fromSbflCounts(SbflCounts c){
        return new SbflStatus(c.ep(), c.ef(), c.np(), c.nf());
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

    @Deprecated
    private SbflStatus(int ep, int ef, int np, int nf){
        this.ep = ep;
        this.ef = ef;
        this.np = np;
        this.nf = nf;
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

    @JsonIgnore
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
