package org.tud.vulnanalysis.model;

import java.util.Date;
import java.util.Set;

public class MavenArtifact {

    private ArtifactIdentifier identifier;
    private long lastModified;
    private Set<ArtifactDependency> dependencies;

    public MavenArtifact(ArtifactIdentifier ident, long lastModified, Set<ArtifactDependency> dependencies){
        this.identifier = ident;
        this.lastModified = lastModified;
        this.dependencies = dependencies;
    }

    public long getLastModified(){
        return this.lastModified;
    }

    public ArtifactIdentifier getIdentifier(){
        return this.identifier;
    }

    public Set<ArtifactDependency> getDependencies(){
        return this.dependencies;
    }

    @Override
    public String toString(){
        return this.identifier.getCoordinates() + " published on " + new Date(this.lastModified).toString() + " with " +
                this.dependencies.size() + " dependencies";
    }
}
