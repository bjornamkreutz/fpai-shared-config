package nl.tno.bnd.repodownloader.ant;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;

public class Repo {
    private final File destDir;
    private final String name;
    private final String indexUrl;
    private final boolean fullRepo;
    private final Pattern skip;

    private String index;

    public Repo(File destDir, String name, String indexUrl, boolean fullRepo, Pattern skip) {
        this.destDir = destDir;
        this.name = name;
        this.indexUrl = indexUrl;
        this.fullRepo = fullRepo;
        this.skip = skip;
    }

    public String getName() {
        return name;
    }

    public void downloadRepo() throws MalformedURLException, IOException {
        File destDir = getDestDir();
        destDir.mkdirs();

        for (String url : getUrls(skip)) {
            String fileName = url.substring(url.lastIndexOf("/") + 1);
            String symbolicName = fileName.substring(0, fileName.indexOf('-'));

            File dir = destDir;
            if (fullRepo) {
                dir = new File(destDir, symbolicName);
                dir.mkdirs();
            }

            File newFile = new File(dir, fileName);

            if (newFile.exists()) {
                System.out.println(newFile + " already exists");
            } else {
                byte[] byteArray = IOUtils.toByteArray(new URL(url));
                IOUtils.write(byteArray, new FileOutputStream(newFile));
                System.out.println("Downloaded " + newFile + " from " + url);
            }
        }

        if (fullRepo) {
            OutputStream indexOut = new FileOutputStream(new File(destDir, "index.xml"));
            IOUtils.write(getIndex(), indexOut, Charset.defaultCharset());
            indexOut.close();
            System.out.println("Written index for " + destDir);
        }
    }

    public File getDestDir() {
        if (fullRepo) {
            return new File(destDir, name);
        } else {
            return destDir;
        }
    }

    public List<String> getUrls(Pattern skip) throws MalformedURLException, IOException {
        String index = getIndex();
        int slashIdx = indexUrl.lastIndexOf("/");
        String baseUrl = indexUrl.substring(0, slashIdx + 1);
        List<String> res = new ArrayList<String>();
        Pattern pattern = Pattern.compile(".*name='url'.+value='(.*)'.*");
        for (String line : index.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String relativeUrl = matcher.group(1);
                String url = baseUrl + relativeUrl;
                if (skip == null || !skip.matcher(url).find()) {
                    res.add(url);
                }
            }
        }
        return res;
    }

    private String getIndex() throws MalformedURLException, IOException {
        if (index == null) {
            URL url = new URL(indexUrl);
            if (indexUrl.endsWith(".gz")) {
                // compressed
                byte[] byteArray = IOUtils.toByteArray(url);
                index = IOUtils.toString(new GZIPInputStream(new ByteArrayInputStream(byteArray)),
                                         Charset.defaultCharset());
            } else {
                // not compressed
                index = IOUtils.toString(url, Charset.defaultCharset());
            }
        }
        return index;
    }
}
