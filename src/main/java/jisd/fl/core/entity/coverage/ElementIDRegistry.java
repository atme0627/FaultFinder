package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
  CodeElementIdentifierとcoverage用IDの対応づけを行うクラス。
  elementの粒度ごとにインスタンスを生成して使用。
 */
public class ElementIDRegistry<E extends CodeElementIdentifier<E>> {
    private final Map<E, Integer> elementToId = new HashMap<>();
    private final ArrayList<E> idToElement = new ArrayList<>();

    /**
     * elementがすでに登録されていた場合は、対応するIDを返す。
     * そうでない場合は、登録した上で新しいIDを発行して返す。
     *
     * @param e カバレッジ取得対象の要素
     * @return 要素に対応づけられたID
     */
    public int getOrCreate(E e){
        Integer existingId = elementToId.get(e);
        if(existingId != null) {
            return existingId;
        }
        int newId = idToElement.size();
        elementToId.put(e, newId);
        idToElement.add(e);
        return newId;
    }

    /**
     * 指定されたIDに対応する要素を取得する。
     *
     * @param id 要素に対応づけられたID
     * @return 指定されたIDに対応する要素。IDが存在しない場合はnullを返す。
     */
    public E elementOf(int id){
        return idToElement.get(id);
    }
}
