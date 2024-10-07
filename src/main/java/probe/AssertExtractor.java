package probe;
import jisd.info.*;

import java.util.ArrayList;

public class AssertExtractor {
    StaticInfoFactory sif;
    String srcDir;
    String binDir;

    public AssertExtractor(String srcDir, String binDir){
        this.srcDir = srcDir;
        this.binDir = binDir;
        this.sif = new StaticInfoFactory(srcDir, binDir);
    }

    public String getSource(String className){
        ClassInfo ci = sif.createClass(className);
        return ci.src();
    }

    public AssertInfo getAssertByLineNum(String className, int lineNum) {
        return null;
    }

    public ArrayList<AssertInfo> getAssertFromMethod(String methodName) {
        return null;
    }

    public ArrayList<AssertInfo> getAssertFromClass(String className) {
        return null;
    }
}
