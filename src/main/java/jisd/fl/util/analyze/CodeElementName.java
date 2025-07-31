package jisd.fl.util.analyze;

import java.nio.file.Path;

public interface CodeElementName extends Comparable<CodeElementName> {
    String getFullyQualifiedClassName();
    String getFullyQualifiedMethodName();
    String getShortClassName();
    //signature含まない
    String getShortMethodName();
    Path getFilePath();
    Path getFilePath(boolean isTest);

    String compressedShortMethodName();
    default String compressedClassName(){
        StringBuilder shortClassName = new StringBuilder();
        String[] packages = getFullyQualifiedClassName().split("\\.");
        for(int j = 0; j < packages.length; j++){
            String packageName = j < packages.length - 2 ? String.valueOf(packages[j].charAt(0)) : packages[j];
            shortClassName.append(packageName);
            if(j < packages.length - 1) shortClassName.append(".");
        }
        return shortClassName.toString();
    }

    boolean isNeighbor(CodeElementName target);
}
