package org.tud.vulnanalysis;

import org.tud.vulnanalysis.lucene.BufferedGAVIterator;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.*;

import java.io.IOException;
import java.net.URLConnection;

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

            URLConnection pomConnection = repo.openPomFileConnection(ident);

            if(pomConnection == null){
                System.err.println("Failed to download POM file.");
                continue;
            }

            ResolverResult result = ResolverProvider
                    .buildResolver(pomConnection.getInputStream(), ident)
                    .resolveDependencies();

            if(result.hasErrors()){
                for(ResolverError e: result.getErrors()){
                    System.err.println(e.toString());
                }
            }

            if(result.hasErrors() && result.hasResults()){
                System.err.println("Got " + result.getErrors().size() + " errors while resolving, falling back to secondary resolver ...");
                result = ResolverProvider.buildBackupResolver(repo.openPomFileInputStream(ident), ident).resolveDependencies();
            }
            else if(result.hasErrors()){
                System.err.println("Got " + result.getErrors().size() + " critical errors while resolving, not falling back.");
            }

            if(result.hasResults()){
                MavenArtifact artifact = new MavenArtifact(ident, pomConnection.getLastModified(), result.getResults());
                System.out.println(artifact);
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
