package org.tud.vulnanalysis.pom;

import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;

import java.io.IOException;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PomFileBatchResolver extends Thread {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();
    private static MavenCentralRepository MavenRepo = MavenCentralRepository.getInstance();

    private final Object batchMonitor = new Object();
    private List<ArtifactIdentifier> batch;

    private boolean stopRequested;

    public PomFileBatchResolver(){
        this.stopRequested = false;
        this.batch = null;
    }


    @Override
    public void run(){
        try{
            while(!this.stopRequested){
                synchronized (batchMonitor){
                    while(this.batch == null){
                        // Wait to be assigned a batch
                        batchMonitor.wait();

                        // Prefer stopping over checking for new batch
                        if(this.stopRequested) break;
                    }
                }

                // Immediately leave loop
                if(this.stopRequested) break;

                this.processBatch();
            }
        }
        catch(InterruptedException ix){
            ix.printStackTrace();
        }
    }

    private void processBatch(){
        synchronized (batchMonitor){
            System.out.println("Start working on batch");

            try{
                while(!this.batch.isEmpty()){
                    ArtifactIdentifier current = this.batch.remove(0);
                    processIdentifier(current);
                }

                this.batch = null;
            }
            finally{
                batchMonitor.notifyAll();
                System.out.println("Finished working on batch!");
            }
        }

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
                System.err.println("Got " + dependcyResolverResult.getErrors().size() + " critical errors while resolving, not falling back.");
            }

            if(dependcyResolverResult.hasResults()){
                MavenArtifact artifact =
                        new MavenArtifact(identifier, lastModified, dependcyResolverResult.getResults());

                //System.out.println("GOT ARTIFACT: " + artifact);
                //TODO: Handle artifact
            }
            else
            {
                System.err.println("No results for this artifact.");
            }
        }
        catch(IOException iox){
            System.err.println("IO Failure while processing artifact identifier " + identifier.toString());
            System.err.println(iox.getClass() + " : " + iox.getMessage());
        }

    }

    public void assignBatch(List<ArtifactIdentifier> newBatch) throws InterruptedException{
        synchronized (batchMonitor){
            while(this.batch != null){
                batchMonitor.wait();
            }
            this.batch = newBatch;
            batchMonitor.notifyAll();
        }
    }

    public void requestStop(){
        this.stopRequested = true;

        synchronized (batchMonitor){
            batchMonitor.notifyAll();
        }
    }


}
