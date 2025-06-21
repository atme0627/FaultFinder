package jisd.fl.probe.record;

import jisd.debug.Location;

import java.time.LocalDateTime;

//TODO: これを継承した配列バージョンのクラスを作る
public class TracedValue implements Comparable<TracedValue>{
    public LocalDateTime createAt;
    public Location loc;
    public String variableName;
    public String value;
    public long objectID = 0;
    public int timesOfExecution = 0;

    public TracedValue(LocalDateTime createAt,
                       Location loc,
                       String variableName,
                       String value){
        this.createAt = createAt;
        this.loc = loc;
        this.variableName = variableName;
        this.value = value;
    }

    @Override
    public int compareTo(TracedValue o) {
        return createAt.compareTo(o.createAt);
    }

    @Override
    public String toString(){
        return
                "[ObjectID] " + objectID +
                " [Location] " + loc.getClassName() + ":" + loc.getLineNumber() +
                " [Variable] " + variableName + " = " + value;
    }
}
