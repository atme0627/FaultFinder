package jisd.fl.probe.record;

import jisd.debug.Location;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TracedValueRecord {
    public TracedValueRecord() {};
    Map<String, List<TracedValue>> piCollection = new HashMap<>();
    List<TracedValue> record = new ArrayList<>();

    public void addElements(List<TracedValue> data){
        record.addAll(data);
    }

    public List<TracedValue> filterByVariableName(String varName){
        List<TracedValue> result = record.stream()
                .filter(tv -> tv.value.equals(varName))
                .collect(Collectors.toList());

        //配列の場合[0]がついていないものも一緒に返す
        if(varName.contains("[")){
            record.stream()
                    .filter(tv -> tv.value.equals(varName.split("\\[")[0]))
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


    //値の宣言行など、実行はされているが値が定義されていない行も
    //実行されていることを認識するために、定義されていない行の値は"not defined"として埋める。
    public void considerNotDefinedVariable(){
        Set<Pair<LocalDateTime, Integer>> executedLines = new HashSet<>();
        for(TracedValue tv : record){
            executedLines.add(Pair.of(tv.createAt, tv.loc.getLineNumber()));
        }

        for(List<TracedValue> pis : piCollection.values()){
            String variableName = pis.get(0).variableName;
            Set<Pair<LocalDateTime, Integer>> executedLinesInThisPis = new HashSet<>();
            for(TracedValue pi : pis){
                executedLinesInThisPis.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
            }

            Set<Pair<LocalDateTime, Integer>> notExecutedLines = new HashSet<>();
            //変数が配列で、[]あり、なしのどちらかが存在するときは含めない
            //[]ありの場合
            if(variableName.contains("[")){
                // continue;
                Set<Pair<LocalDateTime, Integer>> executedLinesOfWithoutBracket = new HashSet<>();
                if(filterByVariableName(variableName.split("\\[")[0]) != null) {
                    for (TracedValue pi : filterByVariableName(variableName.split("\\[")[0])) {
                        executedLinesOfWithoutBracket.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
                    }
                }

                for(Pair<LocalDateTime, Integer> execline : executedLines){
                    if(!executedLinesInThisPis.contains(execline) &&
                            !executedLinesOfWithoutBracket.contains(execline)) notExecutedLines.add(execline);
                }
            }
            //[]なしの場合
            else {
                boolean isArray = false;
                for(String varName : piCollection.keySet()){
                    if(varName.contains("[") && varName.split("\\[")[0].equals(variableName)){
                        isArray = true;
                    }
                }
                if (isArray){
                    // continue;
                    Set<Pair<LocalDateTime, Integer>> executedLinesOfWithBracket = new HashSet<>();
                    if(filterByVariableName(variableName + "[0]") != null) {
                        for (TracedValue pi : filterByVariableName(variableName + "[0]")) {
                            executedLinesOfWithBracket.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
                        }
                    }

                    for(Pair<LocalDateTime, Integer> execline : executedLines){
                        if(!executedLinesInThisPis.contains(execline) &&
                                !executedLinesOfWithBracket.contains(execline)) notExecutedLines.add(execline);
                    }
                }
                else {
                    //配列でない場合
                    for (Pair<LocalDateTime, Integer> execline : executedLines) {
                        if (!executedLinesInThisPis.contains(execline)) notExecutedLines.add(execline);
                    }
                }
            }

            for(Pair<LocalDateTime, Integer> notExecline : notExecutedLines){
                LocalDateTime createAt = notExecline.getLeft();
                Location pisLoc = pis.get(0).loc;
                Location loc = new Location(pisLoc.getClassName(), pisLoc.getMethodName(), notExecline.getRight(), "No defined");
                pis.add(new TracedValue(createAt, loc, variableName, "Not defined"));
            }
        }
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