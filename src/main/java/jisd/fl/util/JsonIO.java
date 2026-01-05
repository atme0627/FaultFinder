package jisd.fl.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.core.entity.CodeElementIdentifier;
import jisd.fl.core.entity.MethodElementName;
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

    public static CoverageCollection importCoverage(File input){
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addKeyDeserializer(CodeElementIdentifier.class,
                new CodeElementNameKeyDeserializer());
        mapper.registerModule(module);

        try {
            return mapper.readValue(input, CoverageCollection.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T importFromJson(File input, TypeReference<T> typeReference){
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(input, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static class CodeElementNameKeyDeserializer extends KeyDeserializer {
        @Override
        public CodeElementIdentifier deserializeKey(String key, DeserializationContext ctxt) {
            // key は toString() の結果
            // 例: "com.example.Foo#bar() line: 42"
            if(key.contains("line")) {
                String[] parts = key.split(" line: ");
                String fqmnWithSig = parts[0];            // "com.example.Foo#bar()"
                int line = Integer.parseInt(parts[1]); //  42

                // MethodElementName／LineElementName を組み立て
                return new MethodElementName(fqmnWithSig).toLineElementName(line);
            }
            else {
                return new MethodElementName(key);
            }
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
