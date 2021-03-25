package org.tud.vulnanalysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tud.vulnanalysis.lucene.BufferedGAVIterator;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.pom.PomFileBatchResolver;
import org.tud.vulnanalysis.pom.dependencies.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Miner {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();

    private Logger log = LogManager.getLogger(Miner.class);

    private ExecutorService threadPool;
    private BufferedGAVIterator artifactIterator;
    private boolean isInitialized;

    public Miner(){
        this.isInitialized = false;
        ResolverProvider.registerResolverType(RecursiveDependencyResolver.class);
    }

    private void initializeIndex(String pathToLuceneIndex){
        File luceneIndexDir = new File(pathToLuceneIndex);

        if(!luceneIndexDir.exists() || !luceneIndexDir.isDirectory()){
            log.error("Invalid lucene index directory: " + luceneIndexDir.getAbsolutePath());
            return;
        }

        try{
            log.info("Initializing miner, this might take a few minutes...");
            artifactIterator = new BufferedGAVIterator(luceneIndexDir.getAbsolutePath());
            artifactIterator.initializeIndex();

            this.threadPool = Executors.newFixedThreadPool(4);

            this.isInitialized = true;
            log.info("Done initializing miner.");
        }
        catch(IOException iox){
            log.error("Failed to initialize lucene index.", iox);
        }
    }

    public void processArtifacts(){
        if(!this.isInitialized){
            throw new IllegalStateException("Cannot process Maven Central artifacts, miner is not initialized.");
        }

        int artifactCnt = 0;
        ArrayList<ArtifactIdentifier> batch = new ArrayList<>();

        while(this.artifactIterator.hasNext()){
            ArtifactIdentifier currentIdentifier = this.artifactIterator.next();
            batch.add(currentIdentifier);

            if(batch.size() >= 50){
                log.trace("Scheduling a new batch @ " + artifactCnt + " artifacts..");
                Thread worker = new PomFileBatchResolver(batch);
                this.threadPool.execute(worker);
                batch = new ArrayList<>();
            }

            if(artifactCnt > 1000){
                break;
            }

            artifactCnt++;
        }


        try{
            log.info("Waiting for threadpool to finish execution...");
            threadPool.shutdown();
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        }
        catch(InterruptedException ix){
            log.error("Error while waiting for threadpool", ix);
        }
        log.info("Finished processing artifacts");
    }

    public static void main(String[] args) {

        Miner miner = new Miner();
        miner.initializeIndex("C:\\Users\\Fujitsu\\Documents\\Research\\Vulnerabilities\\repos\\maven-miner\\index\\central-lucene-index");

        long startTime = System.currentTimeMillis();
        miner.processArtifacts();
        long duration = System.currentTimeMillis() - startTime;

        LogManager.getRootLogger().info("Processing took " + duration + " ms");
    }
}
