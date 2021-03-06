package org.anon.vulnanalysis;

import org.anon.vulnanalysis.lucene.BufferedGAVIterator;
import org.anon.vulnanalysis.model.ArtifactIdentifier;
import org.anon.vulnanalysis.pom.PomFileBatchResolver;
import org.anon.vulnanalysis.pom.dependencies.AetherDependencyResolver;
import org.anon.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.anon.vulnanalysis.pom.dependencies.RecursiveDependencyResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.anon.vulnanalysis.utils.MinerConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Miner {

    private static final DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();

    private final Logger log = LogManager.getLogger(Miner.class);

    private ExecutorService threadPool;
    private BufferedGAVIterator artifactIterator;
    private boolean isInitialized;

    private final MinerConfiguration config;

    public Miner(MinerConfiguration config){
        this.isInitialized = false;
        ResolverProvider.registerResolverType(RecursiveDependencyResolver.class);
        ResolverProvider.registerBackupResolverType(AetherDependencyResolver.class);
        this.config = config;
    }

    public boolean initialize(){
        File luceneIndexDir = new File(config.MavenCentralLuceneIndexPath);

        if(!luceneIndexDir.exists() || !luceneIndexDir.isDirectory()){
            log.error("Invalid lucene index directory: " + luceneIndexDir.getAbsolutePath());
            return false;
        }

        try{
            log.info("Initializing lucene index, this might take a few minutes...");

            artifactIterator = new BufferedGAVIterator(luceneIndexDir.getAbsolutePath());
            artifactIterator.initializeIndex();

            this.threadPool = Executors.newFixedThreadPool(config.NumberOfWorkerThreads);

            log.info("Done initializing index.");
        }
        catch(IOException iox){
            log.error("Failed to initialize lucene index.", iox);
            return false;
        }

        this.isInitialized = true;
        return true;
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

            if(batch.size() >= config.BatchSize){
                log.trace("Scheduling a new batch @ " + artifactCnt + " artifacts..");
                Thread worker = new PomFileBatchResolver(batch, config);
                this.threadPool.execute(worker);
                batch = new ArrayList<>();
            }

            artifactCnt++;
        }

        if(batch.size() > 0){
            log.trace("Scheduling last batch @ " + artifactCnt + " artifacts..");
            this.threadPool.execute(new PomFileBatchResolver(batch, config));
        }
        
        try{
            log.info("Waiting for threadpool to finish execution...");
            threadPool.shutdown();
            threadPool.awaitTermination(10, TimeUnit.DAYS);
        }
        catch(InterruptedException ix){
            log.error("Error while waiting for threadpool", ix);
        }
        log.info("Finished processing " + artifactCnt + " artifacts");
    }
}
