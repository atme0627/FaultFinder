package jisd.fl.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jisd.fl.probe.info.SuspiciousExpression;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;


import java.io.File;

public class JsonExporter {
    public static void exportSuspExpr(SuspiciousExpression root, File output){
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new Jdk8Module());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(output, root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export to JSON", e);
        }
    }
}
