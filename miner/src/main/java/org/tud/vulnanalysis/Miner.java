package org.tud.vulnanalysis;

import org.tud.vulnanalysis.lucene.BufferedGAVIterator;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.PomFileBatchResolver;
import org.tud.vulnanalysis.pom.dependencies.*;

import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;

public class Miner {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();

    public static void main(String[] args) throws IOException, InterruptedException{
        int current = 0;

        ResolverProvider.registerResolverType(RecursiveDependencyResolver.class);
        //ResolverProvider.registerBackupResolverType(MvnPluginDependencyResolver.class);

        BufferedGAVIterator iterator =
                new BufferedGAVIterator("C:\\Users\\Fujitsu\\Documents\\Research\\Vulnerabilities\\repos\\maven-miner\\index\\central-lucene-index");

        System.out.println("Building GAV index...");
        iterator.initializeIndex();

        System.out.println("Done, start iterating artifacts..");
        MavenCentralRepository repo = MavenCentralRepository.getInstance();

        ArrayList<ArtifactIdentifier> batch = new ArrayList<>();
        PomFileBatchResolver resolver = new PomFileBatchResolver();
        PomFileBatchResolver resolver2 = new PomFileBatchResolver();
        resolver.start();
        resolver2.start();
        boolean useRes1 = true;
        long startTime = System.currentTimeMillis();
        int batchCnt = 0;


        while(iterator.hasNext()){

            ArtifactIdentifier ident = iterator.next();
            batch.add(ident);

            if(batch.size() >= 50){
                if(useRes1){
                    System.out.println("RES1: Trying to schedule batch " + batchCnt + "of 50 identifiers...");
                    resolver.assignBatch(batch);
                    useRes1 = false;
                }
                else{
                    System.out.println("RES2: Trying to schedule batch " + batchCnt + " of 50 identifiers...");
                    resolver2.assignBatch(batch);
                    useRes1 = true;
                }

                System.out.println("Done scheduling batch " + batchCnt);
                batch = new ArrayList<>();
                batchCnt++;
            }

            if(current > 1000){
                break;
            }
            current++;
        }

        System.out.println("Total artifacts processed: " + current);
        long endTime = System.currentTimeMillis() - startTime;
        System.out.println("Finished scheduling last batch after: " + endTime);
        resolver.requestStop();
        resolver2.requestStop();
    }
}
