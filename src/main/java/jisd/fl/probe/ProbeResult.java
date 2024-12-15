package jisd.fl.probe;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

public class ProbeResult {
    private Pair<Integer, Integer> lines;
    private String src;
    private String probeMethod;
    private String callerMethod;
    private Set<String> siblingMethods;

    public ProbeResult(){
    }

    public String getProbeMethod() {
        return probeMethod;
    }

    public String getCallerMethod() {
        return callerMethod;
    }

    public Set<String> getSiblingMethods() {
        return siblingMethods;
    }

    void setProbeMethod(String probeMethod) {
        this.probeMethod = probeMethod;
    }

    void setCallerMethod(String callerMethod) {
        this.callerMethod = callerMethod;
    }

    void setSiblingMethods(Set<String> siblingMethods) {
        this.siblingMethods = siblingMethods;
    }

    public Pair<Integer, Integer> getProbeLines() {
        return lines;
    }

    public void setLines(Pair<Integer, Integer> lines) {
        this.lines = lines;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }
}
