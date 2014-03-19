package nl.tno.bnd.repodownloader.ant;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
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
    private static final Pattern REPOSBNDPATTERN = Pattern.compile(".*name=(.+);.*locations=(.+);.*");
    private boolean full = true;
    private File bndFile;
    private File outDir;

    private URL repository;
    private boolean gzipped = false;
    private boolean changeBndFile = false;

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

    public void setChangeBndFile(boolean changeBndFile) {
        this.changeBndFile = changeBndFile;
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
                try {
                    r.downloadRepo();
                } catch (Exception e) {
                    throw new BuildException("Error while making repo " + r.getName() + " local: " + e.getMessage(), e);
                }
            }
            if (changeBndFile) {
                changeBndFile(repos);
            }
        } catch (IOException e) {
            throw new BuildException("Error while making repos local: " + e.getMessage(), e);
        }
    }

    public void changeBndFile(List<Repo> repos) throws FileNotFoundException, IOException {
        // make backup
        File backupfile = new File(bndFile.getParent(), bndFile.getName() + ".orig");
        if (backupfile.exists()) {
            backupfile.delete();
        }
        bndFile.renameTo(backupfile);
        BufferedReader br = new BufferedReader(new FileReader(backupfile));
        FileWriter wr = new FileWriter(bndFile);
        String line;
        while ((line = br.readLine()) != null) {
            if (line.contains("FixedIndexedRepo")) {
                Matcher matcher = REPOSBNDPATTERN.matcher(line);
                if (matcher.matches()) {
                    String name = matcher.group(1);
                    Repo repo = getRepoForName(repos, name);
                    if (repo != null) {
                        // remove everything from indexUrl
                        line = "\taQute.bnd.deployer.repository.LocalIndexedRepo;name='" + name
                               + "';local='"
                               + getBndtoolsOutDir()
                               + name
                               + "';pretty=true,\\";
                    }
                }
            }
            wr.write(line + "\r\n");
        }
        br.close();
        wr.close();
    }

    public File getDestDir() {
        return outDir;
    }

    private Repo getRepoForName(List<Repo> repos, String name) {
        for (Repo r : repos) {
            if (name.equals(r.getName())) {
                return r;
            }
        }
        return null;
    }

    private String getBndtoolsOutDir() {
        String outDirStr = outDir.getAbsolutePath();
        return "${workspace}/" + outDirStr.substring(outDirStr.lastIndexOf("cnf")).replace("\\", "/") + "/";
    }

    private List<Repo> getRepos() throws IOException {
        List<Repo> res = new ArrayList<Repo>();

        if (bndFile != null) {
            BufferedReader br = new BufferedReader(new FileReader(bndFile));
            try {
                Pattern pattern = REPOSBNDPATTERN;
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
