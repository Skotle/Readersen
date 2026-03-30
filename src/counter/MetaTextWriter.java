package counter;
import java.io.*;
import java.util.List;

public class MetaTextWriter {

    private static final String FILE_PATH = "author-date.txt";

    public static void save(List<PostMeta> list) {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(FILE_PATH, true), "UTF-8"))) {

            for (PostMeta meta : list) {
                bw.write(meta.author + "|" + meta.date);
                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
