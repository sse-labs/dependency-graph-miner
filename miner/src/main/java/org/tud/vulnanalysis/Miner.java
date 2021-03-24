package org.tud.vulnanalysis;

import org.tud.vulnanalysis.lucene.BufferedGAVIterator;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.PomFileDownloadResponse;
import org.tud.vulnanalysis.pom.dependencies.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Miner {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();

    public static void main(String[] args) throws IOException {
        int current = 0;

        ResolverProvider.registerResolverType(RecursiveDependencyResolver.class);
        ResolverProvider.registerBackupResolverType(MvnPluginDependencyResolver.class);

        BufferedGAVIterator iterator =
                new BufferedGAVIterator("C:\\Users\\Fujitsu\\Documents\\Research\\Vulnerabilities\\repos\\maven-miner\\index\\central-lucene-index");

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
                if (!(response.getException() instanceof FileNotFoundException)){
                    System.err.println("Error while downloading!");
                } else {
                    System.err.println("Download failed with 404.");
                }
                current++;
                continue;
            }

            ResolverResult result = ResolverProvider
                    .buildResolver(output, ident)
                    .resolveDependencies();

            if(result.hasErrors()){
                for(ResolverError e: result.getErrors()){
                    System.err.println(e.toString());
                }
            }

            if(result.hasErrors() && result.hasResults()){
                System.err.println("Got " + result.getErrors().size() + " errors while resolving, falling back to secondary resolver ...");
                result = ResolverProvider.buildBackupResolver(output, ident).resolveDependencies();
            }
            else if(result.hasErrors()){
                System.err.println("Got " + result.getErrors().size() + " critical errors while resolving, not falling back.");
            }

            if(result.hasResults()){
                MavenArtifact artifact = new MavenArtifact(ident, response.getLastModified(), result.getResults());
                System.out.println(artifact);
                output.delete();
                tempDir.toFile().delete();
            }
            else
            {
                System.err.println("No results for this artifact.");
            }

            if(current > 330){
                break;
            }



            current++;
        }

        System.out.println("Total artifacts processed: " + current);
    }
}
