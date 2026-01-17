package jisd.fl.core.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.core.util.PropertyLoader;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MethodElementName implements CodeElementIdentifier {
    @NotNull
    final public String packageName;
    @NotBlank
    final public String className;
    final public String methodSignature;

    @JsonCreator
    public MethodElementName(
            @JsonProperty("packageName") String packageName,
            @JsonProperty("className") String className,
            @JsonProperty("methodSignature") String methodSignature
    ) {
        this.packageName = packageName;
        this.className = className;
        this.methodSignature = normalizeMethodSignature(methodSignature);
    }

    //メソッド名は一応あってもなくても良い
    //ex1.) sample.demo
    //ex2.) sample.demo#sample
    //ex3.) sample.demo#sample(int)
    public MethodElementName(String fullyQualifiedName){
        String fqClassName;
        final String packageName;
        final String className;
        final String methodSignature;

        //with method name
        if(fullyQualifiedName.contains("#")){
            fqClassName = fullyQualifiedName.split("#")[0];
            methodSignature = normalizeMethodSignature(fullyQualifiedName.split("#")[1]);
        }
        else {
            fqClassName = fullyQualifiedName;
            methodSignature = "<NO METHOD DATA>()";
        }

        //with package
        if(fullyQualifiedName.contains(".")) {
            packageName = fqClassName.substring(0, fqClassName.lastIndexOf('.'));
            className = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);
        }
        else {
            packageName = "";
            className = fqClassName;
        }


        this.packageName = packageName;
        this.className = className.contains("$") ? className.split("\\$")[0] : className;
        this.methodSignature = methodSignature;
    }

    public MethodElementName(String packageName, String className){
        this.packageName = packageName;
        this.className = className;
        this.methodSignature =  "<NO METHOD DATA>()";
    }

    @Override
    public String getFullyQualifiedClassName(){
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    @Override
    public String getFullyQualifiedMethodName(){
        return packageName.isEmpty() ? className + "#" + methodSignature : packageName + "." + className + "#" + methodSignature;
    }

    @Override
    public String getShortClassName(){
        return this.className;
    }

    @Override
    //signature含まない
    public String getShortMethodName(){
       return this.methodSignature.split("\\(")[0];
    }

    @Override
    public Path getFilePath(){
        Path p = getFilePath(false);
        if(Files.exists(p)) return p;
        return getFilePath(true);
    }

    @Override
    public Path getFilePath(boolean isTest){
        String dir;
        dir = isTest ? PropertyLoader.getTestSrcDir().toString() : PropertyLoader.getTargetSrcDir().toString();
        return Paths.get(dir + "/" + packageName.replace('.', '/'), className + ".java");
    }

    @Override
    public String compressedShortMethodName() {
        String signature = getFullyQualifiedMethodName().substring(getFullyQualifiedMethodName().indexOf("(")).length() == 2 ? "()" : "(...)";
        return getShortMethodName() + signature;
    }

    public LineElementName toLineElementName(int line){
        return new LineElementName(this, line);
    }

    @Override
    public boolean isNeighbor(CodeElementIdentifier target){
        if(!(target instanceof MethodElementName methodElementTarget)) return false;
        return this.getFullyQualifiedClassName().equals(methodElementTarget.getFullyQualifiedClassName());
    }

    @Override
    public int compareTo(CodeElementIdentifier o) {
        return this.getFullyQualifiedMethodName().compareTo(o.getFullyQualifiedMethodName());
    }

    @Override
    public boolean equals(Object obj){
        if(obj == null) return false;
        if(!(obj instanceof MethodElementName)) return false;
        return this.getFullyQualifiedMethodName()
                .equals(((MethodElementName) obj).getFullyQualifiedMethodName());
    }

    @Override
    public int hashCode(){
        return this.getFullyQualifiedMethodName().hashCode();
    }

    @Override
    public String toString(){
        return this.getFullyQualifiedMethodName();
    }

    private String normalizeMethodSignature(String methodSignature){
        return EnhancedDebugger.normalizeMethodSignature(methodSignature);
    }
}
