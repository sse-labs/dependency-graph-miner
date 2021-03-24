package org.tud.vulnanalysis.lucene;

import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

public class BufferedGAVIterator implements Iterator<ArtifactIdentifier> {

    private Set<ArtifactIdentifier> artifacts;
    private IndexIterator indexReadIterator;
    private Iterator<ArtifactIdentifier> listIterator;

    private boolean initialized;
    private String splitPattern;

    public BufferedGAVIterator(String pathToIndex) throws IOException {
        indexReadIterator = new IndexIterator(pathToIndex);
        artifacts = new HashSet<>();
        initialized = false;
        listIterator = null;
        splitPattern = Pattern.quote("|");
    }

    public void initializeIndex() throws IOException {
        int current = 0;

        while(indexReadIterator.hasNext()) {
            String value = indexReadIterator.next();

            if(value == null)
                continue;

            String[] parts = value.split(splitPattern);
            ArtifactIdentifier artifact = new ArtifactIdentifier(parts[0], parts[1], parts[2]);

            artifacts.add(artifact);

            if(current % 100000 == 0){
                System.out.println("Progress: " + current + " / " + indexReadIterator.getMaxDocumentCount());
            }

            current++;
        }
        indexReadIterator.closeReader();
        listIterator = artifacts.iterator();
        initialized = true;
    }

    public int getTotalArtifactCount(){
        if(!initialized)
            return -1;
        else
            return artifacts.size();
    }

    @Override
    public boolean hasNext() {
        return initialized && listIterator.hasNext();
    }

    @Override
    public ArtifactIdentifier next() {
        if(!initialized)
            return null;

        return listIterator.next();
    }
}
