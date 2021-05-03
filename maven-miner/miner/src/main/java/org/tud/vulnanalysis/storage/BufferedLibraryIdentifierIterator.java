package org.tud.vulnanalysis.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BufferedLibraryIdentifierIterator implements Iterator<String> {

    private final Neo4jSessionFactory sessionFactory = Neo4jSessionFactory.getInstance();
    private final Logger log = LogManager.getLogger(BufferedLibraryIdentifierIterator.class);

    private final List<String> identifierIndex;
    private boolean isInitialized;

    private int nextIndex;

    private boolean incrementalOnly = false;

    public BufferedLibraryIdentifierIterator(){
        this.identifierIndex = new ArrayList<>();
        this.isInitialized = false;
        this.nextIndex = -1;
    }

    public boolean isInitialized(){
        return this.isInitialized;
    }

    public int getIndexSize(){
        if(!isInitialized)
            return -1;
        else
            return this.identifierIndex.size();
    }

    public void excludeLibrariesWithNextRelations(){
        this.incrementalOnly = true;
    }

    public void buildIndex(){

        log.info("Start building index of all library identifiers...");

        try(Session session = sessionFactory.buildSession()){
            Result result = session.run(this.buildIteratorQuery());

            while(result.hasNext()){
               identifierIndex.add(result.next().get("lib").asString());
            }

            log.info("Successfully built index of " + this.identifierIndex.size() + " library identifiers.");

            this.isInitialized = true;
            this.nextIndex = 0;
        } catch(Exception x) {
            log.error("Uncaught failure while building library identifier index", x);
        }
    }

    @Override
    public boolean hasNext() {
        if(!this.isInitialized)
            return false;

        return this.nextIndex < this.identifierIndex.size();
    }

    @Override
    public String next() {
        if(!this.hasNext())
            return null;

        String identifier = this.identifierIndex.get(this.nextIndex);
        this.nextIndex += 1;

        return identifier;
    }

    private String buildIteratorQuery(){
        if(this.incrementalOnly){
            return "MATCH (a:Artifact) WHERE NOT EXISTS ((a)-[:NEXT]->(:Artifact)) " +
                    "AND NOT EXISTS((a)<-[:NEXT]-(:Artifact)) " +
                    "WITH a.groupId + ':' + a.artifactId AS lib RETURN DISTINCT lib";
        } else {
            return "MATCH (a:Artifact) WITH a.groupId + ':' + a.artifactId AS lib RETURN DISTINCT lib";
        }
    }
}
