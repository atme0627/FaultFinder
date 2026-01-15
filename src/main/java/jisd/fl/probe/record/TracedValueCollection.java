package jisd.fl.probe.record;

import java.util.*;

//valueInfoを受け取って、TracedValueに変換する責務を持つ
public abstract class TracedValueCollection {
    protected List<TracedValue> record;

    protected TracedValueCollection(List<TracedValue> record){
        this.record = record;
    }

    public boolean isEmpty(){
        return record.isEmpty();
    }

    public List<TracedValue> getAll(){
        return record;
    }

}