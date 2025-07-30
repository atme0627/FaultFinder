package jisd.fl.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringHighlighter {
    // ANSI エスケープシーケンス：緑背景、リセット
    private static final String BG_GREEN = "\u001B[42m";
    private static final String RESET    = "\u001B[0m";

    /**
     * input 中のすべての target を緑背景ハイライトして返す
     */
    public static String highlight(String input, String target) {
        if (target == null || target.isEmpty()) {
            return input;
        }
        // target をエスケープして正規表現パターンを作成
        Pattern p = Pattern.compile(Pattern.quote(target));
        Matcher m = p.matcher(input);

        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            // マッチ部分を緑背景＋元文字＋リセット に置き換え
            m.appendReplacement(sb, BG_GREEN + m.group() + RESET);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
