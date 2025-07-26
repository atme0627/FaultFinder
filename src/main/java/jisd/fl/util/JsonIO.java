package jisd.fl.util;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.LineElementName;
import jisd.fl.util.analyze.MethodElementName;


import java.io.File;

public class JsonIO {
    public static void exportSuspExpr(SuspiciousExpression root, File output){
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(output, root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export to JSON", e);
        }
    }

    public static void exportCoverage(CoverageCollection coverage, File output){
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(output, coverage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export to JSON", e);
        }
    }

    public static CoverageCollection importCoverage(File input){
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addKeyDeserializer(CodeElementName.class,
                new CodeElementNameKeyDeserializer());
        mapper.registerModule(module);

        try {
            return mapper.readValue(input, CoverageCollection.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class CodeElementNameKeyDeserializer extends KeyDeserializer {
        @Override
        public CodeElementName deserializeKey(String key, DeserializationContext ctxt) {
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
}
