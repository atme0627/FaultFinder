package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jisd.fl.util.FileUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

@Deprecated
public class SimpleProbeResult implements Serializable {
    public List<Element> per;
    public SimpleProbeResult(){
        per = new ArrayList<>();
    }

    public void addElement(String methodName, int depth, int countInLine){
        per.add(new Element(methodName, depth, countInLine));
    }

    public void addElement(String methodName, int lineNumber, int depth, int countInLine){
        per.add(new Element(methodName, lineNumber, depth, countInLine));
    }

    private boolean exists(String method, int depth){
        for(Element e : per){
            if(e.methodName.equals(method) && e.depth == depth) return true;
        }
        return false;
    }

    public void addAll(List<String> methods, int depth){
        if(methods.isEmpty()) return;
        int countInLine = methods.size();
        for(String m : methods){
            if(!exists(m, depth)) addElement(m, depth, countInLine);
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

    public double probeExSuspScore(String methodName, ToDoubleBiFunction<Integer, Integer> f){
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
        generateJson(dir, fileName);
    }

    public void generateJson(String dir, String fileName){
        String outputFileName = fileName + "_probeEx.json";
        FileUtil.initFile(dir, fileName + ".txt");
        FileUtil.initFile(dir, outputFileName);

        try (PrintStream textOut = new PrintStream(dir + "/" + fileName + ".txt");
             PrintStream jsonOut = new PrintStream(dir + "/" + outputFileName);
             ) {
            print(textOut);
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            jsonOut.println(new ObjectMapper().writer(printer).writeValueAsString(this));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SimpleProbeResult loadJson(String dir){
        File f = new File(dir);
        try {
            return new ObjectMapper().readValue(f, SimpleProbeResult.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Element implements Comparable<Element>, Serializable{
        public final String methodName;
        public final int depth;
        public final int lineNumber;
        //同じprobeLine中で出現したメソッドの数
        //(同時に多く出現するほど疑いが弱くなるという仮定)
        public int countInLine;

        @JsonCreator
        public Element(){
            this(null, 0, 0, 0);
        }

        public Element(String methodName, int depth, int countInLine){
            this(methodName, 0, depth, countInLine);
        }

        public Element(String methodName, int lineNumber, int depth, int countInLine){
            this.methodName = methodName;
            this.lineNumber = lineNumber;
            this.depth = depth;
            this.countInLine = countInLine;
        }

        @Override
        public int compareTo(Element o) {
            int c = this.methodName.compareTo(o.methodName);
            if(c != 0) return c;
            c = this.depth - o.depth;
            if(c != 0) return c;
            c = this.countInLine - o.countInLine;
            if(c != 0) return c;
            return this.lineNumber - o.lineNumber;
        }

        @Override
        public String toString(){
            if(lineNumber == 0) {
                return "[METHOD] " + methodName + "   [DEPTH] " + depth;
            }
            return "[METHOD] " + methodName + "   [DEPTH] " + depth + "  [LINE] " + lineNumber;
        }
    }





}
