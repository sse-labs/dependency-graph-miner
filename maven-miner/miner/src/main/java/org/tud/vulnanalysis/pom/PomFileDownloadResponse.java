package org.tud.vulnanalysis.pom;

public class PomFileDownloadResponse {
    private boolean success;
    private Throwable exception;
    private long lastModified;

    private PomFileDownloadResponse(boolean s, Throwable e, long l){
        this.success = s;
        this.exception = e;
        this.lastModified = l;
    }

    static PomFileDownloadResponse createSuccessResponse(long lastModified){
        return new PomFileDownloadResponse(true, null, lastModified);
    }

    static PomFileDownloadResponse createFailureResponse(Throwable exception){
        return new PomFileDownloadResponse(false, exception, -1);
    }

    public boolean getSuccess(){
        return this.success;
    }

    public Throwable getException(){
        return this.exception;
    }

    public long getLastModified(){
        return this.lastModified;
    }
}