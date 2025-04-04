package jisd.fl.util.analyze;

import javax.validation.constraints.NotBlank;

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

}
