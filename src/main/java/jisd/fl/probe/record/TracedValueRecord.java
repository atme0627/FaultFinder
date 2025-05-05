package jisd.fl.probe.record;

import jisd.debug.DebugResult;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TracedValueRecord {
    public TracedValueRecord() {};
    List<TracedValue> record = new ArrayList<>();

    public void addElements(List<TracedValue> data){
        record.addAll(data);
    }

    public List<TracedValue> filterByVariableName(String varName){
        List<TracedValue> result = record.stream()
                .filter(tv -> tv.variableName.equals(varName))
                .collect(Collectors.toList());

        //配列の場合[0]がついていないものも一緒に返す
        if(varName.contains("[")){
            record.stream()
                    .filter(tv -> tv.variableName.equals(varName.split("\\[")[0]))
                    .forEach(record::add);
        }
        result.sort(TracedValue::compareTo);
        return result;
    }

    //TODO: 返り値をなんとかする
    public Map<String, String> filterByCreateAt(LocalDateTime createAt){
        List<TracedValue> result =  record.stream()
                .filter(tv -> tv.createAt.equals(createAt))
                .collect(Collectors.toList());
        return result.stream().collect(Collectors.toMap(tv -> tv.variableName,tv ->  tv.createAt.toString()));
    }

    public boolean isEmpty(){
        return record.isEmpty();
    }

    public void sort(){
        record.sort(TracedValue::compareTo);
    }

    public List<String> getIncludedVariableNames(){
        return record.stream()
                .map(tv -> tv.variableName)
                .distinct()
                .collect(Collectors.toList());
    }

    public TracedValueRecord getInfoFromWatchPoints(List<Optional<Point>> watchPoints, VariableInfo variableInfo){
        //get Values from debugResult
        //実行されなかった行の情報は飛ばす。
        //実行されたがnullのものは含む。
        TracedValueRecord watchedValues = new TracedValueRecord();
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;
            p = op.get();
            HashMap<String, DebugResult> drs = p.getResults();
            watchedValues.addElements(getValuesFromDebugResults(variableInfo, drs));
        }


        if (watchedValues.isEmpty()) {
            throw new RuntimeException("there is not target value in watch point.");
        }
        return watchedValues;
    }

    public void print(String varName){
        for(TracedValue tv : filterByVariableName(varName)) {
            System.out.println("    >> " + tv);
        }
    }

    public void printAll(){
        for(TracedValue tv : record){
            System.out.println("    >> " + tv);
        }
    }

    //TODO: 必要ないかも
    public void clear(){
        record = null;
    }
}