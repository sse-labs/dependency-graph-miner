from .artifactreferences import ArtifactReference, ArtifactEcosystem
from .versions import VersionNumber

class MultiRangeNugetReference(object):

    def __init__(self, library_ident:str, multi_range_string:str):
        self.sub_ranges = []
        self.library_ident = library_ident
        self.version_range_string_representation = multi_range_string
        self.__parse(multi_range_string)

    def is_floating(self) -> bool:
        for range in self.sub_ranges:
            if range.is_floating:
                return True
        
        return False
    
    def contains_version(self, version: VersionNumber):
        for range in self.sub_ranges:
            if range.contains_version(version):
                return True
        
        return False
    
    def __parse(self, value:str):
        parts = value.split(',')

        has_one_exact_ref = len(parts) % 2 != 0

        index = 0
        while index < len(parts):
            sub_range = ArtifactReference()
            if has_one_exact_ref and (('[' in parts[index] and ']' in parts[index]) or parts[index] == '') :
                sub_range.init_for_nuget(self.library_ident, parts[index])
                index += 1
            else:
                sub_range.init_for_nuget(self.library_ident, f"{parts[index]},{parts[index+1]}")
                index += 2
            self.sub_ranges.append(sub_range)

class MultiRangeNpmSnykReference(object):
    def __init__(self, library_ident:str, multi_range_string:str):
        self.sub_ranges = []
        self.library_ident = library_ident
        self.version_range_string_representation = multi_range_string
        self.__parse(multi_range_string)

    def is_floating(self) -> bool:
        for range in self.sub_ranges:
            if range.is_floating:
                return True
        
        return False
    
    def contains_version(self, version: VersionNumber):
        for range in self.sub_ranges:
            if range.contains_version(version):
                return True
        
        return False
    
    def __parse(self, value:str):
        parts = value.split(',')

        for part in parts:
            sub_range = ArtifactReference()
            sub_range.init_for_snyk_npm(self.library_ident, part.strip())
            self.sub_ranges.append(sub_range)



