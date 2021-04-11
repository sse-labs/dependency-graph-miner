package org.tud.vulnanalysis.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.*;

public class BufferedNodeInformationIterator implements Iterator<BufferedNodeInformationIterator.NodeInformation> {

    private final Logger log = LogManager.getLogger(BufferedNodeInformationIterator.class);
    private final Neo4jSessionFactory sessionFactory = Neo4jSessionFactory.getInstance();

    private final List<NodeInformation> nodeIndex;
    private boolean isInitialized;

    private final ObjectMapper reader = new ObjectMapper();

    private int currentIndex;

    public BufferedNodeInformationIterator(){
        this.nodeIndex = new ArrayList<>();
        this.currentIndex = 0;
    }

    public int getNumberOfNodes(){
        if(!this.isInitialized)
            return -1;
        else
            return this.nodeIndex.size();
    }

    public void buildIndex(){

        log.info("Starting to build node dependency index...");

        try(Session session = sessionFactory.buildSession()){

            session.readTransaction(transaction -> {
                Result res = transaction.run("MATCH (a:Artifact) RETURN a.coordinates AS coords, a.dependencies AS deps, " +
                        "a.parentCoords AS parent");
                res.stream().forEach( record -> {
                    String coords = record.get("coords").asString();
                    String dependenciesRaw = record.get("deps").asString();
                    String parentCoords = record.get("parent").asString();

                    try {
                        String[] dependencies = reader.readValue(dependenciesRaw, String[].class);
                        NodeInformation obj = new NodeInformation();
                        obj.nodeCoordinates = coords;
                        obj.nodeDependencies = dependencies;
                        obj.parentCoordinates = parentCoords;

                        this.nodeIndex.add(obj);
                    } catch (Exception x){
                        log.error("Failed to deserialize dependency list: " + dependenciesRaw, x);
                    }
                });

                return null;
            });

            log.info("Finished building node dependency index, got a total of " + this.nodeIndex.size() + " nodes.");

            this.isInitialized = true;
        } catch (Exception x){
            this.isInitialized = false;
            log.error("Failed to initialize node dependency index", x);
        }
    }

    @Override
    public boolean hasNext() {
        if(!isInitialized)
            return false;

        return this.currentIndex < this.nodeIndex.size();
    }

    @Override
    public NodeInformation next() {
        if(!hasNext())
            return null;

        NodeInformation node = this.nodeIndex.get(this.currentIndex);
        this.currentIndex += 1;

        return node;
    }

    public static class NodeInformation {
        public String nodeCoordinates;

        public String[] nodeDependencies;

        public String parentCoordinates;
    }
}
