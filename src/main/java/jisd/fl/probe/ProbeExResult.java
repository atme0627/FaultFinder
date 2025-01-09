package jisd.fl.probe;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.util.FileUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

public class ProbeExResult implements Serializable {
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

    public Set<String> markingMethods(){
        Set<String> marking = new HashSet<>();
        for(Element e : per) {
            marking.add(e.methodName);
        }
        return marking;
    }

    public double probeExSuspWeight(String methodName, ToDoubleBiFunction<Integer, Integer> f){
        double suspScore = 0;
        List<Element> elements = searchElementByMethod(methodName);
        for(Element e : elements){
            suspScore += f.applyAsDouble(e.depth, e.countInLine);
        }
        return suspScore;
    }

    public void sort(){
        per.sort(Element::compareTo);
    }

    public void print(){
        print(System.out);
    }

    public void print(PrintStream out){
        sort();
        for(Element e : per){
            out.println(e);
        }
    }

    public void save(String dir, String fileName){
        String covFileName = dir + "/" + fileName + ".probeEx";
        FileUtil.createDirectory(dir);
        FileUtil.initFile(dir, fileName + ".txt");

        File data = new File(covFileName);

        try (PrintStream resultOut = new PrintStream(dir + "/" + fileName + ".txt")) {
            data.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(covFileName);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.flush();
            objectOutputStream.close();

            print(resultOut);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ProbeExResult load(String dir, String fileName){
        String covFileName = dir + "/" + fileName + ".probeEx";

        try {
            FileInputStream fileInputStream = new FileInputStream(covFileName);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            ProbeExResult per = (ProbeExResult) objectInputStream.readObject();
            objectInputStream.close();
            return per;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static class Element implements Comparable<Element>, Serializable{
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
