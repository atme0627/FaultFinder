package jisd.fl.probe;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class MethodCollection {
    List<Pair<Integer, String>> mc = new ArrayList<>();

    public boolean isEmpty(){
        return mc.isEmpty();
    }
    public MethodCollection(){};

    public void addElement(Pair<Integer, String> e){
        mc.add(e);
    }

    public int getLine(int index){
        return mc.get(index).getLeft();
    }

    public String getMethod(int index){
        return mc.get(index).getRight();
    }

    public Set<String> searchMethodsFromLine(int line){
        Set<String> methods = new HashSet<>();
        for(Pair<Integer, String> e : mc){
            if(e.getLeft() == line) methods.add(e.getRight());
        }
        return methods;
    }

    public Pair<Integer, String> getElement(int index){
        return mc.get(index);
    }

    public Set<String> getAllMethods(){
        Set<String> allMethods = new HashSet<>();
        for(Pair<Integer, String> e : mc){
            allMethods.add(e.getRight());
        }
        return allMethods;
    }

    public void print(){
        for(Pair<Integer, String> e : mc){
            System.out.println("line: " + e.getLeft() + " Method: " + e.getRight());
        }
    }
}
