package jisd.fl.util.analyze;

import javax.validation.constraints.NotBlank;
import java.util.Set;

import static jisd.fl.util.analyze.StaticAnalyzer.getClassNames;

public class CodeElement {
    @NotBlank
    final String packageName;
    @NotBlank
    final String className;
    final String methodName;

    //メソッド名はあってもなくても良い
    //ex1.) sample.demo
    //ex2.) sample.demo#sample
    //ex3.) sample.demo#sample(int)
    public CodeElement(String fullyQualifiedName){
        String fqClassName;
        final String packageName;
        final String className;
        final String methodName;

        //with method name
        if(fullyQualifiedName.contains("#")){
            fqClassName = fullyQualifiedName.split("#")[0];
            methodName = fullyQualifiedName.split("#")[1].split("\\(")[0];
        }
        else {
            fqClassName = fullyQualifiedName;
            methodName = null;
        }

        packageName = fqClassName.substring(0, fqClassName.lastIndexOf('.'));
        className = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);

        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
    }

    public CodeElement(String packageName, String className){
        this.packageName = packageName;
        this.className = className;
        this.methodName = null;
    }

    public CodeElement(String packageName, String className, @NotBlank String methodName){
        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
    }

    public String getFullyQualifiedClassName(){
        return packageName + "." + className;
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
