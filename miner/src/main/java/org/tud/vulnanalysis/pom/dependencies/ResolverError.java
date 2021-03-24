package org.tud.vulnanalysis.pom.dependencies;

public class ResolverError {

    public String Message;
    public Throwable Cause;

    public ResolverError(String message){
        this.Message = message;
        this.Cause = null;
    }

    public ResolverError(String message, Throwable cause){
        this(message);
        this.Cause = cause;
    }

    @Override
    public String toString(){
        String result = "Error while resolving dependencies: " + this.Message;

        if(this.Cause != null){
            result += " (" + this.Cause.getClass() + ": " + this.Cause.getMessage() + ")";
        }

        return result;
    }

    static class ParsingRelatedResolverError extends ResolverError{

        public String AffectedElement;

        ParsingRelatedResolverError(String message, String affectedElement){
            super(message);
            this.AffectedElement = affectedElement;
        }

        ParsingRelatedResolverError(String message, String affectedElement, Throwable cause){
            super(message, cause);
            this.AffectedElement = affectedElement;
        }

        @Override
        public String toString(){
            return super.toString().replace("Error while resolving dependencies:",
                    "Error while parsing element '"+this.AffectedElement+"':");
        }
    }
}
