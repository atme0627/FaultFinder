package jisd.fl.presenter;

import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.susp.ExpressionType;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProbeReporter {
    // ANSI color constants
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[90m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String LOCATION_BOLD = "\u001B[38;5;75m"; // bright blue (step header)
    private static final String LOCATION_DIM = "\u001B[38;5;67m";  // dimmer blue (child nodes)

    // VS Code Dark+ theme colors for Java syntax
    private static final String KW_COLOR = "\u001B[34m";     // blue - keywords
    private static final String NUM_COLOR = "\u001B[38;5;178m"; // light olive - numeric literals
    private static final String STR_COLOR = "\u001B[38;5;173m"; // orange - string literals
    private static final String TYPE_COLOR = "\u001B[38;5;79m"; // teal - type names
    private static final String METHOD_COLOR = "\u001B[38;5;222m"; // light yellow - method calls

    // Type tag colors: 背景塗りつぶし + 白文字（やや暗め）
    private static final String TAG_ASSIGN = "\u001B[97m\u001B[48;5;24m";  // bright-white on dark blue
    private static final String TAG_RETURN = "\u001B[97m\u001B[48;5;54m";  // bright-white on dark purple
    private static final String TAG_ARGUMENT = "\u001B[97m\u001B[48;5;94m"; // bright-white on dark gold
    private static final String TREE_DIM = "\u001B[2;90m";        // dim gray for tree lines

    private static final int SEPARATOR_WIDTH = 80;

    // Java keywords
    private static final String KEYWORDS = "\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue"
            + "|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements"
            + "|import|instanceof|int|interface|long|native|new|package|private|protected|public"
            + "|return|short|static|strictfp|super|switch|synchronized|this|throw|throws"
            + "|transient|try|void|volatile|while|var|record|sealed|permits|yield)\\b";

    // Matches: digits (int, float, hex, binary), true, false, null
    private static final String LITERALS = "\\b(?:0[xX][0-9a-fA-F_]+[lL]?|0[bB][01_]+[lL]?|\\d[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+-]?\\d+)?[fFdDlL]?|true|false|null)\\b";

    // String/char literals (handles escaped quotes)
    private static final String STRINGS = "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'";

    // Type names: capitalized identifiers (e.g., String, List, MyClass)
    private static final String TYPE_NAMES = "\\b[A-Z][a-zA-Z0-9]*\\b";

    // Method calls: identifier followed by (
    // Note: no capture group - we use group(0) directly
    private static final String METHOD_CALLS = "\\b[a-z][a-zA-Z0-9]*(?=\\s*\\()";

    // Combined pattern: ANSI escape sequences first (to skip them), then tokens
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<ANSI>\u001B\\[[;\\d]*[A-Za-z])"      // existing ANSI escapes (skip)
            + "|(?<STRING>" + STRINGS + ")"
            + "|(?<KEYWORD>" + KEYWORDS + ")"
            + "|(?<LITERAL>" + LITERALS + ")"
            + "|(?<TYPE>" + TYPE_NAMES + ")"
            + "|(?<METHOD>" + METHOD_CALLS + ")"
    );

    public void reportProbeStart(SuspiciousLocalVariable target) {
        System.out.println(BOLD + "[Probe]" + RESET
                + " Target: " + target.variableName() + " == " + target.actualValue()
                + " @ " + target.locateMethod().methodSignature);
        System.out.println(DIM + "─".repeat(SEPARATOR_WIDTH) + RESET);
    }

    public void reportExplorationStep(int step, SuspiciousExpression target, List<SuspiciousExpression> children) {
        System.out.println();
        // Step header: [N] location (bold + white)
        System.out.println(CYAN + "[" + step + "]" + RESET
                + " " + BOLD + LOCATION_BOLD + target.locateMethod() + ":" + target.locateLine() + RESET);
        // Source code line (syntax highlighted)
        System.out.println("    " + highlightJava(target.stmtString().replace("\n", " ")));

        if (children.isEmpty()) {
            System.out.println("    " + YELLOW + "(leaf)" + RESET);
        } else {
            for (SuspiciousExpression child : children) {
                System.out.println("    " + GREEN + "→" + RESET
                        + " " + coloredTypeTag(ExpressionType.from(child)) + LOCATION_DIM + child.locateMethod() + ":" + child.locateLine() + RESET
                        + "  " + highlightJava(child.stmtString().replace("\n", " ")));
            }
        }
    }

    public void reportSectionEnd() {
        System.out.println();
        System.out.println(DIM + "═".repeat(SEPARATOR_WIDTH) + RESET);
        System.out.println();
        System.out.println(BOLD + "[Cause Tree]" + RESET);
    }

    public void printCauseTree(CauseTreeNode root) {
        StringBuilder sb = new StringBuilder();
        printTreeNode(sb, root, "", true);
        System.out.print(sb);
    }

    private void printTreeNode(StringBuilder sb, CauseTreeNode node, String prefix, boolean isTail) {
        // Tree lines (prefix + connector) in dim gray
        String connector = isTail ? "└── " : "├── ";
        sb.append(TREE_DIM).append(prefix).append(connector).append(RESET);

        // Type tag with color
        sb.append(coloredTypeTag(node.type()));

        // Location in dimmer blue
        sb.append(LOCATION_DIM).append("( ").append(node.locateMethod()).append(" line:").append(node.locateLine()).append(" )").append(RESET);

        // Source code with syntax highlighting
        sb.append(" ").append(highlightJava(node.stmtString().replace("\n", " ").trim()));
        sb.append("\n");

        // Recurse children — prefix is plain text (no ANSI) so it renders correctly in dim gray
        var children = node.children();
        String childPrefix = prefix + (isTail ? "    " : "│   ");
        for (int i = 0; i < children.size() - 1; i++) {
            printTreeNode(sb, children.get(i), childPrefix, false);
        }
        if (!children.isEmpty()) {
            printTreeNode(sb, children.getLast(), childPrefix, true);
        }
    }

    private static String coloredTypeTag(ExpressionType type) {
        if (type == null) return DIM + "[   EXPR   ]" + RESET + " ";
        return switch (type) {
            case ASSIGNMENT -> TAG_ASSIGN + "[  ASSIGN  ]" + RESET + " ";
            case RETURN -> TAG_RETURN + "[  RETURN  ]" + RESET + " ";
            case ARGUMENT -> TAG_ARGUMENT + "[ ARGUMENT ]" + RESET + " ";
        };
    }

    /**
     * Java ソースコードに VS Code Dark+ 風のシンタックスハイライトを適用する。
     * 既存の ANSI エスケープ（引数ハイライト等）は保持する。
     */
    static String highlightJava(String source) {
        StringBuilder sb = new StringBuilder();
        Matcher m = TOKEN_PATTERN.matcher(source);
        int lastEnd = 0;

        while (m.find()) {
            // マッチ前のテキストをそのまま追加
            sb.append(source, lastEnd, m.start());

            if (m.group("ANSI") != null) {
                // 既存の ANSI エスケープはそのまま
                sb.append(m.group());
            } else if (m.group("STRING") != null) {
                sb.append(STR_COLOR).append(m.group()).append(RESET);
            } else if (m.group("KEYWORD") != null) {
                sb.append(KW_COLOR).append(m.group()).append(RESET);
            } else if (m.group("LITERAL") != null) {
                sb.append(NUM_COLOR).append(m.group()).append(RESET);
            } else if (m.group("TYPE") != null) {
                sb.append(TYPE_COLOR).append(m.group()).append(RESET);
            } else if (m.group("METHOD") != null) {
                sb.append(METHOD_COLOR).append(m.group()).append(RESET);
            } else {
                sb.append(m.group());
            }
            lastEnd = m.end();
        }
        sb.append(source, lastEnd, source.length());
        return sb.toString();
    }

}
