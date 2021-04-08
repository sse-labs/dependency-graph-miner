package org.tud.vulnanalysis.pom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;
import org.tud.vulnanalysis.storage.ArtifactStorageAdapter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class PomFileBatchResolver extends Thread {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();
    private static MavenCentralRepository MavenRepo = MavenCentralRepository.getInstance();

    private List<ArtifactIdentifier> batch;
    private ArtifactStorageAdapter storageAdapter;
    private Logger log = LogManager.getLogger(PomFileBatchResolver.class);


    public PomFileBatchResolver(List<ArtifactIdentifier> batch){
        this.batch = batch;
        this.storageAdapter = new ArtifactStorageAdapter();
    }


    @Override
    public void run(){
        this.processBatch();
    }

    private void processBatch(){

        List<ResolverResult> resultBatch = new ArrayList<>();
        List<ArtifactIdentifier> failedIdentifiers = new ArrayList<>();

        while(!this.batch.isEmpty()){
            ArtifactIdentifier current = this.batch.remove(0);
            ResolverResult result = processIdentifier(current);

            if(result!= null)
                resultBatch.add(result);
            else
                failedIdentifiers.add(current);
        }

        this.storageAdapter.storeArtifactBatch(resultBatch);
        this.storageAdapter.storeFailedIdentifiers(failedIdentifiers);

        this.batch = null;
        log.info("Finished processing batch.");
    }

    private ResolverResult processIdentifier(ArtifactIdentifier identifier){
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

            if(!dependcyResolverResult.hasDownloadErrors())
            {
                // If we have (possibly corrupt) results and errors while resolving, retry with slower implementation
                if(dependcyResolverResult.hasErrors() && dependcyResolverResult.hasResults()){
                    log.warn("Got " + dependcyResolverResult.getErrors().size() +
                            " errors while resolving " + identifier.toString());

                    if(ResolverProvider.backupResolverEnabled()){
                        log.trace("Retrying artifact with backup resolver: " + identifier.toString());

                        dependcyResolverResult = ResolverProvider
                                .buildBackupResolver(MavenRepo.openPomFileInputStream(identifier), identifier)
                                .resolveDependencies();
                    }

                }
                // In this case it is unlikely that the backup resolver would make any difference
                else if(dependcyResolverResult.hasErrors()){
                    log.warn("Got " + dependcyResolverResult.getErrors().size() +
                            " critical errors while resolving, not falling back: " + identifier.toString());
                }
            } else {
                log.warn("Got download errors for " + identifier.toString());
            }

            if(dependcyResolverResult.hasResults()){
                dependcyResolverResult.LastModified = lastModified;
                log.trace("Successfully processed " + identifier.toString());
                return dependcyResolverResult;
            }
            else
            {
                log.warn("No results for this artifact: " + identifier.toString());
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

}
