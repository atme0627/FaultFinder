package jisd.fl.util;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

public class MethodCallGraph {
    Map<String, Pair<Set<String>, Set<String>>> graph = new HashMap<>();

    void setElement(String callerMethodName, String calleeMethodName){
        //再帰呼び出しは除外
        if(callerMethodName.equals(calleeMethodName)){
            return;
        }

        //親 -> 子
        if(graph.containsKey(callerMethodName)){
            graph.get(callerMethodName).getRight().add(calleeMethodName);
        }
        else {
            Set<String> parent = new HashSet<>();
            Set<String> child = new HashSet<>();
            child.add(calleeMethodName);
            graph.put(callerMethodName, Pair.of(parent, child));
        }

        //子 -> 親
        if(graph.containsKey(calleeMethodName)){
            graph.get(calleeMethodName).getLeft().add(callerMethodName);
        }
        else {
            Set<String> parent = new HashSet<>();
            Set<String> child = new HashSet<>();
            parent.add(callerMethodName);
            graph.put(calleeMethodName, Pair.of(parent, child));
        }
    }

    public Set<String> getParent(String methodName){
        if(!graph.containsKey(methodName)){
            throw new RuntimeException("Method not found: " + methodName);
        }

        return graph.get(methodName).getLeft();
    }

    public Set<String> getChild(String methodName){
        if(!graph.containsKey(methodName)){
            throw new RuntimeException("Method not found: " + methodName);
        }

        return graph.get(methodName).getRight();
    }

    void printCallGraph(){
        graph.forEach((k, v)->{
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(k);
            System.out.println("    parent:");
            v.getLeft().forEach((parent)->{
            System.out.println("        " + parent);
            });
            System.out.println("    child:");
            v.getRight().forEach((child)->{
                System.out.println("        " + child);
            });
        });
    }
}
