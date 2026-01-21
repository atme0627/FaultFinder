package jisd.fl.core.entity.element;

public interface CodeElementIdentifier<T extends CodeElementIdentifier<T>> extends Comparable<T> {
    String fullyQualifiedClassName();
    String fullyQualifiedName();
    String compressedName();
    int compareTo(T other);
    boolean isNeighbor(T other);
}
