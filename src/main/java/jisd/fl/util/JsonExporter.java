package jisd.fl.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jisd.fl.probe.info.SuspiciousExpression;


import java.io.File;

public class JsonExporter {
    public static void exportSuspExpr(SuspiciousExpression root, File output){
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(output, root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export to JSON", e);
        }
    }
}
