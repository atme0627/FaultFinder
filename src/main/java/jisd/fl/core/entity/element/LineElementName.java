package jisd.fl.core.entity.element;

import java.util.Objects;

public class LineElementName implements CodeElementIdentifier<LineElementName> {
    public final MethodElementName methodElementName;
    public final int line;

    public LineElementName(String fullyQualifiedName, int line){
        this.methodElementName = new MethodElementName(fullyQualifiedName);
        this.line = line;
    }

    public LineElementName(MethodElementName methodElementName, int line){
        this.methodElementName = methodElementName;
        this.line = line;
    }

    @Override
    public String fullyQualifiedClassName() {
        return methodElementName.fullyQualifiedClassName();
    }

    @Override
    public String fullyQualifiedName() {
        return methodElementName.fullyQualifiedName() + " line: " + line;
    }


    @Override
    public String compressedName() {
        return methodElementName.compressedName() + " line: " + line;
    }

    @Override
    public int compareTo(LineElementName o) {
        return (line != o.line) ? line - o.line : methodElementName.compareTo(o.methodElementName);
    }

    @Override
    public boolean isNeighbor(LineElementName other){
        return this.methodElementName.equals(other.methodElementName);
    }

    @Override
    public boolean equals(Object obj){
        if(obj == null) return false;
        if(!(obj instanceof LineElementName e)) return false;
        return this.methodElementName.equals(e.methodElementName) && this.line == e.line;
    }

    @Override
    public int hashCode(){
        return Objects.hash(methodElementName, line);
    }

    @Override
    public String toString(){
        return this.fullyQualifiedName();
    }
}
