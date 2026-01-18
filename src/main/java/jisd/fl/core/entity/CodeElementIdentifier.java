package jisd.fl.core.entity;

public interface CodeElementIdentifier  {
    String fullyQualifiedClassName();
    String fullyQualifiedName();
    String compressedName();
    //boolean isNeighbor(CodeElementIdentifier target);
}
