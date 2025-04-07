package jisd.fl.util.analyze;

import javax.validation.constraints.NotBlank;
import java.util.Set;

import static jisd.fl.util.analyze.StaticAnalyzer.getClassNames;

public class CodeElement {
    @NotBlank
    final String packageName;
    @NotBlank
    final String className;
    final String methodSignature;

    //メソッド名はあってもなくても良い
    //ex1.) sample.demo
    //ex2.) sample.demo#sample
    //ex3.) sample.demo#sample(int)
    public CodeElement(String fullyQualifiedName){
        String fqClassName;
        final String packageName;
        final String className;
        final String methodSignature;

        //with method name
        if(fullyQualifiedName.contains("#")){
            fqClassName = fullyQualifiedName.split("#")[0];
            methodSignature = fullyQualifiedName.split("#")[1].split("\\(")[0];
        }
        else {
            fqClassName = fullyQualifiedName;
            methodSignature = null;
        }

        packageName = fqClassName.substring(0, fqClassName.lastIndexOf('.'));
        className = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);

        this.packageName = packageName;
        this.className = className;
        this.methodSignature = methodSignature;
    }

    public CodeElement(String packageName, String className){
        this.packageName = packageName;
        this.className = className;
        this.methodSignature = null;
    }

    public CodeElement(String packageName, String className, @NotBlank String methodSignature){
        this.packageName = packageName;
        this.className = className;
        this.methodSignature = methodSignature;
    }

    public String getFullyQualifiedClassName(){
        return packageName + "." + className;
    }

    public String getFullyQualifiedMethodName(){
        return packageName + "." + className + "#" + methodSignature;
    }

    public static CodeElement generateFullyQualifiedName(String className, String targetSrcDir){
        Set<String> classNames = getClassNames(targetSrcDir);
        for (String n : classNames) {
            String[] ns = n.split("\\.");
            if (ns[ns.length - 1].equals(className)) {
                return new CodeElement(n);
            }
        }
        throw new RuntimeException("Cannot find class " + className + " in Dir: " + targetSrcDir);
    }
}
