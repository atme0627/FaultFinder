package jisd.fl.probe.info;

import jisd.fl.util.analyze.MethodElementName;

/**
 * 木構造のノード情報（CodeElementName、行番号、深さ）を格納するレコード。
 */
public record SuspLineOccurrence(
        MethodElementName methodElementName,
        int line,
        int depth) {
}
