package jisd.fl.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class JsonIO {
    public static void export(Object obj, File output){
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(output, obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export to JSON", e);
        }
    }

    public static void outputJsonToFile(JSONObject obj, File output){
        try (FileWriter file = new FileWriter(output)) {
            // インデント付きで書きたい場合は toString(2)
            file.write(obj.toString(2));
            file.flush(); // 明示的にフラッシュ
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
