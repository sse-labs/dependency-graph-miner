package org.anon.vulnanalysis.pom.dependencies;

import org.anon.vulnanalysis.model.ArtifactDependency;
import org.anon.vulnanalysis.model.ArtifactIdentifier;
import org.anon.vulnanalysis.pom.PomFileUtils;
import org.anon.vulnanalysis.utils.MinerConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

//TODO: Handle parent detection?
public class MvnPluginDependencyResolver extends DependencyResolver {

    private File pomFile;

    private File workDir;// = Paths.get("C:\\Users\\Fujitsu\\Documents\\Temp\\my-maven-miner\\workdir").toFile();
    private File tempDir;

    public MvnPluginDependencyResolver(InputStream pomFileStream, ArtifactIdentifier identifier, MinerConfiguration config) {
        super(pomFileStream, identifier, config);
        this.workDir = Paths.get(config.WorkingDirectoryPath).toFile();
        this.writePomFile();
    }

    private void writePomFile(){
        tempDir = Paths.get(workDir.getAbsolutePath(), identifier.getCoordinates().replace(":", "-")).toFile();
        tempDir.mkdirs();
        this.pomFile = Paths.get(tempDir.getAbsolutePath(), "pom.xml").toFile();

        if(!PomFileUtils.writeToPomFile(pomFileInputStream, pomFile)){
            throw new RuntimeException("Failed to write POM stream to file");
        }
    }

    private void cleanupPomFile(){
        this.pomFile.delete();
        this.tempDir.delete();
    }

    @Override
    public ResolverResult resolveDependencies() {
        ResolverResult result = new ResolverResult(this.identifier);

        HashSet<ArtifactDependency> dependencies = new HashSet<>();

        try {
            boolean foundDependencyList = false;

            String line;
            List<String> output = new ArrayList<>();

            String invokeMavenCommandWindows = "mvn.cmd dependency:list -DexcludeTransitive -B --no-transfer-progress -N";

            Process process = Runtime.getRuntime().exec(invokeMavenCommandWindows, null, pomFile.getParentFile());

            BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = stdReader.readLine()) != null) {
                if(!foundDependencyList && line.toLowerCase().contains("the following files have been resolved:")){
                    foundDependencyList = true;
                } else if (foundDependencyList && line.substring(6).trim().length() == 0){
                    foundDependencyList = false;
                } else if(foundDependencyList) {
                    String dependency = line.substring(6).trim();
                    if(!dependency.toLowerCase().equals("none")){
                        dependencies.add(ArtifactDependency.fromCompositeIdentifier(dependency));
                    }
                }
                output.add(line);
            }
            stdReader.close();

            int exitCode = process.waitFor();

            if(exitCode != 0){
                ResolverError error = new ResolverError("Got non-success exit code while invoking maven: " + exitCode, false);
                result.appendError(error);
                dumpErrorLogs(output, Paths.get(pomFile.getParent(), "resolver-errors.log").toFile());
            } else {
                result.setResults(dependencies);
            }

        }
        catch (Exception x){
            ResolverError error = new ResolverError("Unexpected exception during Maven invocation", x, false);
            result.appendError(error);
            x.printStackTrace();
        }

        if(!result.hasErrors())
            cleanupPomFile();

        return result;
    }

    private void dumpErrorLogs(List<String> output, File logFile) throws IOException {
        Files.write(logFile.toPath(), output);
    }
}
