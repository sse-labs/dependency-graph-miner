package org.tud.vulnanalysis.pom;

import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PomFileBatchResolver extends Thread {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();
    private static MavenCentralRepository MavenRepo = MavenCentralRepository.getInstance();

    private List<ArtifactIdentifier> batch;

    private boolean stopRequested;
    private Lock batchWriteLock;

    public PomFileBatchResolver(){
        this.stopRequested = false;
        this.batch = null;
        this.batchWriteLock = new ReentrantLock();
    }


    @Override
    public void run(){
        try{
            while(!this.stopRequested){
                while(this.batch == null){
                    // Wait to be assigned a batch
                    wait();

                    // Prefer stopping over checking for new batch
                    if(this.stopRequested) break;
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
        this.batchWriteLock.lock();

        try{
            while(!this.batch.isEmpty()){
                ArtifactIdentifier current = this.batch.remove(0);
                processIdentifier(current);
            }

            this.batch = null;
        }
        finally{
            notifyAll();
            this.batchWriteLock.unlock();
        }
    }

    private void processIdentifier(ArtifactIdentifier identifier){
        System.out.println("PROCESSING: " + identifier);
    }

    public void assignBatch(List<ArtifactIdentifier> newBatch){
        this.batchWriteLock.lock();

        try{
            this.batch = newBatch;
        }
        finally {
            notifyAll();
            this.batchWriteLock.unlock();
        }
    }

    public void requestStop(){
        this.stopRequested = true;
    }
}
