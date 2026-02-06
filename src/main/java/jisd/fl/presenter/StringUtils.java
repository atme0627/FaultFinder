package jisd.fl.presenter;

/**
 * 文字列フォーマット用のユーティリティクラス。
 */
public final class StringUtils {
    private StringUtils() {}

    /**
     * 文字列の左側にスペースを追加して指定幅に揃える（右寄せ）。
     * @param str 対象文字列
     * @param size 最小幅
     * @return 左パディングされた文字列
     */
    public static String padLeft(String str, int size) {
        return ("%" + size + "s").formatted(str);
    }

    /**
     * 文字列の右側にスペースを追加して指定幅に揃える（左寄せ）。
     * @param str 対象文字列
     * @param size 最小幅
     * @return 右パディングされた文字列
     */
    public static String padRight(String str, int size) {
        return ("%-" + size + "s").formatted(str);
    }
}
