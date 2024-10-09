package probe;
import jisd.info.*;

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

    //assertが失敗した場合、そのassertのAssertInfoを生成することを想定
    public FailedAssertInfo getAssertByLineNum(String className, int lineNum, Object actual) {
        ClassInfo ci = sif.createClass(className);
        String[] src = ci.src().split("\\r?\\n|\\r");
        String assertLine = src[lineNum - 1];

        FailedAssertInfoFactory factory = new FailedAssertInfoFactory();
        return factory.create(assertLine, actual, srcDir, className, lineNum);
    }
}
