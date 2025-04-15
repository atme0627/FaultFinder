package jisd.fl.probe.record;

import jisd.debug.Location;

import java.time.LocalDateTime;

public class ProbeInfo implements Comparable<ProbeInfo>{
    public LocalDateTime createAt;
    public Location loc;
    public String variableName;
    public String value;
    int arrayIndex = -1;

    public ProbeInfo(LocalDateTime createAt,
                     Location loc,
                     String variableName,
                     String value){
        this.createAt = createAt;
        this.loc = loc;
        this.variableName = variableName;
        this.value = value;
    }

    @Override
    public int compareTo(ProbeInfo o) {
        return createAt.compareTo(o.createAt);
    }

    @Override
    public String toString(){
        return   "[CreateAt] " + createAt +
                " [Variable] " + variableName +
                " [Line] " + loc.getLineNumber() +
                " [value] " + value;
    }
}
