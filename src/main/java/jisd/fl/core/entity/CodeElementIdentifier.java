package jisd.fl.core.entity;

import java.nio.file.Path;

public interface CodeElementIdentifier  {


    //Path getFilePath();
    //Path getFilePath(boolean isTest);

    String fullyQualifiedName();
    String compressedName();
    //boolean isNeighbor(CodeElementIdentifier target);
}
