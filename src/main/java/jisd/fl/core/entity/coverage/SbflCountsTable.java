package jisd.fl.core.entity.coverage;

import java.util.Arrays;

/**
 * ElementIdRegistryのIDを添字にして、ef/ep の数を保持するテーブル
 * 粒度ごとにインスタンスを生成して使用する。
 * nf/np は保持せず、totalFail/totalPassから計算する。
 */
public class SbflCountsTable {
    private int[] ef = new int[0];
    private int[] ep = new int[0];

    public void ensureCapacity(int capacity){
        if(capacity <= ef.length) return;
        ef = Arrays.copyOf(ef, capacity);
        ep = Arrays.copyOf(ep, capacity);
    }

    public int getEf(int id){return ef[id];}
    public int getEp(int id){return ep[id];}
    public void incEf(int id){ef[id]++;}
    public void incEp(int id){ep[id]++;}

    public int capacity(){return ef.length;}
}
