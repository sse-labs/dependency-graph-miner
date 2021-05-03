package org.anon.vulnanalysis.pom.dependencies;

import org.anon.vulnanalysis.model.ArtifactIdentifier;
import org.anon.vulnanalysis.utils.MinerConfiguration;

import java.io.InputStream;

public abstract class DependencyResolver implements IDependencyResolver {

    protected InputStream pomFileInputStream;
    protected ArtifactIdentifier identifier;

    protected MinerConfiguration config;

    public DependencyResolver(InputStream pomStream, ArtifactIdentifier identifier, MinerConfiguration config){
        this.pomFileInputStream = pomStream;
        this.identifier = identifier;

        this.config = config;
    }
}
