package org.anon.vulnanalysis.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigReader {

    private static Logger log = LogManager.getLogger(ConfigReader.class);

    private static final String WorkDirKey = "workdir";
    private static final String IndexDirKey = "indexdir";
    private static final String ThreadCountKey = "workerthreads";
    private static final String BatchSizeKey = "batchsize";
    private static final String Neo4jHostKey = "neo4j.host";
    private static final String Neo4jUserKey = "neo4j.user";
    private static final String Neo4jPassKey = "neo4j.pass";

    public static MinerConfiguration readConfiguration(String configFilePath){
        if(Files.notExists(Paths.get(configFilePath))){
            log.error("Configuration file does not exist at " + configFilePath);
            return null;
        }

        MinerConfiguration config = new MinerConfiguration();
        config.IncludeDependenciesInProfileDefinitions = false;

        try{
            Properties props = new Properties();
            props.load(Files.newInputStream(Paths.get(configFilePath)));

            if(props.containsKey(ThreadCountKey)){
                config.NumberOfWorkerThreads = Integer.parseInt(props.getProperty(ThreadCountKey));
            } else{
                config.NumberOfWorkerThreads = 2;
            }

            if(props.containsKey(WorkDirKey)){
                config.WorkingDirectoryPath = props.getProperty(WorkDirKey);
            } else {
                log.error("Configuration is missing required key " + WorkDirKey);
                return null;
            }

            if(props.containsKey(IndexDirKey)){
                config.MavenCentralLuceneIndexPath = props.getProperty(IndexDirKey);
            } else {
                log.error("Configuration is missing required key " + IndexDirKey);
                return null;
            }

            if(props.containsKey(BatchSizeKey)){
                config.BatchSize = Integer.parseInt(props.getProperty(BatchSizeKey));
            } else {
                config.BatchSize = 100;
            }

            if(props.containsKey(Neo4jHostKey)){
                config.Neo4jHost = props.getProperty(Neo4jHostKey);
            } else {
                log.error("Configuration is missing required key " + Neo4jHostKey);
                return null;
            }

            if(props.containsKey(Neo4jUserKey)){
                config.Neo4jUsername = props.getProperty(Neo4jUserKey);
            } else {
                log.error("Configuration is missing required key " + Neo4jUserKey);
                return null;
            }

            if(props.containsKey(Neo4jPassKey)){
                config.Neo4jPassword = props.getProperty(Neo4jPassKey);
            } else {
                log.error("Configuration is missing required key " + Neo4jPassKey);
                return null;
            }
        }
        catch(IOException iox){
            log.error("IO failure while reading configuration.", iox);
            return null;
        }

        return config;

    }
}
