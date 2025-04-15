package jisd.fl.probe.record;

import jisd.debug.Location;
import jisd.fl.probe.AbstractProbe;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.*;

public class ProbeInfoCollection {
    public ProbeInfoCollection() {};
    Map<String, List<AbstractProbe.ProbeInfo>> piCollection = new HashMap<>();
    public void addElements(List<AbstractProbe.ProbeInfo> pis){
        for(AbstractProbe.ProbeInfo pi : pis){
            if(piCollection.containsKey(pi.variableName)){
                piCollection.get(pi.variableName).add(pi);
            }
            else {
                List<AbstractProbe.ProbeInfo> newPis = new ArrayList<>();
                newPis.add(pi);
                piCollection.put(pi.variableName, newPis);
            }
        }
    }

    public List<AbstractProbe.ProbeInfo> getPis(String key){
        //配列の場合[0]がついていないものも一緒に返す
        if(key.contains("[")){
            List<AbstractProbe.ProbeInfo> pis = new ArrayList<>();
            pis.addAll(piCollection.get(key));
            List<AbstractProbe.ProbeInfo> tmp = piCollection.get(key.split("\\[")[0]);
            if(tmp != null) pis.addAll(tmp);
            pis.sort(AbstractProbe.ProbeInfo::compareTo);
            return pis;
        }
        return piCollection.get(key);
    }

    public boolean isEmpty(){
        return piCollection.isEmpty();
    }

    public void sort(){
        for(List<AbstractProbe.ProbeInfo> pis : piCollection.values()){
            pis.sort(AbstractProbe.ProbeInfo::compareTo);
        }
    }
    public Map<String, String> getValuesAtSameTime(LocalDateTime createAt){
        Map<String, String> pis = new HashMap<>();
        for(List<AbstractProbe.ProbeInfo> l : piCollection.values()){
            for(AbstractProbe.ProbeInfo pi : l){
                if(pi.createAt.equals(createAt)) {
                    pis.put(pi.variableName, pi.value);
                }
            }
        }
        return pis;
    }

    public Map<String, String> getValuesFromLines(Pair<Integer, Integer> lines){
        Map<String, String> pis = new HashMap<>();
        for(List<AbstractProbe.ProbeInfo> l : piCollection.values()){
            for(AbstractProbe.ProbeInfo pi : l){
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
        for(List<AbstractProbe.ProbeInfo> pis : piCollection.values()){
            for(AbstractProbe.ProbeInfo pi : pis){
                executedLines.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
            }
        }

        for(List<AbstractProbe.ProbeInfo> pis : piCollection.values()){
            String variableName = pis.get(0).variableName;
            Set<Pair<LocalDateTime, Integer>> executedLinesInThisPis = new HashSet<>();
            for(AbstractProbe.ProbeInfo pi : pis){
                executedLinesInThisPis.add(Pair.of(pi.createAt, pi.loc.getLineNumber()));
            }

            Set<Pair<LocalDateTime, Integer>> notExecutedLines = new HashSet<>();
            //変数が配列で、[]あり、なしのどちらかが存在するときは含めない
            //[]ありの場合
            if(variableName.contains("[")){
                // continue;
                Set<Pair<LocalDateTime, Integer>> executedLinesOfWithoutBracket = new HashSet<>();
                if(getPis(variableName.split("\\[")[0]) != null) {
                    for (AbstractProbe.ProbeInfo pi : getPis(variableName.split("\\[")[0])) {
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
                        for (AbstractProbe.ProbeInfo pi : getPis(variableName + "[0]")) {
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
                pis.add(new AbstractProbe.ProbeInfo(createAt, loc, variableName, "Not defined"));
            }
        }
    }

    public void print(String key){
        List<AbstractProbe.ProbeInfo> pis = getPis(key);
        if(pis == null) throw new RuntimeException("key " + key + " is not exist.");
        for(AbstractProbe.ProbeInfo pi : pis) {
            System.out.println("    >> " + pi);
        }
    }

    public void printAll(){
        for(String key : piCollection.keySet()){
            print(key);
        }
    }

    public void clear(){
        for(List<AbstractProbe. ProbeInfo> l : piCollection.values()){
            l = null;
        }
    }
}