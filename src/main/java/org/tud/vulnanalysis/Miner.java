package org.tud.vulnanalysis;

import org.tud.vulnanalysis.lucene.BufferedGAVIterator;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.PomFileDownloadResponse;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.tud.vulnanalysis.pom.dependencies.RecursiveDependencyResolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Miner {

    public static void main(String[] args) throws IOException {
        int current = 0;

        DependencyResolverProvider.getInstance().registerResolverType(RecursiveDependencyResolver.class);

        BufferedGAVIterator iterator =
                new BufferedGAVIterator("C:\\Users\\Fujitsu\\Documents\\Temp\\my-maven-miner\\maven-index\\central-lucene-index");

        System.out.println("Building GAV index...");
        iterator.initializeIndex();

        System.out.println("Done, start iterating artifacts..");
        MavenCentralRepository repo = MavenCentralRepository.getInstance();

        while(iterator.hasNext()){

            ArtifactIdentifier ident = iterator.next();

            System.out.println(current + " - Downloading pom file for " + ident.getCoordinates());
            Path tempDir = Paths.get("C:\\Users\\Fujitsu\\Documents\\Temp\\my-maven-miner\\workdir", String.valueOf(current));

            tempDir.toFile().mkdirs();

            File output = Paths.get(tempDir.toString(), "pom.xml").toFile();

            PomFileDownloadResponse response = repo.downloadPomFile(ident, output);
            if(!response.getSuccess()){
                if (!(response.getException() instanceof FileNotFoundException))
                    System.err.println("Error while downloading!");
                current++;
                continue;
            }

            Set<ArtifactDependency> deps = DependencyResolverProvider
                    .getInstance()
                    .buildResolver(output, ident)
                    .resolveDependencies();

            if(deps == null){
                deps = new HashSet<ArtifactDependency>();
            } else {
                output.delete();
                tempDir.toFile().delete();
            }

            MavenArtifact artifact = new MavenArtifact(ident, response.getLastModified(), deps);
            System.out.println(artifact);

            if(current > 888){
                break;
            }



            current++;
        }

        System.out.println("Total artifacts processed: " + current);
    }
}
