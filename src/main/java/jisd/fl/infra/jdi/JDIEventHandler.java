package jisd.fl.infra.jdi;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.Event;

public interface JDIEventHandler<T extends Event> {
    void handle(VirtualMachine vm, T event);
}
