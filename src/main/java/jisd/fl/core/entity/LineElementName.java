package jisd.fl.core.entity;
import java.nio.file.Path;

public class LineElementName implements CodeElementIdentifier {
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

    public String getFullyQualifiedClassName() {
        return methodElementName.fullyQualifiedClassName();
    }

    @Override
    public String getFullyQualifiedMethodName() {
        return methodElementName.fullyQualifiedName();
    }

    public String getShortMethodName() {
        return methodElementName.shortMethodName();
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
    public boolean isNeighbor(CodeElementIdentifier target){
        if(!(target instanceof LineElementName lineElementTarget)) return false;
        return this.methodElementName.equals(lineElementTarget.methodElementName);
    }

    @Override
    public int compareTo(CodeElementIdentifier o) {
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

    public int getLine(){
        return line;
    }

    public String compressedClassName(){
        StringBuilder shortClassName = new StringBuilder();
        String[] packages = getFullyQualifiedClassName().split("\\.");
        for(int j = 0; j < packages.length; j++){
            String packageName = j < packages.length - 2 ? String.valueOf(packages[j].charAt(0)) : packages[j];
            shortClassName.append(packageName);
            if(j < packages.length - 1) shortClassName.append(".");
        }
        return shortClassName.toString();
    }
}
