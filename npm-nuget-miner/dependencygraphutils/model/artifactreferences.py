import enum
from .versions import VersionNumber
from .exceptions import VersionReferenceUnparsableException

class ArtifactReference(object):

    def __init__(self):
        self.ecosystem = ArtifactEcosystem.Unknown
        self.library_identifier = None
        self.is_floating = False
        self.lower_bound = None
        self.lower_bound_exclusive = False
        self.upper_bound = None
        self.upper_bound_exclusive = False
        self.version_range_string_representation = None
    
    def __str__(self):
        return (
            f'{{Library: {self.library_identifier}, Lower: {{Version: {str(self.lower_bound)}, Exclusive: {self.lower_bound_exclusive}}}'
            f', Upper: {{Version: {str(self.upper_bound)}, Exclusive: {self.upper_bound_exclusive}}}, Descriptor: {self.version_range_string_representation}}}'
        )
    
    def contains_version(self, version: VersionNumber) -> bool:
        if self.lower_bound == None and self.upper_bound != None:
            if version == self.upper_bound:
                return not self.upper_bound_exclusive
            else:
                return version < self.upper_bound
        elif self.lower_bound != None and self.upper_bound == None:
            if version == self.lower_bound:
                return not self.lower_bound_exclusive
            else:
                return version > self.lower_bound
        elif self.lower_bound == None and self.upper_bound == None:
            return True

        if version == self.lower_bound:
            return not self.lower_bound_exclusive
        
        if version > self.lower_bound and version < self.upper_bound:
            return True
        
        if version == self.upper_bound:
            return not self.upper_bound_exclusive

    def init_for_nuget(self, library_ident:str, version_spec:str):
        self.ecosystem = ArtifactEcosystem.Nuget
        self.library_identifier = library_ident
        self.version_range_string_representation = version_spec

        # Empty versions where once allowed in nuget!
        if version_spec == '':
            self.lower_bound = None
            self.upper_bound = None
            self.lower_bound_exclusive = True
            self.upper_bound_exclusive = True
            self.is_floating = True
            return

        first_char = version_spec[0]
        if first_char == '(':
            left_delimiter = NugetRangeDelimiter.EXCLUSIVE
            raw_spec = version_spec[1:]
        elif first_char == '[':
            left_delimiter = NugetRangeDelimiter.INCLUSIVE
            raw_spec = version_spec[1:]
        else:
            left_delimiter = NugetRangeDelimiter.NONE
            raw_spec = version_spec
        
        last_char = version_spec[-1]
        if last_char == ')':
            right_delimiter = NugetRangeDelimiter.EXCLUSIVE
            raw_spec = raw_spec[:-1]
        elif last_char == ']':
            right_delimiter = NugetRangeDelimiter.INCLUSIVE
            raw_spec = raw_spec[:-1]
        else:
            right_delimiter = NugetRangeDelimiter.NONE
        
        # No delimiters -> Inclusive minimum version, no upper bound (e.g. '1.0')
        if left_delimiter == NugetRangeDelimiter.NONE and right_delimiter == NugetRangeDelimiter.NONE:
            self.lower_bound = VersionNumber(raw_spec, is_reference=False)
            self.lower_bound_exclusive = False
            self.upper_bound = None
            self.upper_bound_exclusive = True
            self.is_floating = True
        # According to specs, semi-delimited versions are not allowed
        elif left_delimiter == NugetRangeDelimiter.NONE or right_delimiter == NugetRangeDelimiter.NONE:
            raise VersionReferenceUnparsableException('Semi-Delimited versions in Nuget not allowed', version_spec)
        else:
            # Exact version reference, e.g. [1.0]
            if not ',' in raw_spec and left_delimiter == NugetRangeDelimiter.INCLUSIVE and right_delimiter == NugetRangeDelimiter.INCLUSIVE:
                self.lower_bound = VersionNumber(raw_spec, is_reference=False)
                self.upper_bound = VersionNumber(raw_spec, is_reference=False)
                self.lower_bound_exclusive = False
                self.upper_bound_exclusive = False
                self.is_floating = False
            # No other exact version references allowed
            elif not ',' in raw_spec:
                raise VersionReferenceUnparsableException('Exact version matches only supported with inclusive delimiters', version_spec)
            else:
                self.is_floating = True
                # Parse version range, which now must require a comma
                range_version_numbers = [part.strip() for part in raw_spec.split(',')]

                # Open ranges with exclusive delimiter are allowed
                if range_version_numbers[0] == '' and left_delimiter == NugetRangeDelimiter.EXCLUSIVE:
                    self.lower_bound = None
                    self.lower_bound_exclusive = True
                elif range_version_numbers[0] == '' and left_delimiter == NugetRangeDelimiter.INCLUSIVE:
                    # Workaround to deal with version ranges of type [,1.0) in Snyk
                    self.lower_bound = VersionNumber('0', is_reference=False)
                    self.lower_bound_exclusive = False
                # Parse left range limit as VersionNumber, set exclusive according to delimiter
                elif range_version_numbers[0] != '':
                    self.lower_bound = VersionNumber(range_version_numbers[0], is_reference=False)
                    self.lower_bound_exclusive = True if left_delimiter == NugetRangeDelimiter.EXCLUSIVE else False
                # Cannot parse any other left range limit
                else:
                    raise VersionReferenceUnparsableException('Open range requires exclusive delimiter', version_spec)
                
                # Same for right range limit (right-open ranges are exclusive regardless of delimiter)
                if range_version_numbers[1] == '':
                    self.upper_bound = None
                    self.upper_bound_exclusive = True
                elif range_version_numbers[1] != '':
                    self.upper_bound = VersionNumber(range_version_numbers[1], is_reference=False)
                    self.upper_bound_exclusive = True if right_delimiter == NugetRangeDelimiter.EXCLUSIVE else False
                else:
                    raise VersionReferenceUnparsableException('Open range requires exclusive delimiter', version_spec)
    
    def init_for_npm(self, library_ident:str, version_spec:str):
        self.ecosystem = ArtifactEcosystem.Npm
        self.library_identifier = library_ident
        self.version_range_string_representation = version_spec

        if version_spec.startswith('git') or version_spec.startswith('http'):
            raise VersionReferenceUnparsableException('Invalid version range qualifier, references web resource.', version_spec)

        # Always for NPM
        self.lower_bound_exclusive = False

        # Parse domain-specific syntax
        if version_spec.startswith('^'):
            valid_version_spec = NpmValidVersionSpecifier.MINOR_AND_PATCH
            version_spec = version_spec[1:]
        elif version_spec.startswith('~'):
            valid_version_spec = NpmValidVersionSpecifier.PATCH_ONLY
            version_spec = version_spec[1:]
        else:
            valid_version_spec = NpmValidVersionSpecifier.NONE
        
        if valid_version_spec == NpmValidVersionSpecifier.NONE:
            float_version = VersionNumber(version_spec)

            # No qualifier + no wildcard -> Exact version reference
            if not float_version.has_wildcard():
                self.is_floating = False
                self.lower_bound = float_version
                self.upper_bound = float_version
                self.lower_bound_exclusive = False
                self.upper_bound_exclusive = False
            # Represent the '*' version reference, where all versions are accepted
            elif float_version.version_levels[0].is_wildcard:
                self.is_floating = True
                self.lower_bound = None
                self.upper_bound = None
                self.lower_bound_exclusive = True
                self.upper_bound_exclusive = True
            # Calculate ranges with wildcards in later levels, e.g. '1.0.x'
            else:
                # First level is fixed, since it cannot be a wildcard
                lower_bound_str = float_version.version_levels[0].string_representation
                levels_in_version = -1
                wildcard_index = -1

                # Start iterating at second level
                for i in range(1, len(float_version.version_levels)):
                    # If we have a wildcard: convert it to a zero at that level!
                    if float_version.version_levels[i].is_wildcard:             
                        lower_bound_str += '.0'
                        levels_in_version= i + 1
                        wildcard_index = i
                        break
                    else:
                        # Everyting before the wildcard stays the same
                        lower_bound_str += f'.{float_version.version_levels[i].string_representation}'
                
                # Fill up every level after wildcard with .0
                while levels_in_version < len(float_version.version_levels):
                    lower_bound_str += '.0'
                    levels_in_version += 1
                
                # Finally set lower bound
                self.lower_bound = VersionNumber(lower_bound_str)
                self.lower_bound_exclusive = False
                self.is_floating = True

                upper_bound_str = ''
                wildcard_found = False
                try:
                    for i in range(len(self.lower_bound.version_levels)):
                        if not wildcard_found and (i + 1) == wildcard_index:

                            if self.lower_bound.version_levels[i].version_number == None:
                                upper_bound_str += f'.{self.lower_bound.version_levels[i].string_representation}'
                                raise VersionReferenceUnparsableException("Wildcard after string level",self.version_range_string_representation)

                            upper_bound_str += f'.{str((self.lower_bound.version_levels[i].version_number + 1))}'
                            wildcard_found = True
                        elif not wildcard_found:
                            upper_bound_str += f'.{self.lower_bound.version_levels[i].string_representation}'
                        else:
                            upper_bound_str += '.0'
                    
                    upper_bound_str = upper_bound_str[1:]
                    self.upper_bound = VersionNumber(upper_bound_str)
                    self.upper_bound_exclusive = True
                except VersionReferenceUnparsableException:
                    # In this case we cannot resolve the upper bound to an absolute version, need to use wildcards
                    self.upper_bound = VersionNumber(f'{upper_bound_str}.x')
                    self.upper_bound_exclusive = False

        else:
            self.lower_bound = VersionNumber(version_spec)
            self.upper_bound_exclusive = True
            self.is_floating = True

            if valid_version_spec == NpmValidVersionSpecifier.PATCH_ONLY:
                upper_bound_str = self.lower_bound.next_minor_version()
            elif valid_version_spec == NpmValidVersionSpecifier.MINOR_AND_PATCH:
                upper_bound_str = self.lower_bound.next_major_version()
            
            self.upper_bound = VersionNumber(upper_bound_str)
    
    def init_for_snyk_npm(self, library_ident:str, version_spec:str):
        self.ecosystem = ArtifactEcosystem.Npm
        self.library_identifier = library_ident
        self.version_range_string_representation = version_spec
        self.is_floating = True

        if version_spec.strip() == '*':
            self.lower_bound = None
            self.lower_bound = None
            self.lower_bound_exclusive = True
            self.upper_bound_exclusive = True
            return
        
        #Sanatize version spec
        spec_string = ''
        for part in version_spec.split(' '):
            if part == '':
                continue
            elif part == '<=' or part == '<' or part=='>=' or part =='>':
                spec_string = spec_string + part
            else:
                spec_string = spec_string + part + ' '
            
        spec_string = spec_string.strip()


        for part in spec_string.split(' '):
            if part.startswith('<='):
                raw_version = part.replace('<=', '')
                self.upper_bound_exclusive = False
                self.upper_bound = VersionNumber(raw_version)
            elif part.startswith('<'):
                raw_version = part.replace('<', '')
                self.upper_bound_exclusive = True
                self.upper_bound = VersionNumber(raw_version)
            elif part.startswith('>='):
                raw_version = part.replace('>=', '')
                self.lower_bound_exclusive = False
                self.lower_bound = VersionNumber(raw_version)
            elif part.startswith('>'):
                raw_version = part.replace('>', '')
                self.lower_bound_exclusive = True
                self.lower_bound = VersionNumber(raw_version)

                        

class ArtifactEcosystem(enum.Enum):
    Unknown = 0
    Maven = 1
    Npm = 2
    Nuget = 3

class NpmValidVersionSpecifier(enum.Enum):
    NONE = 0
    PATCH_ONLY = 1
    MINOR_AND_PATCH = 2

class NugetRangeDelimiter(enum.Enum):
    NONE = 0
    INCLUSIVE = 1
    EXCLUSIVE = 2