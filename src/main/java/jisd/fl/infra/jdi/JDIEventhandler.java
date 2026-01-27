package jisd.fl.infra.jdi;

import com.sun.jdi.event.Event;
import jisd.probej.VirtualMachine;

public interface JDIEventhandler<T extends Event> {
    void handle(VirtualMachine vm, T event);
}
