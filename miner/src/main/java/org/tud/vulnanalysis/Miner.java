package org.tud.vulnanalysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tud.vulnanalysis.lucene.BufferedGAVIterator;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.pom.PomFileBatchResolver;
import org.tud.vulnanalysis.pom.dependencies.*;
import org.tud.vulnanalysis.storage.Neo4jSessionFactory;
import org.tud.vulnanalysis.utils.ConfigReader;
import org.tud.vulnanalysis.utils.MinerConfiguration;

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

    private MinerConfiguration config;

    public Miner(MinerConfiguration config){
        this.isInitialized = false;
        ResolverProvider.registerResolverType(RecursiveDependencyResolver.class);
        this.config = config;
    }

    public boolean initialize(){
        File luceneIndexDir = new File(config.MavenCentralLuceneIndexPath);

        if(!luceneIndexDir.exists() || !luceneIndexDir.isDirectory()){
            log.error("Invalid lucene index directory: " + luceneIndexDir.getAbsolutePath());
            return false;
        }

        try{
            log.info("Initializing Neo4j storage backend (host: '" + config.Neo4jHost + "' user:'" +
                    config.Neo4jUsername + "')");
            Neo4jSessionFactory.initializeInstance(config);
            log.info("Successfully initialized storage backend");
        }
        catch(Exception x){
            log.error("Failed to initialized storage backend.", x);
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

    public void shutdown(){
        if(Neo4jSessionFactory.getInstance() != null){
            Neo4jSessionFactory.getInstance().close();
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
            threadPool.awaitTermination(10, TimeUnit.DAYS);
        }
        catch(InterruptedException ix){
            log.error("Error while waiting for threadpool", ix);
        }
        log.info("Finished processing artifacts");
    }

    public static void main(String[] args) {

        MinerConfiguration theConfig = ConfigReader.readConfiguration("miner.config");

        if(theConfig == null){
            LogManager.getRootLogger().error("Invalid configuration file, aborting.");
            return;
        }

        Miner miner = new Miner(theConfig);

        if(!miner.initialize()){
            return;
        }

        long startTime = System.currentTimeMillis();
        miner.processArtifacts();
        long duration = System.currentTimeMillis() - startTime;

        LogManager.getRootLogger().info("Processing took " + duration + " ms");

        miner.shutdown();
    }
}
