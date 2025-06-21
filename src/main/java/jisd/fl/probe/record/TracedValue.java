package jisd.fl.probe.record;

import jisd.debug.Location;

import java.time.LocalDateTime;

//TODO: これを継承した配列バージョンのクラスを作る
public class TracedValue implements Comparable<TracedValue>{
    public LocalDateTime createAt;
    public int lineNumber;
    public String variableName;
    public String value;
    public long objectID = 0;

    public TracedValue(LocalDateTime createAt,
                       String variableName,
                       String value, int lineNumber){
        this.createAt = createAt;
        this.lineNumber = lineNumber;
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
                " [Line] " + lineNumber +
                " [Variable] " + variableName + " == " + value;
    }
}
