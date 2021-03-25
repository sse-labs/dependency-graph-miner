package org.tud.vulnanalysis.pom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.neo4j.driver.Session;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;
import org.tud.vulnanalysis.storage.Neo4jSessionFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class PomFileBatchResolver extends Thread {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();
    private static MavenCentralRepository MavenRepo = MavenCentralRepository.getInstance();
    private static Neo4jSessionFactory SessionFactory = Neo4jSessionFactory.getInstance();

    private List<ArtifactIdentifier> batch;
    private Logger log = LogManager.getLogger(PomFileBatchResolver.class);


    public PomFileBatchResolver(List<ArtifactIdentifier> batch){
        this.batch = batch;
    }


    @Override
    public void run(){
        this.processBatch();
    }

    private void processBatch(){

        List<MavenArtifact> artifactBatch = new ArrayList<>();

        while(!this.batch.isEmpty()){
            ArtifactIdentifier current = this.batch.remove(0);
            MavenArtifact artifact = processIdentifier(current);

            if(artifact != null)
                artifactBatch.add(artifact);
        }

        this.storeMavenArtifactBatch(artifactBatch);

        this.batch = null;
        log.info("Finished processing batch.");
    }

    private MavenArtifact processIdentifier(ArtifactIdentifier identifier){
        log.trace("Processing identifier: " + identifier);

        try{
            URLConnection connection = MavenRepo.openPomFileConnection(identifier);

            if(connection == null){
                log.error("Download failed.");
                return null;
            }

            long lastModified = connection.getLastModified();

            ResolverResult dependcyResolverResult = ResolverProvider
                    .buildResolver(connection.getInputStream(), identifier)
                    .resolveDependencies();

            // If we have (possibly corrupt) results and errors while resolving, retry with slower implementation
            if(dependcyResolverResult.hasErrors() && dependcyResolverResult.hasResults()){

                if(ResolverProvider.backupResolverEnabled()){
                    log.warn("Got " + dependcyResolverResult.getErrors().size() +
                            " errors while resolving, falling back to secondary resolver ...");

                    dependcyResolverResult = ResolverProvider
                            .buildBackupResolver(MavenRepo.openPomFileInputStream(identifier), identifier)
                            .resolveDependencies();
                }
                else {
                    log.warn("Got " + dependcyResolverResult.getErrors().size() +
                            " errors while resolving, no backup resolver specified.");
                }

            }
            // In this case it is unlikely that the backup resolver would make any difference
            else if(dependcyResolverResult.hasErrors()){
                log.warn("Got " + dependcyResolverResult.getErrors().size() +
                        " critical errors while resolving, not falling back.");
            }

            if(dependcyResolverResult.hasResults()){
                MavenArtifact artifact =
                        new MavenArtifact(identifier, lastModified, dependcyResolverResult.getResults());
                artifact.setErrorsWhileResolving(dependcyResolverResult.getErrors());

                if(dependcyResolverResult.hasParentIdentifier()){
                    artifact.setParent(dependcyResolverResult.getParentIdentifier());
                }
                log.trace("Got artifact: " + artifact);
                return artifact;
            }
            else
            {
                log.warn("No results for this artifact.");
                return null;
            }
        }
        catch(FileNotFoundException fnfx){
            log.warn("Failed to locate POM file definition on Maven Central: " + identifier.toString());
        }
        catch(IOException iox){
            log.warn("IO Failure while processing artifact identifier " + identifier.toString(), iox);
        }
        catch(Exception x){
            log.error("Unexpected error while processing artifact identifier " + identifier.toString(), x);
        }
        return null;
    }

    private void storeMavenArtifactBatch(List<MavenArtifact> artifactBatch){
        try(Session session = SessionFactory.buildSession()){
            for(MavenArtifact artifact: artifactBatch){

            }
        }
    }

}
