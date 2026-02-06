package jisd.fl.core.entity.coverage;

import java.util.stream.Stream;

/**
 * SBFL カバレッジデータへのアクセスを提供するインターフェース。
 * JaCoCo からの計測データと CSV から復元したデータの両方を統一的に扱う。
 */
public interface SbflCoverageProvider {

    /**
     * 行レベルのカバレッジエントリを返す。
     *
     * @param hideZeroElements true の場合、EP + EF が 0 のエントリを除外
     * @return 行カバレッジエントリのストリーム
     */
    Stream<LineCoverageEntry> lineCoverageEntries(boolean hideZeroElements);

    /**
     * メソッドレベルのカバレッジエントリを返す。
     *
     * @param hideZeroElements true の場合、EP + EF が 0 のエントリを除外
     * @return メソッドカバレッジエントリのストリーム
     */
    Stream<MethodCoverageEntry> methodCoverageEntries(boolean hideZeroElements);

    /**
     * クラスレベルのカバレッジエントリを返す。
     *
     * @return クラスカバレッジエントリのストリーム
     */
    Stream<ClassCoverageEntry> classCoverageEntries();
}
