package org.anon.vulnanalysis.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.*;
import org.anon.vulnanalysis.utils.MinerConfiguration;

public class Neo4jSessionFactory  implements AutoCloseable{

    private Driver neo4jDriver;

    private static Neo4jSessionFactory instance;

    private MinerConfiguration minerConfig;

    private static final Logger log = LogManager.getLogger(Neo4jSessionFactory.class);

    public static void initializeInstance(MinerConfiguration config){
        if(instance == null)
            instance = new Neo4jSessionFactory(config);
    }

    public static boolean ensureIndicesPresent(){
        try(Session session = instance.buildSession()){

            session.run("CREATE INDEX FOR (a:Artifact) ON (a.groupId, a.artifactId)");
            session.run("CREATE CONSTRAINT ON (a:Artifact) ASSERT a.coordinates IS UNIQUE");
            session.run("CREATE CONSTRAINT ON (ref:ArtifactReference) ASSERT ref.coordinates IS UNIQUE");

            return true;
        } catch (Exception x){
            log.error("Failed to create indices", x);
            return false;
        }
    }

    public static Neo4jSessionFactory getInstance(){
        if(instance == null){
            throw new IllegalStateException("Session Factory has not been initialized yet.");
        }
        return instance;
    }

    public Session buildSession(){
        return this.neo4jDriver.session();
    }

    private Neo4jSessionFactory(MinerConfiguration config){
        this.minerConfig = config;
        createDriver();
    }

    private void createDriver(){
        this.neo4jDriver = GraphDatabase.driver(this.minerConfig.Neo4jHost,
                AuthTokens.basic(this.minerConfig.Neo4jUsername, this.minerConfig.Neo4jPassword));
        try{
            this.neo4jDriver.verifyConnectivity();
        }
        catch(Exception x){
            this.neo4jDriver.close();
            throw x;
        }

    }

    @Override
    public void close() {
        if(this.neo4jDriver != null)
            this.neo4jDriver.close();
    }
}
