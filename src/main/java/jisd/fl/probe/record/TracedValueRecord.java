package jisd.fl.probe.record;

import jisd.debug.Location;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.*;

public class TracedValueRecord {
    public TracedValueRecord() {};
    Map<String, List<TracedValue>> piCollection = new HashMap<>();
    public void addElements(List<TracedValue> pis){
        for(TracedValue pi : pis){
            if(piCollection.containsKey(pi.variableName)){
                piCollection.get(pi.variableName).add(pi);
            }
            else {
                List<TracedValue> newPis = new ArrayList<>();
                newPis.add(pi);
                piCollection.put(pi.variableName, newPis);
            }
        }
    }

    public List<TracedValue> getPis(String key){
        //配列の場合[0]がついていないものも一緒に返す
        if(key.contains("[")){
            List<TracedValue> pis = new ArrayList<>();
            pis.addAll(piCollection.get(key));
            List<TracedValue> tmp = piCollection.get(key.split("\\[")[0]);
            if(tmp != null) pis.addAll(tmp);
            pis.sort(TracedValue::compareTo);
            return pis;
        }
        return piCollection.get(key);
    }

    public boolean isEmpty(){
        return piCollection.isEmpty();
    }

    public void sort(){
        for(List<TracedValue> pis : piCollection.values()){
            pis.sort(TracedValue::compareTo);
        }
    }
    public Map<String, String> getValuesAtSameTime(LocalDateTime createAt){
        Map<String, String> pis = new HashMap<>();
        for(List<TracedValue> l : piCollection.values()){
            for(TracedValue pi : l){
                if(pi.createAt.equals(createAt)) {
                    pis.put(pi.variableName, pi.value);
                }
            }
        }
        return pis;
    }

    public Map<String, String> getValuesFromLines(Pair<Integer, Integer> lines){
        Map<String, String> pis = new HashMap<>();
        for(List<TracedValue> l : piCollection.values()){
            for(TracedValue pi : l){
                for(int i = lines.getLeft(); i <= lines.getRight(); i++) {
                    if (pi.loc.getLineNumber() == i) {
                        pis.put(pi.variableName, pi.value);
                    }
                }
            }
        }
        return pis;
    }

    //値の宣言行など、実行はされているが値が定義されていない行も
    //実行されていることを認識するために、定義されていない行の値は"not defined"として埋める。
    public void considerNotDefinedVariable(){
        Set<Pair<LocalDateTime, Integer>> executedLines = new HashSet<>();
        for(List<TracedValue> pis : piCollection.values()){
            for(TracedValue pi : pis){
                executedLines.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
            }
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
                if(getPis(variableName.split("\\[")[0]) != null) {
                    for (TracedValue pi : getPis(variableName.split("\\[")[0])) {
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
                    if(getPis(variableName + "[0]") != null) {
                        for (TracedValue pi : getPis(variableName + "[0]")) {
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

    public void print(String key){
        List<TracedValue> pis = getPis(key);
        if(pis == null) throw new RuntimeException("key " + key + " is not exist.");
        for(TracedValue pi : pis) {
            System.out.println("    >> " + pi);
        }
    }

    public void printAll(){
        for(String key : piCollection.keySet()){
            print(key);
        }
    }

    public void clear(){
        for(List<TracedValue> l : piCollection.values()){
            l = null;
        }
    }
}