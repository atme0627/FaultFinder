package jisd.fl.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtil {
    public static void initDirectory(String dirPath){
        File parentDir = new File(dirPath);
        if(parentDir.exists()){
            deleteDirectory(parentDir);
        }
        try {
            Files.createDirectories(Path.of(dirPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createDirectory(String dirPath){
        File parentDir = new File(dirPath);
        if(parentDir.exists()){
            return;
        }
        try {
            Files.createDirectories(Path.of(dirPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteDirectory(File parentDir){
        File[] childFiles = parentDir.listFiles();
        if(childFiles != null){
            for(File file : childFiles){
                if(file.isDirectory()){
                    deleteDirectory(file);
                }
                else {
                    file.delete();
                }
            }
        }
        parentDir.delete();
    }

    public static void initFile(String dir, String fileName){
        Path p = Paths.get(dir, fileName);
        try {
            Files.deleteIfExists(p);
            createFile(dir, fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    public static void createFile(String dir, String fileName){
        Path p = Paths.get(dir, fileName);
        if(Files.exists(p)){
            return;
        }

        try {
            Files.createFile(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isExist(String dir, String fileName){
        Path p = Paths.get(dir, fileName);
        return  Files.exists(p);
    }
}
