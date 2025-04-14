package jisd.fl.util.analyze;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import jisd.fl.util.PropertyLoader;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static jisd.fl.util.analyze.StaticAnalyzer.getClassNames;

public class CodeElement {
    @NotNull
    final public String packageName;
    @NotBlank
    final public String className;
    final public String methodSignature;

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
            methodSignature = fullyQualifiedName.split("#")[1];
        }
        else {
            fqClassName = fullyQualifiedName;
            methodSignature = null;
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

    public CodeElement(ClassOrInterfaceDeclaration cd){
        CompilationUnit unit = cd.findAncestor(CompilationUnit.class).orElseThrow();
        this.packageName = JavaParserUtil.getPackageName(unit);
        this.className = cd.getNameAsString();
        this.methodSignature = null;
    }

    public CodeElement(MethodDeclaration md){
        CompilationUnit unit = md.findAncestor(CompilationUnit.class).orElseThrow();
        this.packageName = JavaParserUtil.getPackageName(unit);
        this.className = JavaParserUtil.getParentOfMethod(md).getNameAsString();
        this.methodSignature = md.getSignature().toString();
    }

    @Override
    public boolean equals(Object obj){
        if(obj == null) return false;
        if(!(obj instanceof CodeElement)) return false;
        if(this.getFullyQualifiedMethodName()
                .equals(((CodeElement) obj).getFullyQualifiedMethodName())) return true;
        return false;
    }

    @Override
    public int hashCode(){
        return this.getFullyQualifiedMethodName().hashCode();
    }

    @Override
    public String toString(){
        return this.methodSignature != null ? this.getFullyQualifiedMethodName() : this.getFullyQualifiedClassName();
    }

    public String getFullyQualifiedClassName(){
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    public String getFullyQualifiedMethodName(){
        return packageName.isEmpty() ? className + "#" + methodSignature : packageName + "." + className + "#" + methodSignature;
    }

    public String getShortClassName(){
        return this.className;
    }

    //signature含まない
    public String getShortMethodName(){
       return this.methodSignature.split("\\(")[0];
    }

    public static Optional<CodeElement> generateFromSimpleClassName(String className){
        return generateFromSimpleClassName(className, Paths.get(PropertyLoader.getProperty("targetSrcPath")));
    }

    public static Optional<CodeElement> generateFromSimpleClassName(String className, Path targetSrcPath) {
        List<CodeElement> candidates = StaticAnalyzer.getClassNames(targetSrcPath)
                .stream()
                .filter(cn -> cn.substring(cn.lastIndexOf(".") + 1).equals(className))
                .map(CodeElement::new)
                .collect(Collectors.toList());

        //候補がない場合、複数候補がある場合はfail
        if(candidates.isEmpty())  return Optional.empty();
        if(candidates.size() > 1) return Optional.empty();
        return Optional.of(candidates.get(0));
    }

    public Path getFilePath(){
        Path p = getFilePath(false);
        if(Files.exists(p)) return p;
        return getFilePath(true);
    }

    public Path getFilePath(boolean isTest){
        String dir = isTest ? PropertyLoader.getProperty("testSrcDir") : PropertyLoader.getProperty("targetSrcDir");
        return Paths.get(dir + "/" + packageName.replace('.', '/'), className + ".java");
    }

    public boolean isConstructor(){
        return this.className.equals(getShortMethodName());
    }
}
