package org.tud.vulnanalysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tud.vulnanalysis.storage.Neo4jSessionFactory;
import org.tud.vulnanalysis.utils.ConfigReader;
import org.tud.vulnanalysis.utils.MinerConfiguration;

public class Program {

    private static Logger log = LogManager.getLogger(Program.class);

    public static MinerConfiguration readConfiguration(){
        MinerConfiguration config = ConfigReader.readConfiguration("miner.config");

        if(config == null)
            log.error("Failed to read configuration file.");

        return config;
    }

    public static boolean initStorageConnection(MinerConfiguration config){
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

        return true;
    }

    public static void tryShutdownStorageConnection(){
        if(Neo4jSessionFactory.getInstance() != null)
            Neo4jSessionFactory.getInstance().close();
    }

    public enum ExecutionMode {
        MINER_ONLY, RESOLVER_ONLY, ALL
    }

    public static void main(String[] args){

        MinerConfiguration theConfig = Program.readConfiguration();

        if(theConfig == null)
            System.exit(1);

        if(!Program.initStorageConnection(theConfig))
            System.exit(2);

        // Default execution mode
        ExecutionMode mode = ExecutionMode.MINER_ONLY;

        if(args.length == 1 && args[0].equalsIgnoreCase("resolve")){
            mode = ExecutionMode.RESOLVER_ONLY;
        } else if(args.length == 1 && args[0].equalsIgnoreCase("all")){
            mode = ExecutionMode.ALL;
        } else if(args.length == 1 && args[0].equalsIgnoreCase("mine")){
            mode = ExecutionMode.MINER_ONLY;
        } else if(args.length > 0){
            log.error("Usage: Program [resolve|mine|all]");
            System.exit(1);
        }

        try{
            if(mode == ExecutionMode.MINER_ONLY || mode == ExecutionMode.ALL){
                log.info("Starting to mine artifacts from Maven Central ...");
                long startTime = System.currentTimeMillis();
                Miner miner = new Miner(theConfig);

                if(miner.initialize()){
                    miner.processArtifacts();
                }
                long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Finished mining artifacts in " + durationSeconds + " seconds");
            }

            if(mode == ExecutionMode.RESOLVER_ONLY || mode == ExecutionMode.ALL){
                log.info("Starting to resolve artifact dependencies in graph ...");
                long startTime = System.currentTimeMillis();
                GraphNodeRelationResolver resolver = new GraphNodeRelationResolver();

                resolver.createRelationsInGraph();

                long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Finished resolving relations in " + durationSeconds + " seconds");

            }
        } finally {
            Program.tryShutdownStorageConnection();
        }

    }
}
