package jisd.fl.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserUtilTest {

    @Test
    void isConstructorTest() {
        String methodName = "org.apache.commons.math.optimization.RealPointValuePair#RealPointValuePair(double[], double)";
        Assertions.assertTrue(JavaParserUtil.isConstructor(methodName));
    }
}