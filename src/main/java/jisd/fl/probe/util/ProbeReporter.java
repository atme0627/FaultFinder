package jisd.fl.probe.util;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProbeReporter {
    public void reportProbeTarget(SuspiciousVariable target) {
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("LOCATION", target.getLocateMethod(true));
        infoMap.put("TARGET VARIABLE", target.getSimpleVariableName() + " == " + target.getActualValue());
        printRoundedBox("PROBE", formattedMapString(infoMap, 0));
    }

    public void reportCauseExpression(SuspiciousExpression cause){
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("LOCATION", cause.getLocateMethod() + ": line " + cause.getLocateLine());
        infoMap.put("LINE", cause.getStatementStr());
        printWithHeader("CAUSE LINE", formattedMapString(infoMap, 1));

    }


    private void printRoundedBox(String title, List<String> body){
        int maxLen = body.stream().mapToInt(String::length).max().orElse(0);
        int innerWidth = Math.max(maxLen, title.length() + 2);

        String top    = "╭─ " + title + " " + "─".repeat(Math.max(0, innerWidth - title.length() - 1)) + "╮";
        String bottom = "╰" + "─".repeat(innerWidth + 2) + "╯";

        System.out.println(top);
        for (String line : body) {
            System.out.println("│ " + padRight(line, innerWidth) + " │");
        }
        System.out.println(bottom);
    }

    private void printWithHeader(String title, List<String> body){
        int maxLen = body.stream().mapToInt(String::length).max().orElse(0);

        String header    = "── " + title + " " + "─".repeat(Math.max(0, maxLen - title.length()));
        System.out.println(header);
        for (String line : body) {
            System.out.println(line);
        }
    }

    static private String padLeft(String s, int length) {
        if (s == null) s = "";
        return String.format("%" + length + "s", s);
    }

    static private String padRight(String s, int length) {
        if (s == null) s = "";
        return String.format("%-" + length + "s", s);
    }

    static private List<String> formattedMapString(Map<String, String> map, int indentLevel) {
        int maxKeyLen = map.keySet().stream().mapToInt(String::length).max().orElse(0) + 1;
        List<String> ret = new ArrayList<>();
        for(Map.Entry<String, String> entry : map.entrySet()) {
            String formatted = "";
            if (indentLevel > 0) {
                formatted += padRight("", indentLevel * 4);
            }
            formatted += padRight(entry.getKey(), maxKeyLen);
            formatted += ": ";
            formatted += entry.getValue();
            ret.add(formatted);
        }
        return ret;
    }
}
