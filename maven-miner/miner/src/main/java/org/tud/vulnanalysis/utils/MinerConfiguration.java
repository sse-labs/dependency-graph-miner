package org.tud.vulnanalysis.utils;

public class MinerConfiguration {

    public String WorkingDirectoryPath;

    public String MavenCentralLuceneIndexPath;

    public boolean IncludeDependenciesInProfileDefinitions;

    public int NumberOfWorkerThreads;

    public int BatchSize;

    public String Neo4jHost;

    public String Neo4jUsername;

    public String Neo4jPassword;

    public static MinerConfiguration getDefaultConfig(){
        MinerConfiguration config = new MinerConfiguration();
        config.WorkingDirectoryPath = ".";
        config.MavenCentralLuceneIndexPath = ".";
        config.IncludeDependenciesInProfileDefinitions = false;
        config.NumberOfWorkerThreads = 4;
        config.BatchSize = 1000;
        config.Neo4jUsername = "neo4j";
        config.Neo4jHost = "bolt://localhost:7687";
        config.Neo4jPassword = "<CHANGEME>";

        return config;
    }
}
