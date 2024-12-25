package jisd.fl.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

public class ProbeExResult {
    List<Element> per;
    public ProbeExResult(){
        per = new ArrayList<>();
    }

    public void addElement(String methodName, int depth, int countInLine){
        per.add(new Element(methodName, depth, countInLine));
    }

    public void addAll(List<String> methods, int depth){
        if(methods.isEmpty()) return;
        int countInLine = methods.size();
        for(String m : methods){
            addElement(m, depth, countInLine);
        }
    }

    private List<Element> searchElementByMethod(String methodName){
        List<Element> elements = new ArrayList<>();
        for(Element e : per){
            if(e.methodName.equals(methodName)) elements.add(e);
        }

        return elements;
    }

    public double probeExSuspScore(String methodName, ToDoubleBiFunction<Integer, Integer> f){
        double suspScore = 0;
        List<Element> elements = searchElementByMethod(methodName);
        for(Element e : elements){
            suspScore += f.applyAsDouble(e.depth, e.depth);
        }
        return suspScore;
    }

    public void sort(){
        per.sort(Element::compareTo);
    }

    public void print(){
        sort();
        for(Element e : per){
            System.out.println(e);
        }
    }

    static class Element implements Comparable<Element>{
        String methodName;
        int depth;
        //同じprobeLine中で出現したメソッドの数
        //(同時に多く出現するほど疑いが弱くなるという仮定)
        int countInLine;

        public Element(String methodName, int depth, int countInLine){
            this.methodName = methodName;
            this.depth = depth;
            this.countInLine = countInLine;
        }

        @Override
        public int compareTo(Element o) {
            int c = this.methodName.compareTo(o.methodName);
            if(c != 0) return c;
            c = this.depth - o.depth;
            if(c != 0) return c;
            return this.countInLine - o.countInLine;
        }

        @Override
        public String toString(){
            return "[METHOD] " + methodName + "   [DEPTH] " + depth + "  [COUNT] " + countInLine;
        }


    }





}
