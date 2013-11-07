package nl.tno.bnd.repodownloader.ant;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class MakeReposLocal extends Task {
    private boolean full = true;
    private File bndFile;
    private File outDir;

    private URL repository;
    private boolean gzipped = false;

    private Pattern skip;

    public void setBndFile(String bndFile) {
        this.bndFile = new File(bndFile);
        if (!this.bndFile.exists()) {
            throw new BuildException("Given bndFile [" + bndFile + "] can not be found");
        }
    }

    public void setOutDir(String outDir) {
        this.outDir = new File(outDir);
        if (!this.outDir.exists()) {
            if (!this.outDir.mkdirs()) {
                throw new BuildException("Can not create output dir [" + outDir + "]");
            }
        } else if (!this.outDir.isDirectory()) {
            throw new BuildException("Can not create output dir [" + outDir + "]");
        }
    }

    public void setFull(boolean full) {
        this.full = full;
    }

    public void setRepository(String repository) throws MalformedURLException {
        this.repository = new URL(repository);
    }

    public void setGzipped(boolean gzipped) {
        this.gzipped = gzipped;
    }

    public void setSkip(String skip) {
        try {
            this.skip = Pattern.compile(skip, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            throw new BuildException("Invalid regular expression for the skip parameter", ex);
        }
    }

    @Override
    public void execute() {
        if (outDir == null) {
            throw new BuildException("Missing parameter outDir");
        }

        try {
            List<Repo> repos = getRepos();
            for (Repo r : repos) {
                r.downloadRepo();
            }
            // TODO change repositoires.bnd
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getDestDir() {
        return outDir;
    }

    private List<Repo> getRepos() throws IOException {
        List<Repo> res = new ArrayList<Repo>();

        if (bndFile != null) {
            BufferedReader br = new BufferedReader(new FileReader(bndFile));
            try {
                Pattern pattern = Pattern.compile(".*name=(.+);.*locations=(.+);.*");
                String line;

                while ((line = br.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        String name = matcher.group(1);
                        String indexUrl = matcher.group(2);
                        res.add(new Repo(outDir, name, indexUrl, full, skip));
                    }
                }
            } finally {
                br.close();
            }
        }
        if (repository != null) {
            String name = repository.toString();
            name = name.substring(name.lastIndexOf('/') + 1);
            String indexUrl = repository + "/index.xml" + (gzipped ? ".gz" : "");
            res.add(new Repo(outDir, name, indexUrl, full, skip));
        }
        return res;
    }
}
