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
}
