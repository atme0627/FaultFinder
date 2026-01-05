package jisd.fl.probe.info;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class TmpJsonUtils {
    //SuspiciousExpressionリファクタリングのための一時的なクラス
    //Jackson デシリアライズ用メソッド
    public static SuspiciousExpression loadFromJson(File f){
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(f, SuspiciousExpression.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
