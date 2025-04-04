package experiment.defect4j;

import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.StaticAnalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Defects4jUtil {
    static File defects4jDir = new File("/Users/ezaki/Desktop/tools/defects4j");
    static String rootDir = "/Users/ezaki/Desktop/research/experiment/defects4j";

    public static void CheckoutAll(String project, int numberOfBugs){
        for(int i = 1; i <= numberOfBugs; i++){
            CheckoutBuggySrc(project, i);
        }
    }

    public static void CheckoutBuggySrc(String project, int bugId){
        String cmd = "defects4j checkout -p " + project + " -v " + bugId + "b " +
                "-w " + getProjectDir(project, bugId, true);
        execCmd(cmd);
    }

    public static void CompileBuggySrc(String project, int bugId){
        String cmd = "defects4j compile -w " + getProjectDir(project, bugId, true);
        execCmd(cmd);
    }


    public static String getProjectDir(String project, int bugId, boolean buggy){
        return rootDir + "/" + project + "/" + project + "_" + bugId + (buggy ? "_buggy" : "_fixed");
    }



    public static void changeTargetVersion(String project, int bugId){
        Properties p = getD4jProperties(project, bugId);

        String targetSrcDir = getProjectDir(project, bugId, true) + "/" + p.getProperty("d4j.dir.src.classes");
        String testSrcDir = getProjectDir(project, bugId, true) + "/" + p.getProperty("d4j.dir.src.tests");
        String targetBinDir = getProjectDir(project, bugId, true) + "/target/classes";
        String testBinDir = getProjectDir(project, bugId, true) + "/target/test-classes";

        PropertyLoader.setProperty("targetSrcDir", targetSrcDir);
        PropertyLoader.setProperty("testSrcDir", testSrcDir);
        PropertyLoader.setProperty("testBinDir", testBinDir);
        PropertyLoader.setProperty("targetBinDir", targetBinDir);
        PropertyLoader.store();
    }

    public static List<String> getFailedTestMethods(String project, int bugId){
        Properties properties = getD4jProperties(project, bugId);
        String raw = properties.getProperty("d4j.tests.trigger");
        List<String> failedTests = new ArrayList<>(List.of(raw.split(",")));
        failedTests = failedTests.stream()
                .map((s) -> s.replace("::", "#"))
                .collect(Collectors.toList());
        return failedTests;
    }

    public static List<String> getModifiedClasses(String project, int bugId){
        Properties properties = getD4jProperties(project, bugId);
        String raw = properties.getProperty("d4j.classes.modified");
        List<String> modifiedClasses = new ArrayList<>(List.of(raw.split(",")));
        return modifiedClasses;
    }

    public static Properties getD4jProperties(String project, int bugId){
        Properties properties = new Properties();
        String propertyFileName = getProjectDir(project, bugId, true) + "/defects4j.build.properties";
        try {
            properties.load(Files.newBufferedReader(Paths.get(propertyFileName), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    public static List<String> getModifiedMethod(String project, int bugId) {
        Set<String> modifiedMethods = new HashSet<>();

        Defects4jUtil.changeTargetVersion(project, bugId);
        List<String> modifiedClasses = Defects4jUtil.getModifiedClasses(project, bugId);
        Properties p = Defects4jUtil.getD4jProperties(project, bugId);
        String targetRootDir = p.getProperty("d4j.dir.src.classes");

        for(String modifiedClass : modifiedClasses) {
            String targetSrc = targetRootDir + "/" + modifiedClass.replace(".", "/") + ".java";
            String[] cmd = new String[]
                    {"src/main/java/experiment/defect4j/modified_method.sh",
                            project, String.valueOf(bugId), targetSrc};

            Process proc;
            try {
                proc = Runtime.getRuntime().exec(cmd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            List<String> lines = new ArrayList<>();
            try (var buf = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String tmp;
                while ((tmp = buf.readLine()) != null) {
                    lines.add(tmp);
                }
                proc.waitFor();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }

            List<Integer> modifiedLines = new ArrayList<>();
            for (String l : lines) {
                int modifiedLine = Integer.parseInt(l.split("[+,]")[0]
                        .replaceAll("[^0-9]", ""));
                modifiedLines.add(modifiedLine);
            }

            for(int modifiedLine : modifiedLines){
                try {
                    String m = StaticAnalyzer.getMethodNameFormLine(modifiedClass, modifiedLine);
                    if(m != null) modifiedMethods.add(m);
                } catch (NoSuchFileException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return new ArrayList<>(modifiedMethods);
    }

    private static void execCmd(String cmd){
        try {
            Process proc = Runtime.getRuntime().exec(cmd, null, defects4jDir);
            proc.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
