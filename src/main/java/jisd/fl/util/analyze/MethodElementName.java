package jisd.fl.util.analyze;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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

public class MethodElementName implements CodeElementName{
    @NotNull
    final public String packageName;
    @NotBlank
    final public String className;
    final public String methodSignature;

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
            methodSignature = fullyQualifiedName.split("#")[1];
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
        this.className = className;
        this.methodSignature = methodSignature;
    }

    public MethodElementName(String packageName, String className){
        this.packageName = packageName;
        this.className = className;
        this.methodSignature =  "<NO METHOD DATA>()";
    }

    public MethodElementName(String packageName, String className, @NotBlank String methodSignature){
        this.packageName = packageName;
        this.className = className;
        this.methodSignature = methodSignature;
    }

    public MethodElementName(ClassOrInterfaceDeclaration cd){
        CompilationUnit unit = cd.findAncestor(CompilationUnit.class).orElseThrow();
        this.packageName = JavaParserUtil.getPackageName(unit);
        this.className = cd.getNameAsString();
        this.methodSignature =  "<NO METHOD DATA>()";
    }

    public MethodElementName(CallableDeclaration cd){
        CompilationUnit unit = (CompilationUnit) cd.findAncestor(CompilationUnit.class).orElseThrow();
        this.packageName = JavaParserUtil.getPackageName(unit);
        this.className = JavaParserUtil.getParentOfMethod(cd).getNameAsString();
        this.methodSignature = cd.getSignature().toString();
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
        String dir = isTest ? PropertyLoader.getProperty("testSrcDir") : PropertyLoader.getProperty("targetSrcDir");
        return Paths.get(dir + "/" + packageName.replace('.', '/'), className + ".java");
    }

    @Override
    public String compressedShortMethodName() {
        String signature = getFullyQualifiedMethodName().substring(getFullyQualifiedMethodName().indexOf("(")).length() == 2 ? "()" : "(...)";
        return getShortMethodName() + signature;
    }

    public static Optional<MethodElementName> generateFromSimpleClassName(String className){
        return generateFromSimpleClassName(className, Paths.get(PropertyLoader.getProperty("targetSrcPath")));
    }

    public static Optional<MethodElementName> generateFromSimpleClassName(String className, Path targetSrcPath) {
        List<MethodElementName> candidates = StaticAnalyzer.getClassNames(targetSrcPath)
                .stream()
                .filter(cn -> cn.substring(cn.lastIndexOf(".") + 1).equals(className))
                .map(MethodElementName::new)
                .collect(Collectors.toList());

        //候補がない場合、複数候補がある場合はfail
        if(candidates.isEmpty())  return Optional.empty();
        if(candidates.size() > 1) return Optional.empty();
        return Optional.of(candidates.get(0));
    }

    @Override
    public int compareTo(CodeElementName o) {
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
}
