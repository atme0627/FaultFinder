package jisd.fl.core.entity;
import java.time.LocalDateTime;

//TODO: これを継承した配列バージョンのクラスを作る
public class TracedValue implements Comparable<TracedValue>{
    public final LocalDateTime createAt;
    public final int lineNumber;
    public final String variableName;
    public final String value;
    public final boolean isReference;
    public final boolean isField;
    public long objectID = 0;

    public TracedValue(LocalDateTime createAt,
                       String variableName,
                       String value,
                       int lineNumber){
        this.createAt = createAt;
        this.lineNumber = lineNumber;
        this.variableName = variableName.contains("this.") ? variableName.split("\\.")[1] : variableName;
        this.value = value;
        this.isReference = false;
        this.isField = variableName.contains("this.");
    }

    public TracedValue(LocalDateTime createAt,
                       String variableName,
                       String value,
                       int lineNumber,
                       boolean isReference){

        this.createAt = createAt;
        this.lineNumber = lineNumber;
        this.variableName = variableName.contains("this.") ? variableName.split("\\.")[1] : variableName;
        this.value = value;
        this.isReference = isReference;
        this.isField = variableName.contains("this.");
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
