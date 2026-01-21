package jisd.fl.core.entity.element;
import java.util.Objects;

public class MethodElementName implements CodeElementIdentifier<MethodElementName> {
    public final ClassElementName classElementName;
    public final String methodSignature;

    //ex1.) sample.demo#sample
    //ex2.) sample.demo#sample(int)
    public MethodElementName(String fullyQualifiedName){
        String fqClassName;
        final String methodSignature;

        //with method name
        if(fullyQualifiedName.contains("#")){
            fqClassName = fullyQualifiedName.split("#")[0];
            methodSignature = normalizeMethodSignature(fullyQualifiedName.split("#")[1]);
        }
        else {
            throw new IllegalArgumentException("fullyQualifiedName must contain method name: " + fullyQualifiedName);
        }

        this.classElementName = new ClassElementName(fqClassName);
        this.methodSignature = methodSignature;
    }

    @Override
    public String fullyQualifiedClassName(){
        return classElementName.fullyQualifiedName();
    }

    @Override
    public String fullyQualifiedName(){
        return classElementName.fullyQualifiedName() + "#" + methodSignature;
    }

    @Override
    public String compressedName(){
        return classElementName.compressedName() + "#" + compressedMethodName();
    }

    public String compressedMethodName() {
        String signature = methodSignature.substring(methodSignature.indexOf("(")).length() == 2 ? "()" : "(...)";
        return shortMethodName() + signature;
    }

    //signature含まない
    public String shortMethodName(){
        return this.methodSignature.split("\\(")[0];
    }


    public LineElementName toLineElementName(int line){
        return new LineElementName(this, line);
    }


    @Override
    public int compareTo(MethodElementName e) {
        return (!classElementName.equals(e.classElementName)) ? classElementName.compareTo(e.classElementName) : methodSignature.compareTo(e.methodSignature);
    }

    @Override
    public boolean isNeighbor(MethodElementName other){
        return this.classElementName.equals(other.classElementName);
    }

    @Override
    public boolean equals(Object obj){
        if(obj == null) return false;
        if(!(obj instanceof MethodElementName e)) return false;
        return this.classElementName.equals(e.classElementName) && this.methodSignature.equals(e.methodSignature);
    }

    @Override
    public int hashCode(){
        return Objects.hash(classElementName, methodSignature);
    }

    @Override
    public String toString(){
        return this.fullyQualifiedName();
    }

    private static String normalizeMethodSignature(String methodSignature){
        String shortMethodName = methodSignature.split("\\(")[0];
        String[] args = methodSignature.substring(methodSignature.indexOf("(") + 1, methodSignature.indexOf(")")).split(",");
        if(args.length == 1 && args[0].isEmpty()) return methodSignature;
        StringBuilder sb = new StringBuilder();

        sb.append(shortMethodName);
        sb.append("(");
        for(String arg : args){
            arg = arg.trim();
            if(!arg.contains(".")) {
                sb.append(arg);
            }
            else {
                sb.append(arg.substring(arg.lastIndexOf(".") + 1));
            }
            sb.append(", ");
        }

        sb.delete(sb.length() -2, sb.length());
        sb.append(")");
        return sb.toString();
    }
}
