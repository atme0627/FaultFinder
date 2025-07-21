package jisd.fl.util.analyze;

import java.nio.file.Path;

public class LineElementName implements CodeElementName {
    private final MethodElementName methodElementName;
    private final int line;

    public LineElementName(String fullyQualifiedName, int line){
        this.methodElementName = new MethodElementName(fullyQualifiedName);
        this.line = line;
    }

    LineElementName(MethodElementName methodElementName, int line){
        this.methodElementName = methodElementName;
        this.line = line;
    }

    public int getLine(){
        return line;
    }

    @Override
    public String getFullyQualifiedClassName() {
        return methodElementName.getFullyQualifiedClassName();
    }

    @Override
    public String getFullyQualifiedMethodName() {
        return methodElementName.getFullyQualifiedMethodName();
    }

    @Override
    public String getShortClassName() {
        return methodElementName.getShortClassName();
    }

    @Override
    public String getShortMethodName() {
        return methodElementName.getShortMethodName();
    }

    @Override
    public Path getFilePath() {
        return methodElementName.getFilePath();
    }

    @Override
    public Path getFilePath(boolean isTest) {
        return methodElementName.getFilePath(isTest);
    }

    @Override
    public String compressedShortMethodName() {
        return methodElementName.compressedShortMethodName() + " line: " + line;
    }

    @Override
    public boolean isNeighbor(CodeElementName target){
        if(!(target instanceof LineElementName lineElementTarget)) return false;
        return this.methodElementName.equals(lineElementTarget.methodElementName);
    }

    @Override
    public int compareTo(CodeElementName o) {
        if(o instanceof LineElementName) {
            if (this.getFullyQualifiedMethodName().equals(o.getFullyQualifiedMethodName())) {
                return Integer.compare(this.line, ((LineElementName) o).line);
            }
        }
        return this.getFullyQualifiedMethodName().compareTo(o.getFullyQualifiedMethodName());
    }

    @Override
    public boolean equals(Object obj){
        if(obj == null) return false;
        if(!(obj instanceof LineElementName)) return false;
        return this.getFullyQualifiedMethodName()
                .equals(((LineElementName) obj).getFullyQualifiedMethodName())
                && this.line == ((LineElementName) obj).line;
    }

    @Override
    public int hashCode(){
        return this.getFullyQualifiedMethodName().hashCode() + line;
    }

    @Override
    public String toString(){
        return this.getFullyQualifiedMethodName() + " line: " + line;
    }
}
