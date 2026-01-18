package jisd.fl.core.entity;

public class ClassElementName implements CodeElementIdentifier {
    final public String packageName;
    final public String className;

    public ClassElementName(String packageName, String className){
        this.packageName = packageName;
        this.className = className;
    }

    //TODO: 内部クラスに対応(InnerClassElementNameを作る?)
    //      とりあえずここでは$以下は切り捨て
    public ClassElementName(String fqClassName){
        this.packageName = (fqClassName.contains(".")) ? fqClassName.substring(0, fqClassName.lastIndexOf('.')) : "";
        String className = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);
        this.className = className.contains("$") ? className.split("\\$")[0] : className;
    }

    @Override
    public String fullyQualifiedName(){
        return packageName.isEmpty() ? className : packageName + "." + className;
    }


    @Override
    public String compressedName(){
        StringBuilder shortClassName = new StringBuilder();
        String[] packages = fullyQualifiedName().split("\\.");
        for(int j = 0; j < packages.length; j++){
            String packageName = j < packages.length - 2 ? String.valueOf(packages[j].charAt(0)) : packages[j];
            shortClassName.append(packageName);
            if(j < packages.length - 1) shortClassName.append(".");
        }
        return shortClassName.toString();
    }

    public int compareTo(ClassElementName o) {
        return (!packageName.equals(o.packageName)) ? packageName.compareTo(o.packageName) : className.compareTo(o.className);
    }
}
