package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.io.File;
import java.io.InputStream;

public abstract class DependencyResolver implements IDependencyResolver {

    protected InputStream pomFileInputStream;
    protected ArtifactIdentifier identifier;

    public DependencyResolver(InputStream pomStream, ArtifactIdentifier identifier){
        this.pomFileInputStream = pomStream;
        this.identifier = identifier;
    }
}
