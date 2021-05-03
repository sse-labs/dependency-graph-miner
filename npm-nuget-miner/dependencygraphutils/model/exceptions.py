class VersionReferenceUnparsableException(Exception):

    def __init__(self, cause: str, version_ref_string_rep: str):
        self.cause = cause
        self.version_ref = version_ref_string_rep