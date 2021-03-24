package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MvnPluginDependencyResolver extends DependencyResolver {


    public MvnPluginDependencyResolver(File pomFile, ArtifactIdentifier identifier) {
        super(pomFile, identifier);
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
                ResolverError error = new ResolverError("Got non-success exit code while invoking maven: " + exitCode);
                result.appendError(error);
                dumpErrorLogs(output, Paths.get(pomFile.getParent(), "resolver-errors.log").toFile());
            } else {
                result.setResults(dependencies);
            }

        }
        catch (Exception x){
            ResolverError error = new ResolverError("Unexpected exception during Maven invocation", x);
            result.appendError(error);
            x.printStackTrace();
        }

        return result;
    }

    private void dumpErrorLogs(List<String> output, File logFile) throws IOException {
        Files.write(logFile.toPath(), output);
    }
}
