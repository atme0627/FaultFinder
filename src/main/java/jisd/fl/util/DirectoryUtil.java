package jisd.fl.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryUtil {
    public static void initDirectory(String dirPath){
        File parentDir = new File(dirPath);
        if(parentDir.exists()){
            deleteDirectory(parentDir);
        }
        try {
            Files.createDirectory(Path.of(dirPath));
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

}
