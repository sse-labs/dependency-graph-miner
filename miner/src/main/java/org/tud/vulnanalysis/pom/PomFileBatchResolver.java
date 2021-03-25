package org.tud.vulnanalysis.pom;

import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLConnection;
import java.util.List;

public class PomFileBatchResolver extends Thread {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();
    private static MavenCentralRepository MavenRepo = MavenCentralRepository.getInstance();

    private List<ArtifactIdentifier> batch;


    public PomFileBatchResolver(List<ArtifactIdentifier> batch){
        this.batch = batch;
    }


    @Override
    public void run(){
        this.processBatch();
    }

    private void processBatch(){

        while(!this.batch.isEmpty()){
            ArtifactIdentifier current = this.batch.remove(0);
            processIdentifier(current);
        }

        this.batch = null;
        System.out.println("Finished processing batch.");
    }

    private void processIdentifier(ArtifactIdentifier identifier){
        //System.out.println("PROCESSING: " + identifier);

        try{
            URLConnection connection = MavenRepo.openPomFileConnection(identifier);

            if(connection == null){
                System.err.println("Download failed.");
                return;
            }

            long lastModified = connection.getLastModified();

            ResolverResult dependcyResolverResult = ResolverProvider
                    .buildResolver(connection.getInputStream(), identifier)
                    .resolveDependencies();

            // If we have (possibly corrupt) results and errors while resolving, retry with slower implementation
            if(dependcyResolverResult.hasErrors() && dependcyResolverResult.hasResults()){

                if(ResolverProvider.backupResolverEnabled()){
                    System.err.println("Got " + dependcyResolverResult.getErrors().size() +
                            " errors while resolving, falling back to secondary resolver ...");

                    dependcyResolverResult = ResolverProvider
                            .buildBackupResolver(MavenRepo.openPomFileInputStream(identifier), identifier)
                            .resolveDependencies();
                }
                else {
                    System.err.println("Got " + dependcyResolverResult.getErrors().size() +
                            " errors while resolving, no backup resolver specified.");
                }

            }
            // In this case it is unlikely that the backup resolver would make any difference
            else if(dependcyResolverResult.hasErrors()){
                System.err.println("Got " + dependcyResolverResult.getErrors().size() +
                        " critical errors while resolving, not falling back.");
            }

            if(dependcyResolverResult.hasResults()){
                MavenArtifact artifact =
                        new MavenArtifact(identifier, lastModified, dependcyResolverResult.getResults());
                artifact.setErrorsWhileResolving(dependcyResolverResult.getErrors());

                if(dependcyResolverResult.hasParentIdentifier()){
                    artifact.setParent(dependcyResolverResult.getParentIdentifier());
                }
                //System.out.println("GOT ARTIFACT: " + artifact);
                //TODO: Handle artifact
            }
            else
            {
                System.err.println("No results for this artifact.");
            }
        }
        catch(FileNotFoundException fnfx){
            System.err.println("Failed to locate POM file definition on Maven Central: " + identifier.toString());
        }
        catch(IOException iox){
            System.err.println("IO Failure while processing artifact identifier " + identifier.toString());
            System.err.println(iox.getClass() + " : " + iox.getMessage());
        }
        catch(Exception x){
            System.err.println("Unexpected error while processing artifact identifier " + identifier.toString());
            x.printStackTrace();
        }

    }

}
