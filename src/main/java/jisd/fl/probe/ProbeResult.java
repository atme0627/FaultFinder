package jisd.fl.probe;

import java.util.Set;

public class ProbeResult {
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
}
