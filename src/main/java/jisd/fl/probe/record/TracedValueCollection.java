package jisd.fl.probe.record;

import java.util.*;

//valueInfoを受け取って、TracedValueに変換する責務を持つ
public abstract class TracedValueCollection {
    protected List<TracedValue> record;

    protected TracedValueCollection(){
    }

    protected TracedValueCollection(List<TracedValue> record){
        this.record = record;
    }

    public boolean isEmpty(){
        return record.isEmpty();
    }

    public void printAll(){
        record.sort(TracedValue::compareTo);
        for(TracedValue tv : record){
            System.out.println("     " + tv);
        }
    }

    public List<TracedValue> getAll(){
        return record;
    }

}