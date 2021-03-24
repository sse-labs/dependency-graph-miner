package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.io.File;
import java.lang.reflect.Constructor;

public class DependencyResolverProvider {

    private static DependencyResolverProvider theInstance;
    private Class<? extends DependencyResolver> resolverType;

    private DependencyResolverProvider(){
        // The default resolver
        this.registerResolverType(MvnPluginDependencyResolver.class);
    }

    public DependencyResolver buildResolver(File pomFile, ArtifactIdentifier identifier){
        try {
            Constructor<? extends DependencyResolver> c = resolverType.getConstructor(File.class, ArtifactIdentifier.class);
            return c.newInstance(pomFile, identifier);
        } catch (Exception x){
            return null;
        }
    }

    public void registerResolverType(Class<? extends DependencyResolver> resolver){
        this.resolverType = resolver;
    }

    public static DependencyResolverProvider getInstance() {
        if(theInstance == null)
            theInstance = new DependencyResolverProvider();

        return theInstance;
    }
}
