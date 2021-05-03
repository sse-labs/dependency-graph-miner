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
        MINER_ONLY, NODE_RESOLVER_ONLY, LIB_RESOLVER_ONLY, ALL
    }

    public static void main(String[] args){

        MinerConfiguration theConfig = Program.readConfiguration();

        if(theConfig == null)
            System.exit(1);

        if(!Program.initStorageConnection(theConfig))
            System.exit(2);

        ExecutionMode mode = null;

        if(args.length == 0){
            mode = ExecutionMode.MINER_ONLY;
        } else if (args.length == 1){
            switch(args[0].toLowerCase()){
                case "resolve-nodes":
                    mode = ExecutionMode.NODE_RESOLVER_ONLY;
                    break;
                case "resolve-libs":
                    mode = ExecutionMode.LIB_RESOLVER_ONLY;
                    break;
                case "mine":
                    mode = ExecutionMode.MINER_ONLY;
                    break;
                case "all":
                    mode = ExecutionMode.ALL;
                    break;
                default:
                    log.error("Usage: Program [resolve-nodes|resolve-libs|mine|all]");
                    System.exit(1);
            }
        } else {
            log.error("Usage: Program [resolve-nodes|resolve-libs|mine|all]");
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

            if(mode == ExecutionMode.NODE_RESOLVER_ONLY || mode == ExecutionMode.ALL){
                log.info("Starting to resolve artifact dependencies in graph ...");
                long startTime = System.currentTimeMillis();
                GraphNodeRelationResolver resolver = new GraphNodeRelationResolver();

                resolver.createRelationsInGraph();

                long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Finished resolving node relations in " + durationSeconds + " seconds");

            }

            if(mode == ExecutionMode.LIB_RESOLVER_ONLY || mode == ExecutionMode.ALL){
                log.info("Starting to resolve library relations in graph ...");
                long startTime = System.currentTimeMillis();
                LibraryVersionRelationResolver resolver = new LibraryVersionRelationResolver(theConfig);
                resolver.initialize();

                resolver.resolveAllLibraryRelations();

                long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Finished resolving library relations in " + durationSeconds + " seconds");
            }
        } finally {
            Program.tryShutdownStorageConnection();
        }

    }
}
