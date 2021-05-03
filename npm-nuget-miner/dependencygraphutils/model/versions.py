from .exceptions import VersionReferenceUnparsableException
from functools import total_ordering

@total_ordering
class VersionNumber(object):

    def __init__(self, version_string, is_reference = True, use_filling = True):
        self.original_string_representation = version_string
        self.version_levels = [None, None, None]
        self.__parse(version_string, is_reference, use_filling)
    
    def __parse(self, version_string, is_reference: bool, use_filling:bool):
        levels = []

        version_parts = version_string.split('.')

        for index in range(max(3, len(version_parts))):
            if len(version_parts) > index:
                # Parse level if specified
                levels.append(VersionLevel(version_parts[index]))
            else:
                if not use_filling:
                    break
                # Always have three levels, fill with wildcard
                fill_version = 'x' if is_reference else '0'
                levels.append(VersionLevel(fill_version))
        
        self.version_levels = levels
    
    def has_wildcard(self):
        for level in self.version_levels:
            if level.is_wildcard:
                return True
        
        return False

    def next_minor_version(self) -> str:
        if self.version_levels[1].is_wildcard or self.version_levels[1].version_number == None:
            return f'{self.version_levels[0].string_representation}.x.0'
           
        return f'{self.version_levels[0].string_representation}.{(self.version_levels[1].version_number + 1)}.0'

    def next_major_version(self) -> str:
        if self.version_levels[0].is_wildcard or self.version_levels[0].version_number == None:
            return f'*'

        return f'{(self.version_levels[0].version_number + 1 )}.0.0'
    
    def __str__(self) -> str:
        return '.'.join([l.string_representation for l in self.version_levels])
    
    def __eq__(self, other) -> bool:
        if other == None:
            return False
        
        return self.version_levels == other.version_levels
    
    def __lt__(self, other) -> bool:
        for i in range(max(len(self.version_levels), len(other.version_levels))):
            # If a version has more levels, it is higher: 1.2.3 < 1.2.3.4
            if i >= len(self.version_levels):
                return True
            elif i >= len(other.version_levels):
                return False
            
            # Compare based on individual levels, if a level is less than the other, then the version is as well
            if self.version_levels[i] < other.version_levels[i]:
                return True
            elif self.version_levels[i] > other.version_levels[i]:
                return False
        
        # Happens only if versions are exactly equal, meaning not less than!
        return False
    
    def __gt__(self,other) -> bool:
        for i in range(max(len(self.version_levels), len(other.version_levels))):
            # If a version has more levels, it is higher: 1.2.3 < 1.2.3.4
            if i >= len(self.version_levels):
                return False
            elif i >= len(other.version_levels):
                return True
            
            # Compare based on individual levels, if a level is greater than the other, then the version is as well
            if self.version_levels[i] > other.version_levels[i]:
                return True
            elif self.version_levels[i] < other.version_levels[i]:
                return False
        
        # Happens only if versions are exactly equal, meaning not less than!
        return False

class VersionLevel(object):

    def __init__(self, string_representation:str):
        self.string_representation = string_representation  
        self.postfix = None
        self.seperator = None
        self.version_number = None
        self.is_wildcard = False

        raw_qualifier = string_representation

        if '-' in string_representation:
            raw_qualifier = string_representation.split('-')[0]
            self.postfix = '-'.join(string_representation.split('-')[1:])
            self.seperator = "-"
        elif '+' in string_representation:
            raw_qualifier = string_representation.split('+')[0]
            self.postfix = '+'.join(string_representation.split('+')[1:])
            self.seperator = '+'
        if raw_qualifier.isdigit():
            self.version_number = int(raw_qualifier)
        elif raw_qualifier == '*' or raw_qualifier == 'x':
            self.is_wildcard = True

    def __eq__(self, other) -> bool:
        if other == None:
            return False
        
        return self.string_representation == other.string_representation
    
    def __lt__(self, other) -> bool:
        # A wildcard level is bigger than a number level, e.g. 1.0.3 < 1.0.x
        if self.is_wildcard and not other.is_wildcard:
            return False
        elif not self.is_wildcard and other.is_wildcard:
            return True
        # No comparison possible for two wildcard levels -> No total ordering
        elif self.is_wildcard and other.is_wildcard:
            return False

        # Deal with string-only level
        if self.version_number == None and other.version_number == None:
            return self.string_representation < other.string_representation
        elif self.version_number == None:
            return True
        elif other.version_number == None:
            return False

        # Numeric comparison for less then
        if self.version_number < other.version_number:
            return True
        # Numeric comparison for greater then
        elif self.version_number > other.version_number:
            return False
        # Comparison for equality, e.g. not less than
        elif self.string_representation == other.string_representation:
            return False
        # Same numbers, but different postfixes
        else:
            # Self is prerelease (i.e. has postfix), other has not -> self is less than other
            if self.postfix != None and other.postfix == None:
                return True
            # Other is prerelease, self is not -> self is not less than other
            elif self.postfix == None and other.postfix != None:
                return False
            # Happens for 1.003 > 1.3 (thanks nuget...)
            elif self.postfix == None and other.postfix == None:
                # We are smaller if we have more zeros
                return len(self.string_representation) > len(other.string_representation) 
            # If both prerelease -> alphanumeric string comparison on postfixes
            else:
                return self.postfix < other.postfix

    def __gt__(self, other) -> bool:
        # A wildcard level is bigger than a number level, e.g. 1.0.3 < 1.0.x
        if self.is_wildcard and not other.is_wildcard:
            return True
        elif not self.is_wildcard and other.is_wildcard:
            return False
        # No comparison possible for two wildcard levels -> No total ordering
        elif self.is_wildcard and other.is_wildcard:
            return False

        # Deal with string-only level
        if self.version_number == None and other.version_number == None:
            return self.string_representation > other.string_representation
        elif self.version_number == None:
            return False
        elif other.version_number == None:
            return True

        # Numeric comparison for greater than
        if self.version_number > other.version_number:
            return True
        # Numeric comparison for less than
        elif self.version_number < other.version_number:
            return False
        # Comparison for equality, e.g. not less than
        elif self.string_representation == other.string_representation:
            return False
        # Same numbers, but different postfixes
        else:
            # Self is prerelease (i.e. has postfix), other has not -> self is not greater than other
            if self.postfix != None and other.postfix == None:
                return False
            # Other is prerelease, self is not -> self is greater than other
            elif self.postfix == None and other.postfix != None:
                return True
            # Happens for 1.003 > 1.3 (thanks nuget...)
            elif self.postfix == None and other.postfix == None:
                # We are bigger if we have less zeros
                return len(self.string_representation) < len(other.string_representation) 
            # If both prerelease -> alphanumeric string comparison on postfixes
            else:
                return self.postfix > other.postfix