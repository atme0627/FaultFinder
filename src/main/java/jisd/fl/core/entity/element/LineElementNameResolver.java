package jisd.fl.core.entity.element;

/**
 * あるクラスの line -> lineElementName のマップを保持する。
 */
public class LineElementNameResolver {
    private final MethodElementName[] methodByLine;
    private final MethodElementName clinitMethod;

    /**
     * 行番号とそれに対応するメソッド要素名を管理するためのクラスのコンストラクタ。
     *
     * @param lines 対象となる行数。この数に基づき、行番号に対応する配列が初期化される。
     * @param clinitMethod static initializer行を表す便宜的なMethodElementNameオブジェクト。
     */
    public LineElementNameResolver(int lines, MethodElementName clinitMethod){
        this.methodByLine = new MethodElementName[lines + 1];
        this.clinitMethod = clinitMethod;
    }

    public void putMethodRange(int beginLine, int endLine, MethodElementName Method){
        int begin = Math.max(beginLine, 1);
        int end = Math.min(endLine, methodByLine.length - 1);
        for (int i = begin; i <= end; i++) {
            methodByLine[i] = Method;
        }
    }

    public LineElementName lineElementAt(int line){
        MethodElementName m;
        if (line <= 0 || line >= methodByLine.length) {
            //ここには来ないはず
            m = clinitMethod;
        } else {
            m = methodByLine[line];
            if(m == null) {
                m = clinitMethod;
            }
        }
        return new LineElementName(m, line);
    }
}
