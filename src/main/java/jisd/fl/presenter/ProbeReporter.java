package jisd.fl.presenter;

import jisd.fl.core.entity.susp.SuspiciousExprTreeNode;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProbeReporter {
    static private final int HEADER_LENGTH = 100;

    public void reportProbeTarget(SuspiciousVariable target) {
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("LOCATION", target.getLocateMethod(true));
        infoMap.put("TARGET VARIABLE", target.getSimpleVariableName() + " == " + target.getActualValue());
        printRoundedBox("PROBE", formattedMapString(infoMap, 0));
    }

    public void reportCauseExpression(SuspiciousExpression cause){
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("LOCATION", cause.locateMethod + ": line " + cause.locateLine);
        infoMap.put("LINE", cause.stmtString().replace("\n", " "));
        printWithHeader("CAUSE LINE", formattedMapString(infoMap, 1));

    }

    public void reportInvokedReturnExpression(SuspiciousExprTreeNode root){
        if(root.childSuspExprs.isEmpty()) return;
        printHeader("INVOKED RETURNS", HEADER_LENGTH);
        reportInvokedReturnExpression(root, 1);
    }

    private void reportInvokedReturnExpression(SuspiciousExprTreeNode target, int indentLevel){
        Map<String, String> infoMap = new HashMap<String, String>();
        infoMap.put("LOCATION", target.suspExpr.locateMethod + ": line " + target.suspExpr.locateLine);
        infoMap.put("LINE", target.suspExpr.stmtString().replace("\n", " "));
        List<String> formatted = formattedMapString(infoMap, indentLevel);
        for (String line : formatted) {
            System.out.println(line);
        }
        printHeader("", HEADER_LENGTH);
        for(SuspiciousExprTreeNode child : target.childSuspExprs) {
            reportInvokedReturnExpression(child, indentLevel + 1);
        }

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
        printHeader(title, HEADER_LENGTH);
        for (String line : body) {
            System.out.println(line);
        }
    }

    private void printHeader(String title, int length){
        String header;
        if(title.isEmpty()) {
            header = "─".repeat(length);
        } else {
            header = "── " + title + " " + "─".repeat(Math.max(0, length - title.length()) - 4);
        }
        System.out.println(header);
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
