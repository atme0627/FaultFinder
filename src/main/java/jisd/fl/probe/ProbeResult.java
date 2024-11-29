package jisd.fl.probe;

import java.util.Set;

public class ProbeResult {
    private String probeMethod;
    private Set<String> callerMethods;
    private Set<String> siblingMethods;

    public ProbeResult(){
    }



    public String getProbeMethod() {
        return probeMethod;
    }

    public Set<String> getCallerMethods() {
        return callerMethods;
    }

    public Set<String> getSiblingMethods() {
        return siblingMethods;
    }

    void setProbeMethod(String probeMethod) {
        this.probeMethod = probeMethod;
    }

    void setCallerMethods(Set<String> callerMethods) {
        this.callerMethods = callerMethods;
    }

    void setSiblingMethods(Set<String> siblingMethods) {
        this.siblingMethods = siblingMethods;
    }
}
