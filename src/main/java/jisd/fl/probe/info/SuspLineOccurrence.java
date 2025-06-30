package jisd.fl.probe.info;

import jisd.fl.util.analyze.CodeElementName;

/**
 * 木構造のノード情報（CodeElementName、行番号、深さ）を格納するレコード。
 */
public record SuspLineOccurrence(
        CodeElementName codeElementName,
        int line,
        int depth) {
}
