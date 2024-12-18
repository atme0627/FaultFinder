package jisd.fl.probe;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class StackTrace {
    List<Pair<Integer, String>> st = new ArrayList<>();
    public StackTrace(String rawTrace){
        parseRawTrace(rawTrace);
    }

    private void parseRawTrace(String rawTrace) {
        String[] splitStackTrace = rawTrace.split("\\n");
        for (String e : splitStackTrace) {
            if(e.isEmpty()) continue;
            st.add(normalizeElement(e));
        }
    }

    private Pair<Integer, String> normalizeElement(String e){
        StringBuilder sb = new StringBuilder(e);
        sb.setCharAt(sb.lastIndexOf("."), '#');
        String method = sb.substring(sb.indexOf("]") + 2, sb.lastIndexOf("(") - 1);
        int line = Integer.parseInt(e.substring(e.indexOf("(") + 1, e.length() - 1).substring(6));
        return Pair.of(line, method);
    }

    public int getLine(int depth){
        return st.get(depth).getLeft();
    }

    public String getMethod(int depth){
        return st.get(depth).getRight();
    }
}
